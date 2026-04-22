# J2CL Parity Architecture

Status: Proposed  
Updated: 2026-04-22  
Owner: Project Maintainers  
Parent tracker: [#904](https://github.com/vega113/supawave/issues/904)  
Task issue: [#954](https://github.com/vega113/supawave/issues/954)

## 1. Decision Summary

Recommendation:

- keep **J2CL as the application/runtime layer** for Wave models, OT/state, transport, routing, and backend coordination
- adopt **Lit custom elements as the long-term view composition layer**
- use **server-rendered read-only HTML for first paint**, then progressively upgrade only the interactive regions instead of trying to hydrate the entire page as one framework-owned tree
- use **viewport-scoped fragment loading** as the default large-wave path, not whole-wave bootstrap

Do **not** make one of these two moves:

- do not keep scaling the current manual Elemental2 DOM approach into full StageOne/StageTwo/StageThree parity
- do not switch the primary browser runtime to a React-owned root tree

This memo **complements** [docs/j2cl-gwt3-decision-memo.md](./j2cl-gwt3-decision-memo.md). The earlier memo answered whether staged J2CL migration was viable at all. This memo answers the next question: **what runtime architecture should close the remaining GWT parity gap now that the first staged J2CL slices already exist?**

## 2. Current Repo Baseline

### 2.1 The legacy GWT client is still organized as staged runtime layers

The old client is not just a large pile of widgets. It has a runtime contract:

- `StageOne` owns the basic read surface: wave panel, focus frame, collapse, thread navigation, DOM view provider, and CSS-backed rendering (`wave/src/main/java/org/waveprotocol/wave/client/StageOne.java:45-190`).
- `StageTwo` owns live runtime behavior: connector activation, fragments wiring, dynamic rendering, and feature installation (`wave/src/main/java/org/waveprotocol/wave/client/StageTwo.java:1013-1184`).
- `StageThree` owns editing capabilities: edit actions/session, edit toolbar, view toolbar, mention/task/reaction-adjacent edit infrastructure (`wave/src/main/java/org/waveprotocol/wave/client/StageThree.java:66-220`).
- `Stages` and `StagesProvider` load those stages in order and then wire the real app behavior around them, including init-wave flow, `WaveContext`, toolbar actions, archive/pin wiring, and history mode (`wave/src/main/java/org/waveprotocol/wave/client/Stages.java:37-120`, `wave/src/main/java/org/waveprotocol/box/webclient/client/StagesProvider.java:159-225`).

That separation still matters. Closing parity is not only "render the same widgets in J2CL." It is replacing the staged lifecycle that currently controls read surface, live updates, and editing.

### 2.2 The shipped J2CL client exists, but its view layer is still ad hoc

The current J2CL path already proves the migration is real:

- the search/read/write flow exists
- the root shell exists
- the reversible bootstrap seam exists
- the GWT rollback path still exists

But the J2CL UI layer is currently built from direct DOM construction and manual `render(...)` calls:

- search shell: `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSearchPanelView.java:12-240`
- selected-wave panel: `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveView.java:7-105`
- compose flow: `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSidecarComposeView.java`
- root shell wrapper: `j2cl/src/main/java/org/waveprotocol/box/j2cl/root/J2clRootShellView.java:9-102`

The transport/backend seam is also real, but transitional:

- `J2clSearchGateway` fetches bootstrap data by scraping `/`, uses `XMLHttpRequest` for `/search`, and opens `/socket` directly with `document.cookie` session extraction (`j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSearchGateway.java:16-250`).

That is good enough for staged proof slices. It is not a durable parity architecture for the rest of the client.

### 2.3 The repo already has the two key loading primitives needed for parity

The missing architecture is not blocked on greenfield invention. Two important seams already exist:

- **server prerender / shell swap**
  - `WavePreRenderer` already prerenders a user's most recent wave to HTML for first paint and explicitly documents that GWT later replaces it with a shell swap rather than true hydration (`wave/src/main/java/org/waveprotocol/box/server/rpc/render/WavePreRenderer.java:40-161`)
  - the config flag for this is already present as `enable_prerendering` (`wave/config/reference.conf:127-132`)
- **viewport-scoped fragments / dynamic rendering**
  - the client already has a dynamic-rendering/fragments mode in `StageTwo` (`wave/src/main/java/org/waveprotocol/wave/client/StageTwo.java:1017-1184`)
  - the transport already carries viewport hints from the client (`wave/src/main/java/org/waveprotocol/box/webclient/client/RemoteViewServiceMultiplexer.java:183-224`)
  - the server already clamps and applies initial visible-segment selection (`wave/src/main/java/org/waveprotocol/box/server/frontend/WaveClientRpcImpl.java:371-428`)
  - the feature/config knobs already exist (`wave/config/reference.conf:405-441`)

This means the parity plan should not be "render the whole wave, then optimize later." The repo is already telling us the correct long-term direction: **first paint a small readable surface, then grow the active region incrementally.**

### 2.4 The current coexistence contract must remain first-class

The repo is still in dual-root mode:

- `/` serves the legacy GWT bootstrap by default
- `/?view=j2cl-root` is the direct J2CL diagnostic route
- operators can still switch `/` to the J2CL root via `ui.j2cl_root_bootstrap_enabled=true`

That contract is recorded in the current J2CL runbook (`docs/runbooks/j2cl-sidecar-testing.md:13-29`, `:178-220`) and in the current root-default restore work merged in PR `#953`.

The architecture recommendation must therefore preserve **coexistence and rollback** until read/live/edit parity is actually proven.

## 3. The Real Parity Gap

The remaining gap is not "J2CL lacks a few screens." It is this:

| Legacy contract | Current J2CL state | Gap |
|---|---|---|
| StageOne builds the readable wave surface and its interaction scaffolding | J2CL has isolated search, selected-wave, and compose views, but no durable read-surface component model | No scalable replacement for WavePanel-era composition, focus, collapse, thread-nav, and incremental read rendering |
| StageTwo owns live state, transport, fragments, reconnect, and feature activation | J2CL already has socket/search/bootstrap seams, but they are controller-local and transitional | No durable live-runtime layer that can own selected-wave state, route, fragments, read state, and reconnect across the full app |
| StageThree owns editing/toolbars on top of the live/read layers | J2CL has a narrow write pilot only | No durable composition/edit stage for real toolbar/editor parity |
| GWT can shell-swap from server prerendered HTML | J2CL currently mounts interactive DOM directly | No deliberate "read-only first, interactive later" contract for the J2CL path |
| GWT dynamic rendering can load partial visible content | J2CL selected-wave view still projects a small textual summary model | No parity path yet for visible-region wave rendering at scale |

So the architecture question is:

**What UI/runtime structure can host J2CL-owned logic while supporting progressive enhancement, incremental visible-region rendering, and coexistence with Java-server-rendered HTML?**

## 4. Framework Options

### 4.1 Option A: stay on plain Elemental2 + manual DOM

This is the status-quo direction.

Pros:

- no new view framework
- minimal new build surface
- entirely Java/J2CL-owned

Cons:

- the current J2CL code already shows the scaling problem: every view is manually assembling DOM and manually diffing view state
- there is no durable composition/lifecycle boundary comparable to the old stage model
- there is no framework help for component reuse, update scoping, testing structure, accessibility discipline, or progressive enhancement boundaries
- this turns parity work into a custom in-house UI framework project

Verdict: **reject**

Use plain Elemental2 only for low-level DOM/interop seams, not as the long-term UI composition model.

### 4.2 Option B: Lit custom elements with J2CL-owned controllers

Lit's strengths match this repo unusually well:

- Lit components are standard web components and can be used in plain HTML, other frameworks, or progressively enhanced pages ([What is Lit?](https://lit.dev/docs/v3/))
- Lit is explicitly positioned for interoperability and incremental adoption, including existing projects and server-rendered HTML ([Adding Lit to an existing project](https://lit.dev/docs/tools/adding-lit/), [Tools and workflows overview](https://lit.dev/docs/tools/overview/))
- Lit has a component lifecycle, reactive properties, and scoped rendering without taking ownership of the whole document ([Components overview](https://lit.dev/docs/components/overview/))
- Lit SSR and hydration exist for Lit-owned subtrees, including loading custom element definitions later and hydrating declarative-shadow-root output when needed ([Lit SSR server usage](https://lit.dev/docs/ssr/server-usage/), [Lit SSR client usage](https://lit.dev/docs/ssr/client-usage/))

Pros:

- maps naturally to progressive enhancement and HTML-first server output
- custom elements are a narrow interop boundary between Java/J2CL logic and browser UI composition
- lets J2CL remain authoritative for transport, OT/state, routing, and backend coordination
- incremental migration can happen panel-by-panel instead of root-by-root
- unlike React, Lit does not require the whole page to become one framework-owned tree

Cons:

- introduces a second language/runtime boundary for the view layer
- Lit SSR/hydration is still a Labs path and should not be the first dependency for the whole app
- some build plumbing is needed to ship Lit component modules cleanly beside the J2CL bundle

Verdict: **recommend**

Use Lit for the view layer, but keep the authoritative app state and transport in J2CL.

### 4.3 Option C: React as the primary root framework

React is strong at server rendering and hydration:

- `renderToPipeableStream` can stream the shell and later content from the server ([React `renderToPipeableStream`](https://react.dev/reference/react-dom/server/renderToPipeableStream))
- `hydrateRoot` can attach React to server-rendered HTML on the client, but the server and client trees must match and mismatches are bugs ([React `hydrateRoot`](https://react.dev/reference/react-dom/client/hydrateRoot))

Pros:

- very mature SSR/hydration ecosystem
- rich component model and tooling
- strong testing ecosystem

Cons:

- the main React hydration model assumes the server rendered **the same React tree**
- this repo's server is Java-first and already renders HTML through Java servlets/renderers
- making React the primary runtime would push SupaWave toward a Node/JS SSR tier or duplicated render logic
- that would invert the current staged migration by moving view ownership out of the J2CL path instead of completing it

Verdict: **reject as the primary runtime**

React is a good reference point, but adopting it would amount to a second migration: from GWT to J2CL **and** from Java-owned UI logic to a JS-owned root tree.

## 5. Why Lit Wins Here

The decisive factor is not that Lit has "better features" than React. It is that Lit fits the repo's actual constraints:

1. **J2CL wants a narrow framework boundary, not a new totalizing runtime.**  
   J2CL explicitly positions itself as Java that can work tightly with the web ecosystem rather than locking the app into one framework ([google/j2cl README](https://github.com/google/j2cl)). Lit's custom-element model fits that. React does not.

2. **The server is already Java and already renders HTML.**  
   SupaWave already has Java-side HTML renderers and prerender hooks. Lit can progressively enhance that world. React would prefer to own that world.

3. **Parity work needs upgrade boundaries, not whole-root replacement.**  
   Search rail, selected-wave chrome, compose affordances, toolbar surfaces, and thread-nav/focus surfaces can become custom elements incrementally. That is much closer to the current migration shape than a React root rewrite.

4. **Large-wave rendering needs component boundaries around visible fragments.**  
   Lit components can host visible-region sections cleanly while J2CL controllers own the fragment lifecycle and transport.

So the right split is:

- **J2CL Java**: models, OT/state, session bootstrap contract, route state, selected-wave/open/submit flows, fragment transport, read-state logic, feature flags
- **Lit custom elements**: shell chrome, search rail, selected-wave sections, compose panels, toolbar/view panels, fragment-region containers
- **Java server HTML**: first paint, read-only snapshot HTML, bootstrap JSON, rollback/coexistence routing

## 6. Proposed Stage Mapping For The J2CL Era

Do not literally recreate `StageOne`, `StageTwo`, and `StageThree` as old-style interfaces. But do preserve their responsibilities:

### 6.1 Read Surface Stage

Purpose:

- wave/read DOM ownership
- focus and selection framing
- collapse/thread navigation
- visible-region component boundaries
- read-only rendering that can start from server HTML

Maps back to:

- `StageOne` (`wave/src/main/java/org/waveprotocol/wave/client/StageOne.java:51-190`)

Recommended implementation shape:

- J2CL controller/model layer owns read state and fragment window state
- Lit custom elements render the read surface and subregions
- server can provide initial read-only HTML per visible region

### 6.2 Live Surface Stage

Purpose:

- socket/session/bootstrap lifecycle
- reconnect
- read-state refresh
- route/history
- fragment fetch policy
- feature activation and live-update application

Maps back to:

- `StageTwo` (`wave/src/main/java/org/waveprotocol/wave/client/StageTwo.java:1017-1184`)

Recommended implementation shape:

- J2CL remains authoritative here
- no framework should own this state machine
- Lit components receive state snapshots/events through a narrow boundary

### 6.3 Compose Stage

Purpose:

- edit session
- toolbar state
- mention/task/reaction/edit affordances
- write-path orchestration on top of the live/read layers

Maps back to:

- `StageThree` (`wave/src/main/java/org/waveprotocol/wave/client/StageThree.java:72-220`)

Recommended implementation shape:

- keep compose/edit orchestration in J2CL
- use Lit for toolbar and compose panel composition
- do not attempt compose-stage parity until the read/live stages are structurally stable

## 7. Read-Only First, Interactive Later

### 7.1 What not to do

Do not make the next step "render the full interactive app on the client, but faster."

That misses the point of the existing prerender and fragments seams.

### 7.2 Recommended load contract

1. **Server renders shell + read-only content**
   - route shell
   - signed-in/out chrome
   - selected wave or visible fragment slice as static HTML
   - bootstrap JSON for user/session/route/wave ids/visible-fragment hints

2. **Browser paints immediately**
   - no dependency on full J2CL runtime boot before content is visible

3. **J2CL boots**
   - reads bootstrap JSON, not scraped HTML where possible
   - opens live transport
   - establishes selected-wave/read-state/fragment state

4. **Lit components upgrade interactive regions**
   - search rail
   - shell chrome
   - selected-wave controls
   - compose/edit affordances

5. **Visible regions become live**
   - first through state attachment and event wiring
   - then through fragment-driven updates and incremental expansion

This first wave does **not** depend on Lit SSR. The intended first-paint path remains Java-server-rendered HTML plus progressive client upgrade. Lit SSR stays an optional later optimization for JS-owned subtrees if and when the repo decides that a Lit-rendered server path is worth the extra moving parts.

### 7.3 Why this fits SupaWave better than full framework hydration

`WavePreRenderer` already documents that the legacy system does a shell swap rather than true hydration (`wave/src/main/java/org/waveprotocol/box/server/rpc/render/WavePreRenderer.java:40-47`). That is the right starting mental model for J2CL too.

The next J2CL architecture should therefore aim for:

- **progressive upgrade**
- **interactive islands**
- **read-only server ownership of first paint**

not a requirement that the whole page be rendered by one client framework on both server and browser.

Lit supports that direction better than React because it can be introduced as component boundaries on top of HTML-first pages, while React's main hydration model expects the same React tree on both sides.

## 8. Large Waves: Visible Region First

The correct parity target is not "load the whole wave more efficiently." It is "load only the region the user needs now."

The repo already supports that direction:

- client open requests can include `viewportStartBlipId`, `viewportDirection`, and `viewportLimit` (`wave/src/main/java/org/waveprotocol/box/webclient/client/RemoteViewServiceMultiplexer.java:183-224`)
- server-side selection already clamps and emits a visible segment window (`wave/src/main/java/org/waveprotocol/box/server/frontend/WaveClientRpcImpl.java:371-428`)
- config already defines dynamic rendering and viewport limits (`wave/config/reference.conf:405-441`)

### Recommended J2CL parity contract

- initial open should request:
  - manifest/index
  - visible-region blips only
  - bounded `viewportLimit`
- J2CL live stage owns:
  - scroll/selection-derived viewport target
  - fetch-next / fetch-previous behavior
  - reconnect/resume rules
- Lit read-surface components own:
  - region containers
  - insertion/removal presentation
  - loading placeholders
  - local interaction affordances

This also means the selected-wave view in its current text-summary form should evolve into **fragment-region rendering**, not into a full eager whole-wave DOM dump.

## 9. Backend Coordination Changes The Repo Should Make

These are architectural recommendations, not this issue's implementation scope:

### 9.1 Replace HTML scraping bootstrap with explicit bootstrap data

Today `J2clSearchGateway` reads session/bootstrap details by fetching `/` and parsing HTML (`j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSearchGateway.java:23-37`).

That should become:

- server-rendered bootstrap JSON in the page
- explicit route/session/socket metadata
- explicit read-only snapshot metadata

This is a durability/readability change, not an auth-hardening implementation in `#954`. But it should be designed together with the follow-up auth hardening seam below so the repo does not replace HTML scraping with a bootstrap format that immediately conflicts with the eventual socket-auth cleanup.

### 9.2 Keep auth/session hardening separate, but document the seam

The current J2CL path still reads `JSESSIONID` from `document.cookie` before socket auth (`j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSearchGateway.java:54-57`, `:151-154`).

That is a known follow-up problem, but it should not block the view-framework decision. The memo should treat it as a required transport seam to clean up, not a reason to pick a different UI framework.

## 10. Build And Tooling Impact

The framework choice does affect tooling:

- the current J2CL sidecar is a Maven-driven J2CL bundle with Elemental2/JsInterop (`j2cl/pom.xml`)
- Lit does **not** require a framework-specific compiler or full-stack platform, which makes it feasible to add beside the current sidecar rather than replacing the whole build

Recommended build stance:

- keep the authoritative application/runtime build in the existing J2CL sidecar path
- add a small, explicit UI-module build only for Lit custom elements if needed
- do not make Node/JS SSR a prerequisite for the next parity wave

If the view layer cannot be introduced without a large parallel toolchain, the recommendation should be revisited. At the moment, Lit's minimal-tooling posture is one of the main reasons it fits.

The same is true for QA: this architecture should extend the current J2CL verification path in `docs/runbooks/j2cl-sidecar-testing.md`, not bypass it. Browser verification remains part of parity proof until the legacy GWT path is actually retired.

## 11. Accessibility And Localization

Parity is not just rendering and transport. The replacement stack also needs a plausible story for:

- keyboard navigation
- focus retention
- thread navigation affordances
- toolbar semantics
- screen-reader-visible structure
- localized labels/messages

This is another reason to avoid growing the current manual DOM layer forever. Component boundaries make it much easier to enforce accessible shell/read/compose structure consistently than one-off DOM assembly in every view class.

## 12. Recommended Next Slices Under #904

This memo implies the following follow-on sequence:

1. **Adopt the J2CL runtime split explicitly**
   - J2CL owns models/transport/route/live state
   - Lit owns UI composition

2. **Introduce explicit bootstrap JSON + read-only HTML islands**
   - stop depending on HTML scraping as the long-term bootstrap seam

3. **Port StageOne parity as a read-surface layer**
   - focus, collapse, thread-nav, visible fragment containers

4. **Port StageTwo parity as a live-surface layer**
   - read state, reconnect, route/history, fragment requester policy, visible-region updates

5. **Only then expand StageThree parity**
   - toolbar/edit/compose parity on top of a stable read/live base

This keeps the current rollback contract intact while moving the architecture toward a client that can eventually replace GWT without another total rewrite.

## 13. External References

- J2CL README: https://github.com/google/j2cl
- Elemental2 README: https://github.com/google/elemental2
- Lit overview: https://lit.dev/docs/v3/
- Lit components overview: https://lit.dev/docs/components/overview/
- Lit SSR server usage: https://lit.dev/docs/ssr/server-usage/
- Lit SSR client usage: https://lit.dev/docs/ssr/client-usage/
- Lit tools overview: https://lit.dev/docs/tools/overview/
- React `hydrateRoot`: https://react.dev/reference/react-dom/client/hydrateRoot
- React `renderToPipeableStream`: https://react.dev/reference/react-dom/server/renderToPipeableStream

## 14. Bottom Line

The repo should not choose between:

- "keep hand-writing DOM in J2CL forever"
- "move the whole UI to React"

The correct middle path is:

**J2CL for application logic and live runtime, Lit for composable browser UI, Java server HTML for read-only first paint, and fragment-window loading as the default large-wave strategy.**

That is the smallest architecture change that can still plausibly close the real GWT parity gap.
