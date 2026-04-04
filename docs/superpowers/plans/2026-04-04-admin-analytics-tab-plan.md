# Admin Analytics Tab Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an Analytics tab to `/admin` with actionable product and operational metrics for waves, blips, user activity, and public-wave engagement.

**Architecture:** Extend the existing `AdminServlet` + `HtmlRenderer` admin seam with a new lazily loaded Analytics tab backed by a dedicated `AdminAnalyticsService`. The service will combine account metadata (`AccountStore`), current wave/blip state (`WaveletProvider` snapshots), historical conversational activity (`DeltaStore`), and a new live public-wave view tracker updated by the public wave servlets. Cache the computed analytics summary briefly, preserve the last good summary on refresh failures, and expose staleness/warning metadata so the admin page stays usable.

**Tech Stack:** Java 17, Jakarta servlets, Guice singletons, existing Wave `AccountStore` / `WaveletProvider` / `DeltaStore`, server-rendered admin HTML/JS, JUnit + Mockito, `sbt`.

---

## Scope And Definitions

- **Logged in users:** human accounts with `lastLoginTime` inside the labeled window.
- **Active users:** human accounts with `lastActivityTime` inside the labeled window.
- **Users actively writing:** unique human authors of conversational `WaveletBlipOperation`s inside the labeled window.
- **Public wave:** conversational root wavelet contains the shared-domain participant (`@domain`).
- **Blip counts:** conversational documents only; exclude manifest/tags/user-data documents.
- **Public views:** live counts collected from successful public-wave GET traffic and labeled `since process start`.
- **Page views vs API views:** count top-level `/wave/*` document navigations as page views and `/wave/public/*` reads as API views. Do not count the public page’s own background refresh traffic as page views.

## Acceptance Criteria

- `/admin` shows a new `Analytics` tab alongside the existing admin tabs.
- Opening the tab fetches a JSON analytics payload from a new admin API.
- The payload includes:
  - total waves
  - total blips created (historical, from delta scan)
  - logged-in user counts with explicit windows
  - active/writing user counts with explicit windows
  - private/public partitions for waves and extant blips
  - top 10 public waves by views
  - top 10 public waves by participation
  - top 10 active users
  - at least 2 additional high-signal summary metrics that fit the admin surface
- Public-wave view tracking is real, not guessed, and clearly labeled `since process start`.
- Admin analytics is owner/admin-only, like the rest of `/admin`.
- When analytics recomputation fails or exceeds the refresh budget, the endpoint preserves the last good summary and returns warning/staleness metadata instead of breaking the admin page.
- Targeted tests cover analytics computation, admin endpoint shape, auth denial, cache behavior, empty-install behavior, and public-view tracking hooks.
- `wave/config/changelog.json` is updated and validated for the user-facing admin addition.

## File Map

**Create**
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/waveserver/AdminAnalyticsService.java`
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/waveserver/PublicWaveViewTracker.java`
- `wave/src/test/java/org/waveprotocol/box/server/waveserver/AdminAnalyticsServiceTest.java`

**Modify**
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/AdminServlet.java`
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java`
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/PublicWaveServlet.java`
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/PublicWaveFetchServlet.java`
- `wave/src/test/java/org/waveprotocol/box/server/rpc/AdminServletTest.java`
- `wave/src/test/java/org/waveprotocol/box/server/rpc/HtmlRendererFeatureFlagsTest.java`
- `wave/src/test/java/org/waveprotocol/box/server/rpc/PublicWaveServletTest.java`
- `wave/src/test/java/org/waveprotocol/box/server/rpc/PublicWaveFetchServletTest.java`
- `wave/config/changelog.json`

## Out Of Scope

- Persistent analytics rollups or background precomputation jobs.
- Historical public-wave view persistence across restarts.
- A standalone analytics page outside `/admin`.
- New billing/commercial metrics that require schema additions.
- A separate rollout feature flag for this admin-only tab; the existing admin auth boundary is the rollout boundary for this slice.

## Known Assumptions

- `AdminAnalyticsService` and `PublicWaveViewTracker` will use normal Guice constructor injection and JIT binding, like other concrete servlet collaborators in this codebase. No explicit module binding change is planned unless injector startup proves that assumption wrong.

## Review Gates

- Before implementation starts:
  - post the plan path to issue `#605`
  - run an Opus plan review and address findings
  - run a Codex `gpt-5.4` `high` review on the plan and address findings
- Before PR creation:
  - run a Codex `gpt-5.4` `high` review on the implementation diff
  - fix or explicitly disposition every material finding
  - update issue `#605` with commits, review outcomes, verification commands, and PR link
- If Opus is unavailable or times out after a reasonable retry, document that provider blockage in issue `#605` before continuing so the review gap is explicit.

### Task 1: Add The Analytics Aggregation Service

**Files:**
- Create: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/waveserver/AdminAnalyticsService.java`
- Create: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/waveserver/PublicWaveViewTracker.java`
- Test: `wave/src/test/java/org/waveprotocol/box/server/waveserver/AdminAnalyticsServiceTest.java`

- [ ] **Step 1: Write the failing service tests first**

Create focused tests that cover:
- a populated case with human accounts, one public wave, one private wave, conversational deltas that create multiple blips and write events, and live view counts for one public wave
- an empty-install case with no accounts, no waves, and no deltas
- cache reuse inside the TTL window

Assert the service reports:
- correct wave totals and public/private partition
- correct historical blip-created total
- correct recent logged-in / active / writing counts
- non-empty top public waves and top users lists in the populated case
- stable zero/default values in the empty case

Run:

```bash
sbt "testOnly org.waveprotocol.box.server.waveserver.AdminAnalyticsServiceTest"
```

Expected before implementation: FAIL because `AdminAnalyticsService` / `PublicWaveViewTracker` do not exist yet.

- [ ] **Step 2: Implement `PublicWaveViewTracker`**

Use concurrent primitives (`ConcurrentHashMap`, `LongAdder`) keyed by serialized wave id. Track at least:
- total public page views since process start
- total public API fetches since process start
- per-wave combined view counts for ranking

Expose methods along these lines:

```java
public final class PublicWaveViewTracker {
  public void recordPageView(WaveId waveId) { ... }
  public void recordApiView(WaveId waveId) { ... }
  public long getCombinedViews(WaveId waveId) { ... }
  public long getTotalPageViews() { ... }
  public long getTotalApiViews() { ... }
  public Map<String, Long> snapshotCombinedViews() { ... }
}
```

Return defensive copies from snapshot methods.

- [ ] **Step 3: Implement `AdminAnalyticsService`**

Inject:
- `AccountStore`
- `WaveletProvider`
- `DeltaStore`
- `PublicWaveViewTracker`
- `@Named(CoreSettingsNames.WAVE_SERVER_DOMAIN) String waveDomain`

Use the concrete iteration APIs that already exist:
- `accountStore.getAllAccounts()` for human account enumeration
- `waveletProvider.getWaveIds()` / `waveletProvider.getWaveletIds(waveId)` / `waveletProvider.getSnapshot(waveletName)` for snapshot-based wave metrics
- `deltaStore.getWaveIdIterator()` / `deltaStore.lookup(waveId)` / `deltaStore.open(...)` for historical delta-backed metrics

Use the storage traversal that actually exists in this repo:
- iterate `deltaStore.getWaveIdIterator()`
- for each wave id, call `deltaStore.lookup(waveId)`
- keep only conversational wavelets (typically `conv+root`)
- open each wavelet with `deltaStore.open(WaveletName.of(waveId, waveletId))`
- walk forward through delta history from version `0` to `getEndVersion().getVersion()` using `getDelta(version)` / `getResultingVersion(version)` so the scan advances on real stored boundaries

Do not assume a global “all deltas” iterator exists.

The service should:
- cache the last computed summary for ~30 seconds
- preserve the last good summary if a refresh fails
- scan human accounts for registration / login / activity counts
- scan current conversational wave snapshots for:
  - public/private wave counts
  - extant public/private blip counts
  - per-wave participant and contributor counts
  - recent wave creation / update counts
- scan conversational delta history for:
  - historical blips created (first-seen conversational blip ids per wavelet)
  - recent writer counts
  - top-user write metrics

Add a refresh budget so the delta scan cannot monopolize an admin request forever:
- target: 5s max refresh wall-clock time
- if the refresh budget is exceeded and a prior summary exists, keep serving the prior summary and attach warning metadata
- if there is no prior summary yet, return a clear analytics warming/error state instead of partial, misleading counts

The computed summary should include explicit windows, for example:

```java
static final long DAY_MS = 24L * 60L * 60L * 1000L;

// users
loggedIn24h, loggedIn7d, loggedIn30d
active24h, active7d, active30d
writers24h, writers7d, writers30d

// waves/blips
totalWaves, publicWaves, privateWaves
totalBlipsCreated, publicBlipsCurrent, privateBlipsCurrent
wavesCreated7d, wavesUpdated7d
```

Use the shared-domain participant plus `WaveletDataUtil.isPublicWavelet(...)` for classification.

- [ ] **Step 4: Run the service tests and make them pass**

Run:

```bash
sbt "testOnly org.waveprotocol.box.server.waveserver.AdminAnalyticsServiceTest"
```

Expected after implementation: PASS.

- [ ] **Step 5: Commit the service layer**

```bash
git add \
  wave/src/jakarta-overrides/java/org/waveprotocol/box/server/waveserver/AdminAnalyticsService.java \
  wave/src/jakarta-overrides/java/org/waveprotocol/box/server/waveserver/PublicWaveViewTracker.java \
  wave/src/test/java/org/waveprotocol/box/server/waveserver/AdminAnalyticsServiceTest.java
git commit -m "feat: add admin analytics aggregation service"
```

### Task 2: Expose The Analytics API In `AdminServlet`

**Files:**
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/AdminServlet.java`
- Test: `wave/src/test/java/org/waveprotocol/box/server/rpc/AdminServletTest.java`

- [ ] **Step 1: Write the failing servlet tests first**

Add tests that:
- authenticate an owner/admin caller, stub `AdminAnalyticsService` with a non-empty summary, hit `GET /admin/api/analytics/status`, and assert a `200` JSON response with the expected top-level sections
- assert unauthenticated callers are rejected
- assert authenticated non-admin callers are rejected

Run:

```bash
sbt "testOnly org.waveprotocol.box.server.rpc.AdminServletTest"
```

Expected before implementation: FAIL because `/api/analytics/status` is not handled.

- [ ] **Step 2: Inject the analytics service and add the endpoint**

Update the servlet constructor to accept `AdminAnalyticsService`.

Handle:

```java
if (pathInfo != null && pathInfo.equals("/api/analytics/status")) {
  handleAnalyticsStatus(resp);
}
```

`handleAnalyticsStatus(...)` should:
- require the existing admin auth gate
- serialize the service summary with stable keys for the UI
- include live-only / freshness metadata (`generatedAtMs`, `scanDurationMs`, `stale`, `warnings`)
- return a structured warning/error payload when analytics data is warming or stale instead of a blank 500 page

- [ ] **Step 3: Keep the JSON contract explicit**

Return a payload shaped like:

```json
{
  "summary": { ... },
  "generatedAtMs": 1711900000000,
  "scanDurationMs": 420,
  "stale": false,
  "warnings": [],
  "windows": { "recentLogin": ["24h","7d","30d"], "recentWriting": ["24h","7d","30d"] },
  "topViewedPublicWaves": [ ... ],
  "topParticipatedPublicWaves": [ ... ],
  "topUsers": [ ... ],
  "liveViews": { "pageViewsSinceStart": 12, "apiViewsSinceStart": 5 }
}
```

- [ ] **Step 4: Run the servlet tests and make them pass**

Run:

```bash
sbt "testOnly org.waveprotocol.box.server.rpc.AdminServletTest"
```

Expected after implementation: PASS.

- [ ] **Step 5: Commit the admin API**

```bash
git add \
  wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/AdminServlet.java \
  wave/src/test/java/org/waveprotocol/box/server/rpc/AdminServletTest.java
git commit -m "feat: add admin analytics api"
```

### Task 3: Add The Analytics Tab UI

**Files:**
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java`
- Test: `wave/src/test/java/org/waveprotocol/box/server/rpc/HtmlRendererFeatureFlagsTest.java`

- [ ] **Step 1: Add the failing UI assertions first**

Extend the existing `HtmlRenderer.renderAdminPage(...)` regression test to assert the rendered admin HTML includes:
- an `Analytics` tab button
- a `panel-analytics` container
- JavaScript that fetches `/admin/api/analytics/status` lazily on tab open

Run:

```bash
sbt "testOnly org.waveprotocol.box.server.rpc.HtmlRendererFeatureFlagsTest"
```

- [ ] **Step 2: Add the new tab and panel markup**

In `HtmlRenderer.renderAdminPage(...)`, follow the existing admin tab pattern:

```html
<button class="admin-tab" data-tab="analytics">Analytics</button>
<div class="tab-panel" id="panel-analytics">...</div>
```

Render analytics as:
- a compact overview card for key counts
- a waves/blips partition card
- a users activity card
- two public-waves tables (`Top Viewed`, `Top Participated`)
- one `Top Active Users` table

Add a muted label where needed:
- `Views are live counts since process start`

- [ ] **Step 3: Add tab-specific client logic**

Add a lazy loader similar to contacts/flags/ops:

```javascript
if (tab.dataset.tab === 'analytics' && !analyticsLoaded) {
  loadAnalyticsStatus();
}
```

`loadAnalyticsStatus()` should:
- fetch `/admin/api/analytics/status`
- render counts and tables
- show explicit windows in labels, e.g. `Logged in (7d)` and `Writers (30d)`
- render wave links to `/wave/<waveId>` for public entries when possible
- render warnings/staleness metadata when present

- [ ] **Step 4: Run the targeted UI test**

Run:

```bash
sbt "testOnly org.waveprotocol.box.server.rpc.HtmlRendererFeatureFlagsTest"
```

Expected after implementation: PASS.

- [ ] **Step 5: Commit the analytics tab UI**

```bash
git add \
  wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java \
  wave/src/test/java/org/waveprotocol/box/server/rpc/HtmlRendererFeatureFlagsTest.java
git commit -m "feat: add analytics tab to admin ui"
```

### Task 4: Record Public-Wave Views At The Existing Public Seams

**Files:**
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/PublicWaveServlet.java`
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/PublicWaveFetchServlet.java`
- Test: `wave/src/test/java/org/waveprotocol/box/server/rpc/PublicWaveServletTest.java`
- Test: `wave/src/test/java/org/waveprotocol/box/server/rpc/PublicWaveFetchServletTest.java`

- [ ] **Step 1: Write failing tests for successful public view tracking**

First align `PublicWaveServletTest` with the active Jakarta override constructor by building the servlet with `Config`, `waveDomain`, and `WaveletProvider`, not the legacy `(waveletProvider, domain)` shape.

Then add/extend tests so that a successful public response:
- records a page view in `PublicWaveServlet`
- records an API view in `PublicWaveFetchServlet`
- does **not** record anything for private or 404 cases

Run:

```bash
sbt "testOnly org.waveprotocol.box.server.rpc.PublicWaveServletTest org.waveprotocol.box.server.rpc.PublicWaveFetchServletTest"
```

- [ ] **Step 2: Inject `PublicWaveViewTracker` and record only after access is confirmed**

In each servlet, record the view only after:
- the wave exists
- the public-access check passes
- the response is about to return `200`

Do not record on malformed ids, private waves, or internal errors.

- [ ] **Step 3: Run the public-wave tests**

Run:

```bash
sbt "testOnly org.waveprotocol.box.server.rpc.PublicWaveServletTest org.waveprotocol.box.server.rpc.PublicWaveFetchServletTest"
```

Expected after implementation: PASS.

- [ ] **Step 4: Commit the tracking hook**

```bash
git add \
  wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/PublicWaveServlet.java \
  wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/PublicWaveFetchServlet.java \
  wave/src/test/java/org/waveprotocol/box/server/rpc/PublicWaveServletTest.java \
  wave/src/test/java/org/waveprotocol/box/server/rpc/PublicWaveFetchServletTest.java
git commit -m "feat: track public wave views for admin analytics"
```

### Task 5: Final Verification, Changelog, And PR Readiness

**Files:**
- Modify: `wave/config/changelog.json`

- [ ] **Step 1: Add the changelog entry**

Add a new top entry describing:
- the new admin analytics tab
- the key metrics exposed
- the live public-view tracking note

- [ ] **Step 2: Run targeted tests**

Run:

```bash
sbt "testOnly org.waveprotocol.box.server.waveserver.AdminAnalyticsServiceTest org.waveprotocol.box.server.rpc.AdminServletTest org.waveprotocol.box.server.rpc.HtmlRendererFeatureFlagsTest org.waveprotocol.box.server.rpc.PublicWaveServletTest org.waveprotocol.box.server.rpc.PublicWaveFetchServletTest"
```

Expected: PASS.

- [ ] **Step 3: Run compile verification**

Run:

```bash
sbt wave/compile
sbt compileGwt
python3 scripts/validate-changelog.py wave/config/changelog.json
```

Expected: all commands exit `0`.

- [ ] **Step 4: Run the real-app sanity check**

Run the server from this worktree on an available port, for example:

```bash
WAVE_PORT=9899 sbt prepareServerConfig run
```

Then verify:
- `curl -i http://localhost:9899/healthz`
- sign in as an admin user and open `http://localhost:9899/admin`
- open the Analytics tab and confirm cards/tables render
- hit at least one public wave URL so the live view counters change
- refresh the Analytics tab and confirm the top viewed list updates

Record the exact commands and outcomes in issue `#605`.

- [ ] **Step 5: Commit the changelog / final polish**

```bash
git add wave/config/changelog.json
git commit -m "chore: document admin analytics tab"
```

- [ ] **Step 6: Push and open the PR**

After the implementation review loop is complete and all verification passes:

```bash
git push -u origin codex/admin-analytics-tab-605
```

Open a PR against `main` with:
- title: `[codex] Add analytics tab to admin dashboard`
- body including `Closes #605`
- verification commands and results
