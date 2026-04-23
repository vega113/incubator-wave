# Issue #965 Server-First Selected-Wave HTML And In-Place Upgrade Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Render a real read-only selected-wave snapshot on the server for the J2CL root-shell route before the full client boots, then upgrade that exact surface in place inside the post-`#964` Lit shell without blanking the page, duplicating the selected-wave DOM, or widening the rollback/coexistence seam.

**Architecture:** Start from the post-`#963` bootstrap-contract baseline on `origin/main` and the post-`#964` Lit shell baseline once `#964` lands. Add a targeted server renderer for the requested `wave=` route param using the existing `WaveContentRenderer` / `WavePreRenderer` direction, thread that HTML into `HtmlRenderer.renderJ2clRootShellPage(...)` as server-first skeleton markup, then make the root-shell J2CL boot path adopt existing DOM instead of clearing it so the server snapshot stays visible until the first live selected-wave update replaces it.

**Tech Stack:** Jakarta servlet + `HtmlRenderer`, existing server renderers under `wave/src/main/java/org/waveprotocol/box/server/rpc/render/`, `WaveletProvider`-backed snapshot lookup, J2CL Java DOM views/controllers, Lit shell assets from `#964`, the explicit `/bootstrap.json` contract from `#963`, existing `j2clSearchTest` / server junit coverage, and local smoke verification through `scripts/worktree-boot.sh` / `scripts/wave-smoke.sh`.

---

## 1. Goal / Baseline / Root Cause

### 1.1 Baseline

This plan is written against the current prep-lane state in `/Users/vega/devroot/worktrees/issue-965-server-first-paint` plus the already-merged `#963` contract on `origin/main` and the still-pending `#964` shell work:

- The active worktree is **behind `origin/main` by one commit**. `origin/main` is at commit `4967a897f` (`feat(j2cl): add explicit bootstrap JSON contract (#963) (#980)`), while this branch is still at `4f7c0d777` (`#931`). Implementation must therefore begin with a rebase after `#964` lands; do not implement `#965` on the stale pre-`#963` tree.
- `#963` is already the intended bootstrap baseline:
  - `wave/src/main/java/org/waveprotocol/box/common/J2clBootstrapContract.java`
  - `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/J2clBootstrapServlet.java`
  - `j2cl/src/main/java/org/waveprotocol/box/j2cl/transport/SidecarSessionBootstrap.java` with `fromBootstrapJson(...)`
  - `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSearchGateway.java` switched from scraping `/` to fetching `/bootstrap.json`
- `#964` has a reviewed plan at `docs/superpowers/plans/2026-04-23-issue-964-lit-root-shell.md`, but its implementation is not yet on `main`. `#965` must treat `#964` as a hard dependency because the server-first snapshot needs the Lit shell and root-shell progressive-enhancement seam that `#964` introduces.
- The current route/coexistence seam already exists in `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/WaveClientServlet.java:128-166`: `view=j2cl-root` still forces the explicit J2CL route, while the `j2cl-root-bootstrap` flag still controls whether `/` opts into that route.
- The current root-shell page is still emitted by `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java:3215-3410`. Today that method only renders shell chrome plus a placeholder workflow host; it does not render selected-wave HTML even when `wave=` is present.
- The server already has the rendering direction `#965` should reuse:
  - `wave/src/main/java/org/waveprotocol/box/server/rpc/render/WavePreRenderer.java:40-161` documents the repo's "render first, shell swap later" seam.
  - `wave/src/main/java/org/waveprotocol/box/server/rpc/render/WaveContentRenderer.java:51-208` can already turn a `WaveViewData` snapshot into full read-only HTML.
- The current client boot path will destroy any server-rendered snapshot immediately:
  - `j2cl/src/main/java/org/waveprotocol/box/j2cl/sandbox/SandboxEntryPoint.java:41-52` clears the mount host with `host.innerHTML = ""` before entering root-shell mode.
  - `j2cl/src/main/java/org/waveprotocol/box/j2cl/root/J2clRootShellView.java:13-19` clears the workflow host again.
  - `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSearchPanelView.java:48-60` clears the shell host again before rebuilding the search layout.
  - `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveView.java:18-24` clears the selected-wave host again before creating the card.
- The current selected-wave model is still a text-summary projection, not a server-first DOM adoption seam:
  - `J2clSelectedWaveProjector` extracts string entries from fragments/documents (`extractContentEntries`, `extractDocumentEntries`) at `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveProjector.java:46-50`, `:259-297`.
  - `J2clSelectedWaveView` then renders those strings into `<pre>` nodes and blank empty states at `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveView.java:67-105`.
  - `J2clSelectedWaveController` already ignores channel-establishment frames at `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveController.java:286-309`, `:376-382`; `#965` should reuse that existing predicate for its "first real live update" swap trigger rather than inventing a second heuristic.

### 1.2 Root Cause

The repo already has almost every ingredient `#965` needs, but the ingredients do not meet:

1. The server can render a wave snapshot, but there is no helper that renders the **requested** `wave=` query target for the root-shell route.
2. The root-shell HTML has no "server selected-wave snapshot lives here" contract. It only exposes a generic workflow host placeholder.
3. The J2CL root-shell boot path is destructive. It assumes the correct startup sequence is "clear everything, rebuild everything", which is the opposite of the in-place upgrade requirement.
4. The selected-wave view/controller pair treats "loading" as a blank client-owned state, so even if server HTML existed, the first controller render would overwrite it before live data arrives.

### 1.3 Narrow Root-Cause Summary

`#965` is not blocked on inventing SSR from scratch. It is blocked on three narrow seams:

- add a targeted server-side selected-wave snapshot renderer for `wave=...`
- teach the root-shell HTML to expose a server-first snapshot contract
- teach the J2CL root-shell boot path to adopt and swap that DOM instead of clearing it

## 2. Acceptance Criteria

`#965` is complete when all of the following are true:

- After rebasing onto a `main` that includes `#963` and `#964`, a signed-in request to `/?view=j2cl-root&wave=<wave-id>` returns HTML that already contains a readable selected-wave snapshot before `/j2cl-search/sidecar/j2cl-sidecar.js` runs.
- The returned server HTML is readable with JavaScript disabled:
  - shell chrome remains visible
  - selected-wave title/body/participants metadata are visible
  - no client-only blank panel is shown in place of the selected wave
- The first J2CL root-shell boot upgrades that exact snapshot in place:
  - no full blank flash
  - no duplicate selected-wave roots
  - no focus jump caused by the upgrade
  - no loss of the `?view=j2cl-root&q=...&wave=...` route/return-target contract
- Signed-out requests and signed-in requests without a valid accessible `wave=` remain safe:
  - no unauthorized wave data leaks
  - the root shell still renders
  - the client can still sign in and continue normally
  - the explicit `wave=` deep link stays preserved in the route/return-target so post-sign-in restore can reopen the requested wave
- The default `/` route and the `j2cl-root-bootstrap` rollback semantics are unchanged.
- The post-`#963` JSON bootstrap contract remains the only root-session bootstrap path on the rebased baseline; no new HTML scraping is reintroduced.
- Root-shell responses that contain per-viewer snapshot state are served with `Cache-Control: private, no-store` plus `Vary: Cookie`.
- Tests cover:
  - targeted server snapshot rendering for an accessible requested wave
  - safe fallback for inaccessible / missing / signed-out wave requests
  - root-shell HTML markers for server snapshot + upgrade placeholder
  - root-shell/J2CL boot preserving server HTML until the first live update
  - first live update swapping server HTML out exactly once
  - no regression to the standalone `/j2cl-search/index.html` sidecar route
- Local verification proves:
  - server HTML is present in `curl` output before JS runs
  - browser root-shell load shows the selected-wave content immediately
  - the live J2CL update path still takes over successfully

## 3. Scope And Non-Goals

### 3.1 In Scope

- Requested-wave server rendering for the J2CL root-shell route only.
- Root-shell HTML contract updates so the server snapshot and upgrade placeholder are explicit.
- Client DOM-adoption changes needed to preserve server HTML until the live selected-wave path is ready.
- Narrow selected-wave controller/view changes required to swap from server snapshot to live J2CL content without flicker.
- Root-shell first-paint / shell-swap observability for this slice.
- Changelog fragment and verification record.
- A bounded server-render budget / payload cap so a very large wave falls back safely instead of turning first paint into an unbounded render.

### 3.2 Explicit Non-Goals

- No default-root cutover changes. `/` stays governed by the existing `j2cl-root-bootstrap` control plane.
- No whole-app hydration or React-style root ownership.
- No full StageOne read-surface parity work (`#966`), viewport-fragment windowing (`#967`), or live-surface parity (`#968`).
- No compose / toolbar / overlay / edit parity work.
- No redefinition of the `#963` bootstrap JSON shape unless a tiny additive field is proven strictly necessary during implementation review. The preferred path is to reuse URL state plus existing `shell.routeReturnTarget`.
- No implementation work before `#964` lands on `main`.
- No text-only fallback for missing Stitch artifacts: design-packet §6 marks the server-first family as Required, so an unavailable Stitch service pauses implementation until the artifact is produced and pinned.

## 4. Dependency Readiness

### 4.1 `#963` Status: Merged, But Not Yet In This Worktree

Verified live state:

- PR `#980` for `#963` merged at `2026-04-23T13:30:53Z`.
- `origin/main` now contains the `J2clBootstrapContract` + `/bootstrap.json` contract and a `J2clSearchGateway` that reads `/bootstrap.json` instead of scraping `/`.
- This worktree branch does **not** contain that merge yet.

Implementation consequence:

- The first implementation step for `#965` must be: `git fetch origin && git rebase origin/main` after `#964` merges.
- Do not write or review implementation code against the stale pre-`#963` checkout.

### 4.2 `#964` Status: Hard Dependency

`#965` depends on `#964` landing because the reviewed `#964` plan establishes the shell/chrome progressive-enhancement seam this slice upgrades inside. `#965` should assume these artifacts exist on `main` before Task 1 starts:

- `j2cl/lit/` package and the staged `war/j2cl/assets/shell.js` + `shell.css`
- root-shell markup using the `shell-*` primitives from the `#964` plan
- a stable `shell-main-region` / `#j2cl-root-shell-workflow` contract
- the no-unstyled-flash shell fallback from `#964`

Implementation consequence:

- `#965` is **not** ready to code on the current mainline.
- `#965` **is** ready to start immediately once `#964` merges and this lane rebases onto the updated `origin/main`.
- Task 1 must refresh all file:line anchors in this plan against the rebased post-`#964` tree before code edits start.

### 4.3 Design / Parity Docs Status

These prerequisites are already ready and should be consumed directly:

- `docs/j2cl-gwt-parity-matrix.md:142-153` (`R-6.1`, `R-6.3`, `R-6.4`)
- `docs/j2cl-lit-design-packet.md:342-359` (§5.6 server-first first-paint primitives)
- `docs/j2cl-parity-architecture.md`
- `docs/j2cl-parity-issue-map.md`

## 5. Slice Parity Packet - Issue #965

**Title:** Serve read-only selected-wave HTML first and upgrade it inside the J2CL root shell  
**Stage:** server-first  
**Dependencies:** `#962` merged, `#963` merged on `origin/main` but not yet rebased into this lane, `#964` not yet merged

### Parity matrix rows claimed

- `R-6.1` - Server-rendered read-only first paint
- `R-6.3` - Shell-swap upgrade path
- `R-6.4` - Rollback-safe coexistence

### GWT / J2CL seams de-risked

- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/WaveClientServlet.java:128-166` - route/coexistence selector
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java:3215-3410` - current root-shell HTML contract
- `wave/src/main/java/org/waveprotocol/box/server/rpc/render/WavePreRenderer.java:40-161` - existing shell-swap precedent
- `wave/src/main/java/org/waveprotocol/box/server/rpc/render/WaveContentRenderer.java:51-208` - read-only wave HTML renderer
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/sandbox/SandboxEntryPoint.java:41-52`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/root/J2clRootShellView.java:13-19`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSearchPanelView.java:48-60`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveView.java:18-24`

### Server-first DOM marker contract

Task 3 and Task 4 must share one exact marker contract. Unless the post-`#964` rebase forces a small rename, the implementation should keep this shape:

- Workflow root inside `#j2cl-root-shell-workflow`:
  - `section.sidecar-search-shell[data-j2cl-server-first-workflow="true"]`
- Selected-wave column host:
  - `div.sidecar-selected-host[data-j2cl-selected-wave-host="true"]`
- Server snapshot wrapper:
  - `section[data-j2cl-server-first-selected-wave="<wave-id>"]`
  - `data-j2cl-server-first-mode="snapshot|no-wave|signed-out|denied|render-error|budget-exceeded|payload-exceeded"`
  - `data-j2cl-upgrade-placeholder="selected-wave"`
- Client adoption rule:
  - if `data-j2cl-server-first-selected-wave` is present, adopt and preserve the wrapper until the first non-establishment selected-wave update
  - if only `data-j2cl-server-first-mode` is present without a wave id, keep the safe fallback chrome but do not try to swap live content into it until route state selects a real wave

This contract gives the client one authoritative way to distinguish:

- real server snapshot present
- safe empty state present
- signed-out / denied / renderer-error fallback present

Reason-code mapping:

- `snapshot` - real rendered snapshot present
- `no-wave` - no `wave=` query was supplied
- `signed-out` - preserve the requested route/return-target, but do not render wave content
- `denied` - authenticated viewer lacks access to the requested wave
- `render-error` - unexpected renderer failure
- `budget-exceeded` - render wall-clock budget exceeded
- `payload-exceeded` - rendered fragment exceeded the byte cap

Render bound for Task 3:

- wall-clock render budget: `150ms`
- rendered fragment size cap: `131072` bytes (128 KiB)

If either limit is exceeded, the server returns the safe fallback marker state instead of partial content and emits the matching reason code.

### Rollout / rollback seam

- Route selector: `?view=j2cl-root`
- Default `/`: unchanged; still governed by `j2cl-root-bootstrap`
- Rollback: turn the feature flag off for `/`; keep `?view=j2cl-root` as the explicit diagnostic route
- No schema or persistence migration required

### Required-match behaviors

- A signed-in selected-wave root-shell URL shows readable content before full client activation.
- Upgrade happens in place, not by blanking and rebuilding from empty.
- Signed-out / inaccessible wave states remain safe and readable.
- The explicit J2CL route and existing rollback path remain reversible.

### Keyboard / focus plan

- The server-rendered shell keeps the first tab stop and main-region focus order established by `#964`.
- The selected-wave upgrade does not steal focus from the active element.
- The swap from server snapshot to live view is a content update within the selected-wave region, not a root-level replacement.

### Accessibility plan

- Server HTML alone exposes the selected-wave content as readable, non-interactive content.
- The upgrade does not duplicate landmarks or leave duplicate live regions in the DOM.
- Signed-out fallback remains semantically readable and does not silently hide the missing snapshot state.
- Snapshot HTML is wrapped under a dedicated server-first container so its `wave-content` / `blip-*` classes stay scoped to that region and do not collide with `#964`'s `shell-*` primitives.
- The workflow interior keeps the current `sidecar-*` classes only inside `[data-j2cl-server-first-workflow="true"]`; `shell-*` remains the outer `#964` contract, so styling responsibilities stay separated.

### Observability plan

- Reuse the existing `window.__stats` event-buffer pattern already used by the legacy bootstrap page so the root-shell path can emit:
  - `j2cl.root_shell.server_first_paint`
  - `j2cl.root_shell.shell_swap`
  - failure reason codes for "no wave", "access denied", "renderer error", and "client swap timeout"
- Record server-side render failures in the existing servlet logs; do not invent a new backend sink for this slice.
- Task 3 owns adding the root-shell `window.__stats` shim if the rebased post-`#964` `HtmlRenderer.renderJ2clRootShellPage(...)` does not already expose it.
- If `#964` already introduced a root-shell stats shim with a compatible buffer shape, Task 3 extends that shim rather than replacing it.
- Event payload shape:
  - `module: "j2cl.root_shell"`
  - `subtype: "server_first_paint" | "shell_swap"`
  - `reason: <mode-or-failure-code>`
  - `durationMs: <number>`
  - `waveIdPresent: <boolean>`

### Verification plan

- Server tests:
  - targeted snapshot renderer test
  - `WaveClientServletJ2clRootShellTest`
  - `WaveContentRendererTest`
- Client tests:
  - root-shell DOM adoption test
  - `org.waveprotocol.box.j2cl.search.J2clSelectedWaveControllerTest` carries the preserve-until-first-update, focus-preservation, reconnect, and read/unread-after-swap assertions
  - no-duplicate-swap regression test
  - focus-preservation assertion: capture `document.activeElement` before the swap and assert it is unchanged after the first live update replaces the snapshot
  - reconnect / read-unread regression after swap for the `#931` behavior
- Build gate:
  - `sbt -batch j2clLitBuild j2clLitTest j2clSearchTest testOnly org.waveprotocol.box.server.rpc.WaveClientServletJ2clRootShellTest testOnly org.waveprotocol.box.server.rpc.render.WaveContentRendererTest testOnly org.waveprotocol.box.server.rpc.render.J2clSelectedWaveSnapshotRendererTest testOnly org.waveprotocol.box.j2cl.search.J2clSelectedWaveControllerTest compileGwt Universal/stage`
- Local smoke / browser:
  - reuse file-store data if needed via `scripts/worktree-file-store.sh --source /Users/vega/devroot/incubator-wave`
  - boot with `scripts/worktree-boot.sh`
  - `curl` the root-shell URL and confirm server snapshot markers are present before JS
  - browser-check signed-in selected-wave root-shell load, refresh, and post-boot live update behavior
  - browser-check `/j2cl-search/index.html` still works unchanged
  - browser-check default `/` with `j2cl-root-bootstrap` off still stays on the legacy path

### Stitch artifact pinning (design-packet §6, Required for `#965`)

Before code task execution starts, Task 2 below must pin a committed Stitch artifact that covers:

- `server-shell-skeleton`
- `server-wave-skeleton`
- `server-upgrade-placeholder`
- `server-rollback-chrome`

The implementation PR must update this plan section with:

- Stitch project id
- screen ids for all four variants above
- applied design-system id

Implementation stays blocked until those ids are committed alongside the slice packet update.
If the Stitch service or connector is unavailable, the lane owner updates the GitHub issue immediately and pauses implementation rather than silently proceeding without the required artifact.

## 6. File Structure

### 6.1 New Files

- `wave/src/main/java/org/waveprotocol/box/server/rpc/render/J2clSelectedWaveSnapshotRenderer.java` - targeted requested-wave lookup + render helper for the root shell
- `wave/src/test/java/org/waveprotocol/box/server/rpc/render/J2clSelectedWaveSnapshotRendererTest.java` - renderer coverage for accessible, missing, and denied waves
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/root/J2clServerFirstRootShellDom.java` - helper that finds/adopts the server-rendered root-shell/search/selected-wave nodes instead of clearing them
- `j2cl/src/test/java/org/waveprotocol/box/j2cl/root/J2clServerFirstRootShellDomTest.java` - DOM adoption / marker lookup coverage
- `wave/config/changelog.d/2026-04-23-j2cl-server-first-paint.json` - changelog fragment

### 6.2 Modified Files

- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/WaveClientServlet.java` - call the targeted snapshot renderer for signed-in `wave=` requests on the J2CL root-shell path
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java` - emit server-first selected-wave snapshot markup, upgrade markers, and root-shell stats shim
- `wave/src/jakarta-test/java/org/waveprotocol/box/server/rpc/WaveClientServletJ2clRootShellTest.java` - cover snapshot HTML presence and safe fallback states
- `wave/src/test/java/org/waveprotocol/box/server/rpc/render/WaveContentRendererTest.java` - add assertions needed by the selected-wave snapshot contract if the fragment wrapper changes
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/sandbox/SandboxEntryPoint.java` - stop clearing the root-shell mount host when server-first markers are present
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/root/J2clRootShellController.java` - thread the adopted DOM helper through the root-shell bootstrap path
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/root/J2clRootShellView.java` - adopt existing workflow/search/selected-wave containers instead of rebuilding from empty
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSearchPanelView.java` - adopt existing root-shell layout nodes so the server snapshot survives client boot
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveController.java` - keep the server snapshot visible during the initial loading window until the first live update arrives
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveView.java` - adopt/swap the server-rendered selected-wave region instead of clearing it immediately
- `j2cl/src/test/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveControllerTest.java` - extend with preserve-until-first-update, reconnect, focus-preservation, and read/unread-after-swap coverage
- `j2cl/src/test/java/org/waveprotocol/box/j2cl/sandbox/SandboxBuildSmokeTest.java` - extend if needed for any root-shell stats shim or sidecar-route regression coverage

### 6.3 Inspect-Only References

- `docs/superpowers/plans/2026-04-23-issue-964-lit-root-shell.md`
- `wave/src/main/java/org/waveprotocol/box/common/J2clBootstrapContract.java` on `origin/main`
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/J2clBootstrapServlet.java` on `origin/main`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSearchGateway.java` on `origin/main`

## 7. Implementation Order

### Task 1: Rebase Onto The Real Baseline

- [ ] Wait for `#964` to merge to `main`. Do not start code changes before that merge exists on `origin/main`.
- [ ] Run `git fetch origin && git rebase origin/main` in this worktree so the branch contains both `#963` and `#964`.
- [ ] Verify the rebased baseline contains:
  - the `#963` `/bootstrap.json` files listed in §4.1
  - the `#964` Lit shell package / staged shell assets / root-shell primitive markup
- [ ] Re-run the narrow seam audit after the rebase and re-anchor every file:line reference in §1 and §5. If `#964` changed file names or ownership materially, update §6 before touching code.

### Task 2: Pin The Required Stitch Server-First Artifact

- [ ] Create or update the required Stitch project for the server-first first-paint family.
- [ ] Capture and commit the project id, the four required screen ids, and the design-system id back into §5 of this plan before code work continues.
- [ ] Confirm the visual artifact covers:
  - shell skeleton
  - selected-wave snapshot container
  - upgrade placeholder state
  - rollback chrome state
- [ ] If Stitch is unavailable, post the blocker to the GitHub issue immediately and stop; do not replace this required artifact with prose-only design guesses.

### Task 3: Add The Requested-Wave Server Snapshot Renderer

- [ ] Implement `J2clSelectedWaveSnapshotRenderer` under `wave/src/main/java/org/waveprotocol/box/server/rpc/render/` using the existing `WaveContentRenderer` / `WaveletProvider` seam rather than inventing a second rendering stack.
- [ ] Scope the renderer narrowly:
  - render only the requested `wave=` target
  - require an authenticated viewer
  - return safe "no snapshot" state for missing / inaccessible waves
  - never leak inaccessible content into the HTML
- [ ] Update `WaveClientServlet` so the J2CL root-shell branch resolves the requested `wave=` and passes the rendered snapshot (or safe empty state) into `HtmlRenderer.renderJ2clRootShellPage(...)`.
- [ ] Extend `HtmlRenderer.renderJ2clRootShellPage(...)` so the page contains the exact marker contract from §5, a real selected-wave snapshot region instead of only the current placeholder text, and the root-shell `window.__stats` shim if it is not already present after the `#964` rebase.
- [ ] Keep the route/return-target logic exactly aligned with the existing `q=` / `wave=` pass-through behavior.
- [ ] Bound the server render path:
  - if rendering exceeds `150ms` or produces more than `131072` bytes of snapshot HTML, return the safe empty marker state instead of blocking first paint
  - emit the corresponding failure reason into logs / stats
- [ ] Preserve `wave=` in signed-out root-shell deep links and return-targets, but render only the `signed-out` safe fallback mode under the marker contract.
- [ ] Add explicit response-header handling for server-first root-shell pages so per-viewer snapshot HTML is served as `Cache-Control: private, no-store` with `Vary: Cookie`.
- [ ] Add server-side tests proving:
  - accessible signed-in wave renders snapshot HTML
  - signed-out requests do not render wave content
  - inaccessible or unknown wave ids do not leak content
  - root-shell HTML still renders safely in all cases
  - rollback path for `/` with `j2cl-root-bootstrap` off remains unchanged
  - the snapshot renderer is not invoked for the legacy `/` route when rollback is active
  - cache-control / vary headers are correct for server-first responses
  - `budget-exceeded` and `payload-exceeded` fallbacks map to the expected marker modes

### Task 4: Make Root-Shell Boot Non-Destructive

- [ ] Introduce `J2clServerFirstRootShellDom` (or an equivalently narrow helper) so the root-shell path can adopt server-rendered nodes instead of clearing them.
- [ ] Remove or conditionalize the unconditional `innerHTML = ""` clears on the root-shell path:
  - `SandboxEntryPoint.mount(...)`
  - `J2clRootShellView`
  - `J2clSearchPanelView`
  - `J2clSelectedWaveView`
- [ ] Preserve the standalone `/j2cl-search/index.html` sidecar path behavior. The non-destructive adoption logic is for the root-shell server-first mode, not the isolated sidecar route.
- [ ] Ensure the root-shell search column can still boot normally even when only the selected-wave column has meaningful server-rendered content.
- [ ] Add J2CL tests proving the server-first markers are adopted and not discarded during initial mount, while the standalone sidecar route still builds from empty as before.

### Task 5: Swap From Server Snapshot To Live Selected-Wave Content

- [ ] Update the selected-wave controller/view so the initial "loading" render does not blank the server snapshot before the first live update arrives.
- [ ] On the first non-establishment selected-wave update, as defined by the existing `J2clSelectedWaveController.isChannelEstablishmentUpdate(...)` predicate, replace the server snapshot exactly once and continue using the live J2CL render path for later updates.
- [ ] Keep reconnect and read/unread behavior from `#931` intact after the swap.
- [ ] If the live stream errors before the first non-establishment update arrives, keep the server snapshot visible, surface a non-destructive client error state, and allow retry/refresh to continue from that snapshot instead of blanking the region.
- [ ] Emit root-shell first-paint / shell-swap events through the `window.__stats` shim added in Task 3, including failure reasons for swap timeouts or missing live data.
- [ ] Add tests proving:
  - the server snapshot remains visible during the bootstrap/loading window
  - the first live update replaces it
  - later updates do not recreate duplicate containers
  - the swap does not regress the route-selected wave state
  - focus stays on the same active element across the swap
  - read/unread + reconnect behavior from `#931` still works after the swap
  - a pre-swap live-stream error keeps the snapshot and surfaces the expected client error mode

### Task 6: Verification, Changelog, And Issue Traceability

- [ ] Add the changelog fragment under `wave/config/changelog.d/`.
- [ ] Run the targeted test/build command from §5.
- [ ] Run a local root-shell smoke with realistic file-store data if needed, and record the exact command plus outcome in `journal/local-verification/2026-04-23-issue-965-server-first-paint.md`.
- [ ] Browser-verify:
  - signed-in `/?view=j2cl-root&wave=<existing-wave>`
  - refresh on the same URL
  - signed-out `/?view=j2cl-root&wave=<existing-wave>`
  - `/j2cl-search/index.html`
  - default `/` remains unchanged
- [ ] Mirror the exact verification commands/results into the GitHub issue comment when implementation starts or completes.

## 8. Ready-To-Start Call

`#965` is **not** ready to implement on the current checkout because this lane is still missing the merged `#963` commit and `#964` has not landed yet.

`#965` **is ready to start immediately after `#964` lands**, because:

- the parity/design docs are already in place
- `#963` is already merged on `origin/main`
- the remaining work is a narrow, well-mapped server-render + DOM-adoption slice
- no additional design or architecture blocker remains beyond rebasing onto the updated mainline
