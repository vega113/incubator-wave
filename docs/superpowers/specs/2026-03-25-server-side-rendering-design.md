# Server-Side Rendering (SSR) Design for Apache Wave

Status: DRAFT
Owner: Wave Core Team
Date: 2026-03-25

## 1. Problem Statement

Apache Wave currently requires the full GWT client to load, initialize, open a
WebSocket connection, request wave data, and render it in the DOM before any
wave content is visible. This creates several problems:

1. **Slow time-to-content**: Users see an empty page with a skeleton loader for
   2-5 seconds while GWT bootstraps and fetches data over WebSocket.
2. **No public wave viewing**: Every wave requires authentication. There is no
   way for unauthenticated users to view public or shared waves.
3. **No SEO**: Search engines cannot index wave content because it is rendered
   entirely client-side via GWT JavaScript.
4. **Heavy client requirement**: Even read-only viewing requires downloading the
   full ~1.5 MB GWT permutation, which is wasteful for casual readers.

## 2. Investigation Findings

### 2.1. No Existing SSR Infrastructure

The codebase contains **no server-side wave content rendering**. Specifically:

- `HtmlRenderer.java` (1500+ lines) renders only the application shell: top bar,
  CSS theme, session variables, and the `<div id="app">` container. It does
  **not** render any wave content.
- `WaveClientServlet` serves the shell HTML and injects `__session`,
  `__websocket_address`, and `__client_flags` as JavaScript globals, then loads
  `webclient.nocache.js`.
- `FetchServlet` at `/fetch/*` returns wave snapshots as **protobuf-JSON**, not
  rendered HTML. It requires authentication.
- `FragmentsServlet` at `/fragments` returns JSON fragment metadata for
  viewport-driven lazy loading. Also requires authentication.
- `WaveRefServlet` at `/waveref/*` simply redirects to `/#<wave-path>` (or to
  sign-in if unauthenticated). No content rendering.
- No server-side HTML rendering of wave document content exists anywhere.

### 2.2. Wiab.pro SSR Traces

Git history shows no evidence of server-side wave content rendering from
Wiab.pro. The imported code includes:

- `DynamicRendererImpl`: A client-side (GWT) viewport-aware pager that pages
  blips in/out based on scroll position. This is **not** server-side rendering.
- `ConversationRenderer` / `ReductionBasedRenderer`: Client-side renderers that
  traverse the conversation model tree and produce `UiBuilder` (SafeHtml)
  output. These run inside GWT, not on the server.
- `FullDomRenderer`: Implements `RenderingRules<UiBuilder>` to produce the HTML
  structure for blips, threads, participants, etc. Client-side only.
- `HtmlDomRenderer`: Turns `UiBuilder` output into DOM elements via
  `SafeHtmlBuilder`. Client-side only.

The rendering pipeline is:
```
ConversationView -> ReductionBasedRenderer<UiBuilder> -> FullDomRenderer
                 -> HtmlDomRenderer -> DOM Elements
```

All of these classes depend on GWT (`com.google.gwt.dom.client.Element`,
`com.google.gwt.core.client.GWT`) and cannot run on the server JVM.

### 2.3. Current Loading Sequence

```
Browser                          Server
  |--- GET / ----------------------->|
  |<-- HTML shell (HtmlRenderer) ----|  (~50ms)
  |--- GET webclient.nocache.js ---->|
  |<-- GWT bootstrap (~1.5MB) ------|  (~500ms)
  |    [GWT compiles/initializes]       (~1-2s)
  |--- WebSocket connect ----------->|
  |<-- WebSocket handshake ----------|  (~50ms)
  |--- OpenWave request ------------>|
  |<-- Full wavelet snapshot --------|  (~100-500ms)
  |    [Client-side DOM rendering]      (~200-500ms)
  |    === CONTENT VISIBLE ===          (TOTAL: 2-5s)
```

### 2.4. Existing Building Blocks

Despite no SSR, the codebase has useful infrastructure:

- **SnapshotSerializer**: Serializes `ReadableWaveletData` to protobuf
  `WaveletSnapshot`, which contains all documents with their content operations
  (`DocInitialization`).
- **Access control**: `WaveletDataUtil.checkAccessPermission()` checks if a user
  is a participant or if the shared-domain participant is present. There is a
  TODO comment in `WaveletContainerImpl` (line 278): *"A user who isn't logged
  in should have access to public waves once they've been implemented."*
- **Document operations model**: Wave documents are stored as sequences of
  document operations (`DocOp`). These can be composed and applied to produce a
  final document state, which is an XML-like tree that can be walked to produce
  HTML.
- **Skeleton loading**: `StageOne` already sets up a skeleton loader placeholder
  in the `#app` div while GWT initializes.
- **Fragments/streaming**: The fragments infrastructure sends partial wave data
  for viewport-driven lazy loading, which could be adapted for SSR.
- **`RenderingRules<R>`**: The rendering rules are parameterized by output type
  `R`. Currently `R = UiBuilder`, but this could be `R = String` (HTML string)
  for server-side use.

### 2.5. Access Control Gap

The current `checkAccessPermission` requires `user != null` (line 231 of
`WaveletDataUtil.java`). A wave is accessible if:
- The user is a participant, OR
- The shared-domain participant (`@<domain>`) is on the participant list.

There is **no concept of "public" waves** accessible to anonymous users.
Implementing public wave viewing will require extending this model.

## 3. Proposed Architecture

### 3.1. Overview

We propose two complementary capabilities:

| Capability | URL Pattern | Auth Required | GWT Required | Priority |
|---|---|---|---|---|
| **Inline snapshot** (hydration-ready) | `/ (with wave hash)` | Yes | Yes (takes over) | P1 |
| **Public read-only view** | `/wave/<waveId>` | No | No | P2 |
| **API: rendered HTML** | `/render/<waveId>` | Configurable | No | P3 |

### 3.2. Server-Side Document Renderer

The core new component is a **server-side wave document renderer** that converts
a `ReadableWaveletData` snapshot into HTML.

```
ReadableWaveletData
  -> WaveDocumentHtmlRenderer.render(waveletData)
    -> for each document (blip):
         DocInitialization -> XML tree -> HTML string
    -> compose into conversation HTML with blip metadata
  -> complete HTML page or fragment
```

#### 3.2.1. Document-to-HTML Conversion

Wave documents use an XML-like model with elements and annotations. The key
document elements map to HTML as follows:

| Wave Element | HTML Output |
|---|---|
| `<line>` | `<p>` or `<div>` |
| `<line t="h1">` | `<h1>` |
| `<line t="h2">` | `<h2>` |
| `<line t="h3">` | `<h3>` |
| `<line t="li">` | `<li>` inside `<ul>` |
| `<line t="li" i="decimal">` | `<li>` inside `<ol>` |
| Text nodes | Text content |
| `<image>` | `<img>` |
| `<gadget>` | Placeholder or iframe |
| Annotations (`link/manual`) | `<a href="...">` |
| Annotations (`style/fontWeight`) | `<span style="...">` |

The server-side renderer will walk the `DocInitialization` operations and produce
a sanitized HTML string. It does **not** need GWT -- it operates on the document
operation model which is pure Java.

#### 3.2.2. New Classes

```
wave/src/main/java/org/waveprotocol/box/server/rpc/render/
  WaveDocumentHtmlRenderer.java    -- DocInitialization -> HTML string
  WaveContentRenderer.java         -- wavelet snapshot -> full blip HTML
  WavePageRenderer.java            -- rendered blips -> complete HTML page
  PublicWaveServlet.java           -- /wave/<waveId> endpoint
  RenderApiServlet.java            -- /render/<waveId> JSON/HTML API
```

## 4. Use Case 1: Inline Snapshot (Faster Initial Load)

### 4.1. Concept

When the authenticated user loads the wave client page, the server pre-renders
the user's inbox digest list and optionally the first wave's content as HTML,
embedding it directly in the initial page response. The GWT client then
"hydrates" -- it takes over the pre-rendered DOM instead of rendering from
scratch.

### 4.2. Design

#### Step 1: Inline Search Digest

`WaveClientServlet.doGet()` currently calls `HtmlRenderer.renderWaveClientPage()`.
We extend this to:

1. Run the user's default search query (`in:inbox`) server-side.
2. Render the search digest as HTML (title, snippet, participant avatars, time).
3. Embed this in a `<div id="ssr-search-digest" style="display:none">` element.
4. Also embed the raw search JSON as a `<script type="application/json"
   id="ssr-search-data">` block.

The GWT client, during `StageOne`, checks for `#ssr-search-digest`. If present,
it injects this HTML into the search panel container immediately, providing
instant visual content. When `StageTwo` completes, the live search data replaces
the pre-rendered content.

#### Step 2: Inline First Wave Content

If the URL contains a wave hash (e.g., `/#example.com/w+abc123`), the server:

1. Parses the wave ID from the URL fragment (sent as a query parameter or
   inferred from the `Referer` header for returning users).
2. Fetches the wavelet snapshot.
3. Renders the first N blips as HTML using `WaveDocumentHtmlRenderer`.
4. Embeds this in `<div id="ssr-wave-content" style="display:none">`.
5. Also embeds the raw snapshot JSON for client hydration.

**Important limitation**: URL fragments (`#...`) are not sent to the server. To
enable server-side rendering of a specific wave, we need one of:
- A query parameter: `/?wave=example.com/w+abc123`
- A cookie tracking the user's last-viewed wave.
- A new URL scheme: `/wave/example.com/w+abc123` that renders SSR then
  initializes GWT.

### 4.3. Hydration Protocol

The GWT client needs modifications to detect and adopt pre-rendered DOM:

1. `StageOne.DefaultProvider` checks for `#ssr-wave-content` on init.
2. If present, it inserts the pre-rendered HTML into the wave panel.
3. `StageTwo` opens the wave normally but skips initial rendering.
4. The `DynamicRendererImpl` detects already-rendered blips and registers them
   in `pagedIn` without re-rendering.
5. Once the live model is ready, the renderer swaps from static to live DOM.

### 4.4. Sequence Diagram (With SSR)

```
Browser                          Server
  |--- GET /?wave=x -------------->|
  |                                 |  [Run search query]
  |                                 |  [Fetch wave snapshot]
  |                                 |  [Render to HTML]
  |<-- HTML with pre-rendered ------|  (~100-200ms)
  |    === CONTENT VISIBLE ===         (vs 2-5s before)
  |--- GET webclient.nocache.js --->|
  |<-- GWT bootstrap ---------------|
  |    [GWT hydrates pre-rendered DOM]
  |--- WebSocket connect ---------->|
  |<-- Live updates begin ----------|
  |    === INTERACTIVE ===
```

Time-to-content drops from 2-5s to ~200ms.

## 5. Use Case 2: Public Wave Read-Only View

### 5.1. Concept

A new servlet at `/wave/<waveId>` serves a complete, read-only HTML page for
any wave marked as public. No authentication or GWT is required.

### 5.2. Public Wave Access Model

We introduce a new "public" participant address: `@<domain>` (the bare domain
address, already used as the shared-domain participant) or a new sentinel like
`public@<domain>`.

A wave is public if its participant list contains the public participant.
Wave creators can add this participant to make their wave publicly viewable.

The access check in `WaveletDataUtil.checkAccessPermission()` is extended:

```java
public static boolean checkPublicAccess(ReadableWaveletData snapshot,
    ParticipantId publicParticipantId) {
  return snapshot != null
      && publicParticipantId != null
      && snapshot.getParticipants().contains(publicParticipantId);
}
```

### 5.3. PublicWaveServlet Design

```java
@Singleton
public class PublicWaveServlet extends HttpServlet {

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
    // 1. Parse wave ID from URL path
    // 2. Fetch wavelet snapshot from WaveletProvider
    // 3. Check public access (no auth required)
    // 4. Render wave content to HTML via WaveContentRenderer
    // 5. Wrap in page template via WavePageRenderer
    // 6. Return complete HTML page
  }
}
```

### 5.4. Read-Only Page Template

The rendered page includes:

- **Header**: Wave title, participant avatars, last modified time.
- **Content**: Rendered blips with author attribution and timestamps.
- **Footer**: "Open in Wave" button (links to authenticated client).
- **Meta tags**: OpenGraph and Twitter Card tags for social sharing.
- **Structured data**: JSON-LD for search engine rich results.
- **Minimal CSS**: Standalone stylesheet, no GWT dependency.

### 5.5. URL Routing

```
/wave/<domain>/<waveId>          -> PublicWaveServlet (read-only HTML)
/wave/<domain>/<waveId>?format=json -> RenderApiServlet (JSON)
/wave/<domain>/<waveId>/blip/<blipId> -> anchor to specific blip
```

### 5.6. Caching Strategy

Public wave pages are cached with:
- `Cache-Control: public, max-age=60, stale-while-revalidate=300`
- `ETag` based on the wavelet version hash.
- Caddy/CDN can cache at the edge for public waves.
- Authenticated inline snapshots use `Cache-Control: private, no-store`.

## 6. Use Case 3: Render API

### 6.1. Concept

A JSON API at `/render/<waveId>` returns rendered HTML fragments for
programmatic use (embeds, previews, bots).

### 6.2. Response Format

```json
{
  "waveId": "example.com/w+abc123",
  "title": "Meeting Notes",
  "version": 42,
  "lastModified": "2026-03-25T10:30:00Z",
  "participants": ["alice@example.com", "bob@example.com"],
  "html": "<div class='wave-blip'>...</div>",
  "blips": [
    {
      "blipId": "b+abc",
      "author": "alice@example.com",
      "html": "<p>Hello world</p>",
      "lastModified": "2026-03-25T10:30:00Z"
    }
  ]
}
```

## 7. Implementation Plan

### Phase 1: Server-Side Document Renderer (Foundation)

**Goal**: Build `WaveDocumentHtmlRenderer` that converts `DocInitialization` to
clean HTML.

Tasks:
1. Create `WaveDocumentHtmlRenderer` that walks `DocOp` components and emits
   HTML.
2. Handle all standard document elements: lines (paragraphs, headings, lists),
   text, images, links.
3. Handle annotations: bold, italic, links, colors.
4. Sanitize output (prevent XSS from wave content).
5. Unit tests against known wave document fixtures.

Estimated effort: 3-5 days.

### Phase 2: Wave Content Renderer

**Goal**: Render a full wavelet (all blips with metadata) as an HTML fragment.

Tasks:
1. Create `WaveContentRenderer` that iterates wavelet documents, identifies
   conversation structure (root thread, reply threads), and renders blips in
   order.
2. Include blip metadata: author, contributors, timestamps.
3. Handle inline replies and thread nesting.
4. Unit tests with multi-blip conversations.

Estimated effort: 3-4 days.

### Phase 3: Public Wave Access Model

**Goal**: Allow waves to be marked as public and viewed without authentication.

Tasks:
1. Define the public participant sentinel (e.g., `public@<domain>` or
   `@everyone@<domain>`).
2. Extend `checkAccessPermission` to allow null user when wave is public.
3. Add configuration flag: `core.enable_public_waves = false` (off by default).
4. Ensure the GWT client can add/remove the public participant.
5. Unit tests for public access checks.

Estimated effort: 2-3 days.

### Phase 4: Public Wave Servlet

**Goal**: Serve public waves as read-only HTML at `/wave/<waveId>`.

Tasks:
1. Create `PublicWaveServlet` with URL parsing and access checks.
2. Create `WavePageRenderer` that wraps content in a full HTML page with
   header, styles, meta tags.
3. Register in `ServerMain`.
4. Add caching headers and ETag support.
5. Integration tests.

Estimated effort: 3-4 days.

### Phase 5: Inline Snapshot for Authenticated Users

**Goal**: Pre-render wave content in the initial HTML for faster loading.

Tasks:
1. Extend `WaveClientServlet` to accept a `wave` query parameter.
2. Fetch and render the wave snapshot server-side.
3. Embed rendered HTML and JSON data in the page.
4. Modify GWT client (`StageOne` / `StageTwo`) to detect and hydrate
   pre-rendered content.
5. Update the client-side URL handling to use `/?wave=...` format.
6. Performance benchmarks.

Estimated effort: 5-8 days (includes GWT modifications).

### Phase 6: Render API

**Goal**: JSON API for programmatic access to rendered wave content.

Tasks:
1. Create `RenderApiServlet` with JSON response format.
2. Support both authenticated and public access.
3. Rate limiting.
4. API documentation.

Estimated effort: 2-3 days.

### Phase 7: SEO and Social Sharing

**Goal**: Make public waves discoverable by search engines and shareable.

Tasks:
1. Add OpenGraph meta tags (title, description, image).
2. Add Twitter Card meta tags.
3. Add JSON-LD structured data.
4. Generate `robots.txt` entries for public wave paths.
5. Optional: sitemap generation for public waves.

Estimated effort: 2-3 days.

## 8. Technical Risks and Mitigations

### 8.1. DocOp to HTML Fidelity

**Risk**: The server-side HTML renderer may not produce output identical to the
GWT client, causing visual differences between SSR and hydrated content.

**Mitigation**: Use the same document element mapping as `FullDomRenderer` and
`ParagraphHtmlRenderer`. Create visual regression tests comparing server
output to GWT screenshots.

### 8.2. GWT Hydration Complexity

**Risk**: GWT is not designed for hydration (unlike React/Vue). Making it adopt
pre-rendered DOM without re-rendering is non-trivial.

**Mitigation**: Use a simpler approach -- the pre-rendered HTML is a visual
placeholder that gets replaced (not hydrated) once GWT is ready. This is closer
to a "shell swap" pattern than true hydration. The pre-rendered content gives
immediate visual feedback; GWT replaces it with interactive content. The visual
transition can be smoothed with a fade animation.

### 8.3. Server Load

**Risk**: Server-side rendering adds CPU cost per page load.

**Mitigation**:
- Cache rendered HTML for public waves (keyed by wavelet version).
- For authenticated users, limit SSR to the first 10 blips.
- Make SSR opt-in via configuration: `core.enable_ssr = false`.
- The rendering is lightweight (string concatenation, no DOM) compared to
  GWT compilation.

### 8.4. Public Wave Security

**Risk**: Making waves publicly accessible introduces new attack surfaces.

**Mitigation**:
- Public access is opt-in per wave (requires adding public participant).
- HTML output is strictly sanitized (no script injection).
- Public waves are read-only (no mutation endpoints without auth).
- Rate limiting on public endpoints.
- Off by default in configuration.

### 8.5. Document Format Complexity

**Risk**: Wave documents can contain gadgets, images, and other complex
elements that are difficult to render server-side.

**Mitigation**: Phase 1 handles text, formatting, links, and basic structure.
Complex elements (gadgets, embedded waves) render as placeholders with a
"View in Wave client" link. Iterative improvement in later phases.

## 9. Configuration

New configuration keys in `reference.conf`:

```hocon
core {
  # Enable server-side rendering of wave content
  enable_ssr = false

  # Enable public wave viewing (waves with public participant)
  enable_public_waves = false

  # Maximum number of blips to pre-render for inline snapshots
  ssr_max_blips = 10

  # Cache TTL for public wave rendered HTML (seconds)
  ssr_public_cache_ttl = 60

  # The participant address that marks a wave as public
  public_participant_address = "public"
  # Resolves to: public@<wave_server_domain>
}
```

## 10. Success Metrics

| Metric | Current | Target (SSR) |
|---|---|---|
| Time to first content (authenticated) | 2-5s | < 500ms |
| Time to interactive (authenticated) | 2-5s | 2-5s (unchanged) |
| Public wave load time | N/A (impossible) | < 300ms |
| GWT download for read-only | ~1.5 MB | 0 (public view) |
| SEO indexable pages | 0 | All public waves |

## 11. Dependencies

- No external library dependencies for Phase 1-4 (pure Java HTML generation).
- Phase 5 (hydration) requires GWT client modifications.
- Phase 7 (SEO) may benefit from a sitemap library but can be done manually.

## 12. Open Questions

1. **URL scheme**: Should we use `/wave/` prefix for public views, or something
   else? `/read/`? `/view/`? `/w/`?
2. **Public participant name**: `public@domain`? `@everyone@domain`? The
   existing shared-domain participant `@domain` concept is close but different.
3. **Hydration vs. replacement**: True hydration (GWT adopts existing DOM) is
   ideal but complex. Shell-swap (replace pre-rendered with GWT-rendered) is
   simpler. Which approach first?
4. **Inline snapshot trigger**: How does the server know which wave to
   pre-render? Query parameter, cookie, or new URL scheme?
5. **Gadget rendering**: Should gadgets in public waves be rendered as iframes,
   placeholders, or omitted?

## 13. Relationship to Existing Work

- **DynamicRendererImpl**: The existing client-side dynamic renderer already
  does viewport-aware lazy rendering. SSR complements this by providing initial
  content before the dynamic renderer initializes.
- **Fragments infrastructure**: The server-side fragment fetching can be
  extended to serve rendered HTML fragments instead of raw JSON.
- **Skeleton loading**: The existing skeleton loading placeholder will be
  replaced by actual pre-rendered content.
- **FullDomRenderer / RenderingRules**: The rendering rules define the
  client-side HTML structure. The server-side renderer should produce
  compatible HTML to enable smooth transitions.

## 14. References

- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java` -- current page shell renderer
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/WaveClientServlet.java` -- wave client page handler
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/FetchServlet.java` -- JSON snapshot endpoint
- `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/render/FullDomRenderer.java` -- client-side rendering rules
- `wave/src/main/java/org/waveprotocol/wave/client/render/ReductionBasedRenderer.java` -- client-side rendering engine
- `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/render/DynamicRendererImpl.java` -- viewport-aware pager
- `wave/src/main/java/org/waveprotocol/box/server/common/SnapshotSerializer.java` -- wavelet snapshot serialization
- `wave/src/main/java/org/waveprotocol/box/server/util/WaveletDataUtil.java` -- access control checks
- `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/render/FragmentRequester.java` -- fragment fetch interface
- `wave/config/application.conf` -- client flags and fragment configuration
- `docs/migrate-conversation-renderer-to-apache-wave.md` -- renderer migration history
