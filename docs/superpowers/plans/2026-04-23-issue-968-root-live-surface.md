# Issue #968 Root-Shell Live Surface Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Promote the current J2CL sidecar workflow into a durable StageTwo-like live surface inside the J2CL root shell, covering route/history continuity, reconnect, selected-wave/open-state continuity, read-state/live-state wiring, and root-level integration with the existing shell/bootstrap seams, without widening into StageThree compose parity or a default-root cutover.

**Architecture:** Keep J2CL authoritative for the live-runtime state machine. The root shell should stop behaving like "the sidecar widget mounted in a different div" and instead gain a root-scoped live-runtime coordinator that owns bootstrap/session state, route state, reconnect status, selected-wave continuity, and fragment-policy inputs. The existing search, selected-wave, and compose controllers remain the narrowest reusable seams, but they should be composed under a root-runtime owner rather than each independently reacquiring bootstrap and lifecycle state. Lit stays visual-only for this slice: existing shell primitives and status-strip slots render state that J2CL publishes.

**Tech Stack:** J2CL Java under `j2cl/src/main/java`, Jakarta servlet/rendering seams under `wave/src/jakarta-overrides/java`, generated protocol models under `gen/messages/`, existing root-shell Lit primitives under `j2cl/lit/`, the explicit `/bootstrap.json` contract from `#963`, the unread/read-state path from `#931`, the StageOne read surface from `#966`, viewport-hint/fragment-window support from `#967` plus `#993`, server-side viewport-hint support under `WaveClientRpcImpl`, and targeted J2CL/server unit tests plus local browser verification on `/?view=j2cl-root`.

---

## 1. Goal / Baseline / Root Cause

### 1.1 Current baseline in this worktree

The current repo state already provides several of the prerequisites `#968` must consume rather than recreate:

- `#933` is closed: J2CL sidecar/root-shell sockets now rely on the WebSocket upgrade handshake and host-match guard instead of a client-side auth frame. The current outbound frame contract is covered by `j2cl/src/test/java/org/waveprotocol/box/j2cl/search/J2clSearchGatewayAuthFrameTest.java`.
- `#931` is closed: the selected-wave panel already has real unread/read-state fetching, debounce, reconnect persistence, and visibility-based refresh inside `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveController.java`.
- `#963` is closed: root/session/socket metadata now come from `/bootstrap.json`, served by `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/J2clBootstrapServlet.java`, with the contract pinned in `wave/src/main/java/org/waveprotocol/box/common/J2clBootstrapContract.java`.
- `#964` is closed: the root shell already exposes Lit shell primitives such as `shell-main-region` and `shell-status-strip`, and `HtmlRenderer.renderJ2clRootShellPage(...)` mounts the J2CL bundle into the existing shell page.
- `#965` is merged: the root-shell host can serve selected-wave HTML before J2CL boot; `#968` should preserve that server-first host contract rather than reimplement it.
- `#966` is merged: StageOne read-surface parity exists; `#968` consumes it only through the current selected-wave/read-state seams.
- `#967` is merged: viewport-hint transport, selected-wave fragment fetching, viewport growth, and `J2clSelectedWaveViewportState` are present in the J2CL selected-wave path.
- `#993` is merged: viewport metadata edge cases have additional hardening on top of `#967`.

### 1.2 Exact current seams

These seams are the actual starting point for `#968`:

- `j2cl/src/main/java/org/waveprotocol/box/j2cl/root/J2clRootShellController.java:21-69`
  - The root shell currently just instantiates the existing `J2clSearchPanelController`, `J2clSelectedWaveController`, `J2clSidecarComposeController`, and `J2clSidecarRouteController` directly.
  - There is no root-scoped live-runtime owner. The root shell is still a composition wrapper around the sidecar controllers.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSidecarRouteController.java:102-138`
  - Route/history state is still the sidecar route model: query + selected-wave id, optionally pinned behind `view=j2cl-root`.
  - The controller is already root-shell aware enough to preserve the explicit selector, but it is still named and scoped as sidecar route state, not a root-runtime route contract.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveController.java:238-500`
  - Reconnect, read-state refresh, and visibility-refresh logic already exist, but they are owned entirely by the selected-wave controller.
  - Bootstrap is refetched on open/reconnect from inside this controller, which keeps session/bootstrap lifecycle controller-local instead of root-scoped.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSearchGateway.java:45-115`
  - Selected-wave open still creates a socket per opened wave and sends a manual `ProtocolOpenRequest` frame, now with optional `SidecarViewportHints`.
  - The gateway fetches bootstrap, opens sockets, and fetches fragment windows, but it does not own a shared root session/bootstrap snapshot or any root-runtime continuity.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/transport/SidecarOpenRequest.java:7-31`
  - The sidecar open request carries participant id, wave id, wavelet prefixes, and `SidecarViewportHints`.
  - This is now a consumed seam, not an implementation gap for `#968`.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/transport/SidecarTransportCodec.java:13-30`
  - The manual JSON encoder writes required fields `"1"`, `"2"`, and `"3"` plus optional viewport fields `"5"`, `"6"`, and `"7"`.
  - Transport-codec behavior is already covered by existing J2CL tests from the merged viewport work.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveViewportState.java`
  - The selected-wave path now has an explicit viewport-window model from `#967`.
  - `#968` should consume this model through existing selected-wave controller behavior, not duplicate fragment growth logic in the root coordinator.
- `gen/messages/org/waveprotocol/box/common/comms/proto/ProtocolOpenRequestProtoImpl.java:856-900`
  - The generated protocol model already reserves the JSON/proto slots for viewport hints:
    - `"5"` = `viewportStartBlipId`
    - `"6"` = `viewportDirection`
    - `"7"` = `viewportLimit`
- `wave/src/main/java/org/waveprotocol/box/server/frontend/WaveClientRpcImpl.java:371-427`
  - The server already validates/clamps viewport hints and selects visible segments accordingly.
  - This means the server-side StageTwo/fragment seam already exists; the J2CL sidecar/root-shell transport simply does not reach it yet.
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java:3327-3415`
  - The root-shell bootstrap script already normalizes legacy hash deep links, hooks `pushState`/`replaceState`, and keeps auth return-target links in sync.
  - That means route-awareness is currently split between inline server JS and `J2clSidecarRouteController`.
- `j2cl/lit/src/elements/shell-status-strip.js`
  - The shell already has a status/live-region surface. `#968` should publish live runtime state into this existing seam, not invent a parallel root status DOM.

### 1.3 Root cause

The missing piece is not "more selected-wave logic." The missing piece is a durable StageTwo-like root-runtime owner.

Today:

- bootstrap/session state is reacquired independently by search, selected-wave open, and compose flows;
- route/history state is split between root-shell bootstrap JS and the sidecar route controller;
- reconnect/read-state continuity lives inside the selected-wave widget;
- viewport-fragment policy is now available in the selected-wave path, but root-runtime ownership still does not coordinate or publish that live-state continuity;
- root-shell chrome has a live-status surface, but no root-runtime controller publishes live state into it.

That is why parity docs still describe the live runtime as controller-local and sidecar-oriented. `#968` is the slice that turns those seams into one root-scoped live surface.

## 2. Acceptance Criteria

`#968` is complete when all of the following are true:

- The J2CL root shell has a root-scoped live-runtime coordinator that owns:
  - bootstrap/session state,
  - route/history continuity,
  - selected-wave continuity,
  - reconnect state,
  - read-state/live-state publication,
  - fragment-policy inputs for selected-wave opens.
- `/?view=j2cl-root` preserves query + selected-wave deep links across:
  - initial load,
  - back/forward,
  - reconnect,
  - reload,
  - sign-in/sign-out return-target navigation.
- Reconnect no longer behaves as an internal selected-wave widget concern only:
  - the root shell surfaces reconnect state in its status strip,
  - the selected wave remains logically selected during reconnect,
  - the last known read state survives transient failures,
  - successful recovery resets the reconnect budget.
- The root live surface consumes the existing `#967`/`#993` viewport-hint and fragment-window path without changing server protocol or duplicating selected-wave fragment growth behavior.
- `#931` unread/read modeling is consumed as part of the root live surface rather than treated as an isolated selected-wave label feature.
- The shell consumes live-state visuals through existing Lit/root-shell seams only:
  - `shell-status-strip` for reconnect and root-runtime status,
  - existing read/unread surfaces for unread state.
- Existing compose handoff via `J2clSidecarWriteSession` remains intact and atomic-version behavior from `#936` is not regressed.
- No new rollout flag is introduced by default:
  - `j2cl-root-bootstrap` remains the only rollout/coexistence seam unless implementation proves a narrower live-surface sub-flag is strictly necessary.
- No change is made to:
  - default `/` root routing,
  - StageThree compose parity (`#969`),
  - Lit read-surface parity (`#966`),
  - fragment-window modeling itself (`#967`),
  - server-first read HTML (`#965`),
  - the GWT rollback seam.

## 3. Scope And Non-Goals

### 3.1 In scope

- Root-runtime ownership of bootstrap/session lifecycle inside the J2CL root shell.
- Root-runtime ownership of route/history continuity for the current query + selected-wave deep-link model.
- Publishing reconnect/live-state into the root shell status surface.
- Wiring the existing unread/read-state path from `#931` into the root live surface contract.
- Consuming the existing J2CL viewport-hint and fragment-window path when coordinating selected-wave opens.
- Targeted J2CL/server tests and root-shell browser verification.

### 3.2 Explicit non-goals

- No StageOne read-surface reimplementation or visual redesign from `#966`.
- No fragment-window modeling, visible-region rendering, or viewport metadata hardening rework from `#967` / `#993`.
- No StageThree compose/editor parity from `#969`.
- No attempt to aggregate ephemeral compose/search socket lifecycles into the root reconnect-status strip in `#968`; root reconnect publication is limited to the selected-wave live surface plus bootstrap refresh continuity.
- No new server protocol beyond exposing existing `ProtocolOpenRequest` viewport fields already present in generated models.
- No default-root cutover.
- No rollback/coexistence change in `WaveClientServlet`.
- No redesign of shell primitives or new Stitch work; `#964` and the design packet already own that.

## 4. Dependency Readiness

### 4.1 Merged prerequisites

As of 2026-04-24 in this worktree after `git fetch origin main`:

- `#933` — closed
- `#931` — closed
- `#963` — closed
- `#964` — closed
- `#936` — closed
- `#965` — merged into `origin/main`
- `#966` — merged into `origin/main`
- `#967` — merged into `origin/main`
- `#993` — merged into `origin/main`

These merged issues mean the auth, unread/read modeling, bootstrap JSON, root-shell chrome, server-first selected-wave host, StageOne read surface, viewport transport, fragment-window growth, and atomic write-basis seams are already available.

### 4.2 Dependency readiness call

As of 2026-04-24:

- There are no dependency blockers for starting `#968` implementation on `issue-968-root-live-surface`.
- The lane must consume, not recreate, the now-merged work from `#965`, `#966`, `#967`, and `#993`.
- The first implementation slice is intentionally narrower than the full plan: refresh this plan, then add the root-scoped live-surface model/controller and root-shell status publication wiring only.
- Keep compose/write path changes, default-root routing, server protocol changes, and StageThree parity out of this first slice.
- Before future larger slices, re-diff root-shell host and selected-wave viewport seams against `origin/main` because adjacent lanes continue to merge into the same surfaces.

Implementation verdict: `#968` is dependency-ready for the first narrow implementation slice.

## 5. Slice Packet For #968

**Title:** Promote the J2CL sidecar into a root-shell live surface with route/history/reconnect/read-state integration
**Stage:** live
**Dependencies:** `#933`, `#931`, `#963`, `#965`, `#966`, `#967`, `#993`

### Matrix rows claimed

- `R-4.1` — socket/session lifecycle
- `R-4.3` — reconnect
- `R-4.5` — route/history integration
- `R-4.7` — feature activation and live-update application

### Matrix rows shared / coordinated

- `R-4.4` — read/unread state live updates
  - modeling was introduced by `#931`
  - `#968` owns root-live-surface wiring and continuity consumption
- `R-4.6` — fragment fetch policy
  - transport/window modeling belongs to `#967`
  - `#968` owns the live-runtime side that consumes the resulting viewport state
- `R-7.3` — server clamp behavior
  - server behavior already exists
  - `#968` owns client-side visibility/telemetry surfacing once `#967` lands

### Existing seams consumed

- Bootstrap JSON: `J2clBootstrapServlet` + `J2clBootstrapContract`
- Root-shell mount: `HtmlRenderer.renderJ2clRootShellPage(...)` + `WaveSandboxEntryPoint.mount(..., "root-shell")`
- Root-shell live status surface: `shell-status-strip`
- Route/history persistence: `J2clSidecarRouteController`
- Selected-wave reconnect/read-state: `J2clSelectedWaveController`
- Server viewport-hint support: `WaveClientRpcImpl` + `WaveClientRpcViewportHintsTest`
- J2CL viewport-hint transport and fragment growth: `SidecarViewportHints`, `SidecarOpenRequest`, `SidecarTransportCodec`, `J2clSelectedWaveViewportState`, `J2clSelectedWaveController.fetchFragments(...)`

### Rollout / rollback seam

- Route: `/?view=j2cl-root`
- Flag/coexistence seam: `j2cl-root-bootstrap`
- Default `/` stays on legacy GWT unless the existing feature flag says otherwise
- `#968` must not alter the rollback contract

## 6. Exact Files Likely To Change

### New J2CL files

- `j2cl/src/main/java/org/waveprotocol/box/j2cl/root/J2clRootLiveSurfaceController.java`
  - Root-scoped live-runtime owner for bootstrap/session state, route continuity, reconnect status, selected-wave continuity, and root status publication.
  - It does not replace `J2clRootShellController` as the composition root; it owns lifecycle wiring and cross-controller coordination after the composition root instantiates the child views/controllers.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/root/J2clRootLiveSurfaceModel.java`
  - Immutable root-runtime snapshot published to shell/status surfaces.
- `j2cl/src/test/java/org/waveprotocol/box/j2cl/root/J2clRootLiveSurfaceControllerTest.java`
  - Focused tests for root-runtime lifecycle, stale-bootstrap drops, and reconnect/route continuity.

### Modified J2CL files

- `j2cl/src/main/java/org/waveprotocol/box/j2cl/root/J2clRootShellController.java`
  - Becomes a thin composition root that delegates live-runtime ownership to `J2clRootLiveSurfaceController`.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/root/J2clRootShellView.java`
  - Expose/set root live-status content through the existing shell status surface, not only the return-target label.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSidecarRouteController.java`
  - Preserve current URL contract, but promote root-shell route/history behavior from "sidecar route" to the root live surface's authoritative route seam.
  - No class rename is planned in `#968`; the issue should prefer behavioral ownership changes over naming churn.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSidecarRouteState.java`
  - Expand only if additional root-runtime route metadata is strictly needed after `#966/#967` land.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSidecarRouteCodec.java`
  - Remains the URL codec; may need narrow extensions for root-runtime continuity only.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSidecarComposeController.java`
  - Narrow bootstrap/session wiring change only if the root coordinator becomes the single bootstrap owner. Compose/write-session semantics remain unchanged.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveController.java`
  - Shift from widget-local lifecycle owner to selected-wave/live-channel controller under the root runtime.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveModel.java`
  - Preserve last-known read/reconnect continuity under root-runtime ownership.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveProjector.java`
  - Continue to consume read-state and fragment data, but do not own root-runtime continuity decisions.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSearchGateway.java`
  - Future shared bootstrap/session access if a later slice makes the root coordinator the single bootstrap owner. Do not change transport semantics in the first slice.

### Modified tests

- `j2cl/src/test/java/org/waveprotocol/box/j2cl/search/J2clSidecarRouteControllerTest.java`
- `j2cl/src/test/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveControllerTest.java`
- `j2cl/src/test/java/org/waveprotocol/box/j2cl/search/J2clSearchGatewayAuthFrameTest.java`
- `j2cl/src/test/java/org/waveprotocol/box/j2cl/transport/SidecarTransportCodecTest.java`
  - Inspect-only for the first slice unless root-live wiring reveals a transport regression. Viewport transport assertions already exist after `#967` / `#993`.
- `wave/src/jakarta-test/java/org/waveprotocol/box/server/rpc/J2clBootstrapServletTest.java`
  - Existing bootstrap JSON regression coverage; verify the signed-out contract still degrades cleanly once the root runtime becomes the single bootstrap owner.

### Changelog / rollout traceability

- `wave/config/changelog.d/2026-04-23-root-live-surface.json`
  - Required when implementation lands because `#968` changes user-visible root-shell live behavior.

### Inspect-only references

- `wave/src/main/java/org/waveprotocol/box/server/frontend/WaveClientRpcImpl.java`
  - Existing server-side viewport-hint + clamp logic; not expected to change for `#968`.
- `wave/src/test/java/org/waveprotocol/box/server/frontend/WaveClientRpcViewportHintsTest.java`
  - Existing server regression coverage for viewport hints/clamps.
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/J2clBootstrapServlet.java`
  - Existing bootstrap/session/shell metadata seam.
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java`
  - Existing root-shell bootstrap JS and history hook seam.
- `j2cl/lit/src/elements/shell-status-strip.js`
  - Existing root live-region/status surface.
- `j2cl/lit/src/elements/shell-main-region.js`
  - Existing shell primitive; `#968` should keep using the current slot/mount contract rather than introducing a new Lit surface.

## 7. Task Breakdown

### Task 1: Freeze the dependency gate and root-runtime boundary

- [ ] Keep `#968` scoped to the root-runtime/state-machine layer only.
- [ ] Treat `#965`, `#966`, `#967`, and `#993` as merged prerequisites to consume, not work to subsume into `#968`.
- [ ] For the first worker slice, touch only root live-surface model/controller wiring, root-shell status publication, focused J2CL tests, and any required changelog fragment.
- [ ] Keep compose/write path, default root routing, server protocol, and StageThree parity out of the first worker slice.
- [ ] Record in the issue comment:
  - worktree path,
  - branch,
  - plan path,
  - closed prerequisites,
  - merged dependency refresh note for `#965`, `#966`, `#967`, and `#993`,
  - first-slice scope.

### Task 2: Introduce a root-scoped live-runtime coordinator

- [ ] Add `J2clRootLiveSurfaceController` under `org.waveprotocol.box.j2cl.root`.
- [ ] Keep `J2clRootShellController` as the composition root that instantiates the child views/controllers.
- [ ] Move root-runtime lifecycle ownership out of `J2clRootShellController.start()` and into the new coordinator.
- [ ] The new coordinator should own:
  - current bootstrap snapshot,
  - current route state,
  - current selected-wave id,
  - reconnect status,
  - root status-strip publication,
  - cross-controller coordination after the composition root has instantiated the child controllers.
- [ ] `J2clRootShellController` should become a thin adapter:
  - construct `J2clRootShellView`,
  - construct the child search/selected-wave/compose views,
  - delegate to `J2clRootLiveSurfaceController.start()`.
- [ ] Add `J2clRootLiveSurfaceControllerTest` to lock:
  - stale bootstrap results are ignored after route reselection,
  - reconnect preserves the selected-wave id,
  - root status survives transient disconnects,
  - root-shell startup still mounts through the existing workflow host,
  - signed-out bootstrap responses leave the root shell stable and keep auth return-target links intact.

### Task 3: Make route/history authority explicitly root-scoped

- [ ] Keep `J2clSidecarRouteController` as the current URL codec/controller seam rather than inventing a second router.
- [ ] Re-scope its usage so the root live coordinator, not individual child widgets, owns the current route state.
- [ ] Preserve the existing root-shell URL contract:
  - `?view=j2cl-root&q=...&wave=...`
  - legacy hash deep-link normalization still happens once during server bootstrap
  - post-mount updates come from the J2CL route controller
- [ ] Explicit legacy-hash handoff contract:
  - the inline server bootstrap JS may do the one-time `replaceState(...)` normalization before J2CL mounts,
  - `J2clRootLiveSurfaceController.start()` must treat `location.search` as authoritative when it already contains `?view=j2cl-root...` and `location.hash` no longer carries a legacy wave token,
  - the coordinator must not re-run hash normalization or write a duplicate history entry on initial mount if the server bootstrap already normalized the URL.
- [ ] Keep `routeUrlObserver -> J2clRootShellView.syncReturnTarget(...)` as the steady-state same-origin auth-link/return-target seam.
- [ ] Extend route tests to cover:
  - root-shell start after server hash normalization,
  - the server bootstrap JS one-time normalization does not lead the coordinator to emit a second boot-time history mutation,
  - back/forward after reconnect,
  - no duplicate push on internal metadata/read-state refresh,
  - deep-link restore after a reconnect-driven bootstrap refresh.

### Task 4: Promote bootstrap/session lifecycle from controller-local to root-scoped

- [ ] Stop treating bootstrap fetch as something each controller reacquires independently.
- [ ] The root live coordinator should fetch `/bootstrap.json` at root-shell start and publish the current `SidecarSessionBootstrap` to child flows.
- [ ] Only refresh bootstrap when required:
  - initial boot,
  - reconnect after a transport failure,
  - explicit root-runtime invalidation.
- [ ] Preserve current signed-out behavior from `J2clBootstrapServlet` and root-shell auth links.
- [ ] If compose needs the shared bootstrap snapshot, make that a narrow `J2clSidecarComposeController` wiring change only:
  - inject the root-owned bootstrap snapshot or supplier,
  - keep compose/write-session behavior otherwise unchanged,
  - do not widen this issue into StageThree compose work.
- [ ] Extend tests to prove:
  - search, selected-wave open, and compose can share one stable bootstrap snapshot,
  - stale bootstrap responses do not overwrite a newer route selection,
  - reconnect refreshes bootstrap only when needed,
  - signed-out `/bootstrap.json` responses keep the root shell stable instead of throwing the runtime into a dead-end error state,
  - stale bootstrap responses delivered through the compose bootstrap supplier path are dropped under the same route-generation guard as search/selected-wave paths.
- [ ] Reconnect-driven bootstrap refresh failure mode:
  - if `/bootstrap.json` returns a transient 5xx or network failure during reconnect,
  - keep the last selected wave + route state intact,
  - surface the failure in the root status strip,
  - log dropped/stale bootstrap callbacks with the `[j2cl-root-live]` prefix,
  - continue using the existing bounded reconnect/backoff path rather than clearing selection or pushing a new route.
- [ ] Session-expired reconnect branch:
  - if `/bootstrap.json` comes back signed out / auth-required during reconnect,
  - keep the current route/return target intact,
  - surface a session-expired message in the root status strip,
  - preserve working sign-in return-target links instead of pretending the live surface recovered.
- [ ] Use one explicit stale-bootstrap guard in the root coordinator:
  - a monotonic `routeGeneration` (or equivalently named) counter increments on every route selection / bootstrap-invalidating transition,
  - every async bootstrap callback carries the generation it was issued under,
  - callbacks whose generation no longer matches are dropped.
- [ ] Lock that guard with a dedicated `J2clRootLiveSurfaceControllerTest` case rather than leaving the stale-drop behavior as prose only.

### Task 5: Consume the merged viewport-hint and fragment-window path

- [ ] Treat `SidecarViewportHints`, `SidecarOpenRequest` viewport fields, `SidecarTransportCodec` fields `5/6/7`, `J2clSearchGateway.openSelectedWave(... SidecarViewportHints ...)`, `fetchFragments(...)`, `J2clSelectedWaveController` viewport growth, and `J2clSelectedWaveViewportState` as existing seams.
- [ ] Do not alter the server protocol or transport codec in the first worker slice.
- [ ] Do not invent a second root-level fragment-window model; root-live coordination should observe or preserve selected-wave state through existing selected-wave controller seams.
- [ ] If a later `#968` slice needs to route viewport status into shell chrome, add it as a root-status/model projection only after proving the selected-wave controller remains the single owner of fragment growth.

### Task 6: Re-scope selected-wave reconnect/read-state behavior under the root live surface

- [ ] Keep `J2clSelectedWaveController` as the selected-wave stream/read-state coordinator, but make it a child of the new root live controller rather than the top-level owner.
- [ ] `J2clSelectedWaveController` should continue to own its existing socket-local behavior from `#931`:
  - reconnect backoff scheduling,
  - reconnect budget counting and reset on successful update,
  - read-state debounce,
  - visibility-refresh fetches,
  - stale-callback / stale-generation drops,
  - `J2clSidecarWriteSession` derivation.
- [ ] The root live controller should own only the cross-controller continuity decisions:
  - selected wave stays selected during reconnect,
  - last known read state is preserved through transient failures,
  - reconnect status is published to the root shell,
  - successful recovery is reflected in root status text based on the child controller's published reconnect state,
  - route state remains authoritative through reconnect/reload.
- [ ] Ownership boundary rule:
  - `J2clSelectedWaveController` remains the authoritative owner of reconnect budget/counter state,
  - `J2clRootLiveSurfaceController` may only read and publish that state,
  - the root coordinator must not increment, reset, or shadow a second reconnect counter.
- [ ] Preserve `J2clSidecarWriteSession` handoff to compose unchanged aside from any narrow bootstrap-supplier wiring from Task 4.
- [ ] Extend `J2clSelectedWaveControllerTest` to prove:
  - reconnect preserves root-level selected-wave continuity,
  - stale reopen/update callbacks are ignored after route change,
  - root-runtime status does not regress when read-state fetches fail softly,
  - visibility refresh still closes the UDW-only-change gap from `#931`,
  - reconnect/bootstrap refresh preserves the current write-session `historyHash` / base-version continuity required by `#936`,
  - compose-path submit wiring still sees the preserved write-session continuity after the root-owned bootstrap supplier path is introduced.
- [ ] Extend `J2clRootLiveSurfaceControllerTest` to prove the reconnect-counter single-owner rule:
  - the child `J2clSelectedWaveController` remains the only mutator of reconnect-budget state,
  - the root coordinator only reads/publishes that state and never increments/resets a shadow counter.
- [ ] Keep `J2clSelectedWaveModel` / `J2clSelectedWaveProjector` changes concrete and narrow:
  - only the fields/projection needed to preserve current read-state + write-session continuity under the root-runtime owner,
  - no new presentation semantics beyond what `#931` already introduced.

### Task 7: Publish live state into the existing root shell chrome

- [ ] Extend `J2clRootShellView` so it can render root live status into the existing `shell-status-strip` seam.
- [ ] Publish:
  - reconnect in progress / reconnected,
  - current return target,
  - selected-wave continuity summary when relevant.
- [ ] Reuse design-packet visual vocabulary only:
  - `shell-status-strip` from `#964`
  - `live-reconnect-banner` / unread-read indicator concepts from `docs/j2cl-lit-design-packet.md` heading `### 5.3 Live-Surface Visual Indicators`
- [ ] Existing unread/read state continues to render through the current selected-wave J2CL surface (`J2clSelectedWaveView`) unless `#966`/`#967` have already moved that rendering seam.
- [ ] Do not introduce a new Lit custom element for `#968` by default; prefer the existing `shell-status-strip` slot/light-DOM path.
- [ ] If that proves insufficient, pause and update the plan + issue before adding a new Lit element.
- [ ] Do not move behavioral ownership into Lit. Lit remains slot/render-only.
- [ ] Accessibility contract for reconnect/state publication:
  - `shell-status-strip` already renders with `aria-live=\"polite\"`,
  - `#968` should keep that politeness level unless the plan/issue are explicitly updated.
- [ ] Browser verification must confirm:
  - reconnect is announced in the status strip/live region,
  - focus is not stolen by reconnect messaging,
  - auth links keep the correct return target after back/forward.

### Task 8: Preserve feature activation and StageTwo parity seams

- [ ] Add root-runtime assertions or status hooks that confirm the active live surface still has:
  - search controller,
  - selected-wave controller,
  - compose controller,
  - read-state refresh path.
- [ ] Keep `#936` atomic write-basis behavior intact; no reconnect or route refactor may drop `historyHash`/base-version continuity.
- [ ] Observability decision for `#968`:
  - no new standalone telemetry channel by default,
  - reuse the existing shell status-strip UI seam for user-visible state,
  - reuse existing client logging/console seams from the root bootstrap and selected-wave controller for debugging,
  - use a consistent root-runtime console/log prefix such as `[j2cl-root-live]` for lifecycle events that browser verification may need to inspect,
  - if a reviewer requires explicit counters, update the plan/issue before widening scope.
- [ ] Search-panel retries/loading remain request-local; the root status strip in `#968` does not aggregate search-request retry state.
- [ ] Record the exact verification commands and browser steps in the issue and later PR.
- [ ] Before implementation starts, diff the merged `#965` host-surface files against this worktree and record whether the root-shell host contract moved:
  - `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java`
  - `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/WaveClientServlet.java`
  - `j2cl/lit/src/elements/shell-main-region.js`
- [ ] Confirm the merged fragment-window transport seam still maps to generated `ProtocolOpenRequest` viewport slots (`5/6/7`) before any future caller-state wiring changes.

## 8. Verification Commands

Run these from `/Users/vega/devroot/worktrees/issue-968-root-live-surface` once implementation starts:

### Targeted J2CL/server test gates

```bash
sbt -batch j2clSearchBuild j2clSearchTest
j2cl/mvnw -f j2cl/pom.xml -Psearch-sidecar -Dtest=org.waveprotocol.box.j2cl.root.J2clRootLiveSurfaceControllerTest,org.waveprotocol.box.j2cl.root.J2clRootShellViewTest test
sbt -batch "testOnly org.waveprotocol.box.j2cl.search.J2clSidecarRouteControllerTest org.waveprotocol.box.j2cl.search.J2clSelectedWaveControllerTest org.waveprotocol.box.j2cl.search.J2clSearchGatewayAuthFrameTest org.waveprotocol.box.j2cl.transport.SidecarTransportCodecTest"
sbt -batch "testOnly org.waveprotocol.box.server.frontend.WaveClientRpcViewportHintsTest org.waveprotocol.box.server.rpc.J2clBootstrapServletTest"
```

### Local root-shell browser verification

```bash
bash scripts/worktree-boot.sh --port 9968
PORT=9968 bash scripts/wave-smoke.sh start
PORT=9968 bash scripts/wave-smoke.sh check
```

Required browser path:

- Open `http://localhost:9968/?view=j2cl-root` from the worktree root after running the commands above.
- Open the same route while signed out and confirm the signed-out `/bootstrap.json` contract still produces a stable shell with working auth return-target links.
- Complete one signed-out -> sign-in round trip and verify the return target lands back on the same root-shell route/deep link, including after a reconnect-triggering route restore during the session.
- Open a deep link with both query and wave:
  - `http://localhost:9968/?view=j2cl-root&q=in%3Ainbox&wave=example.com%2Fw%2Babc`
- Open a legacy-hash deep link that the server bootstrap JS currently normalizes, then verify the J2CL route controller remains authoritative after mount:
  - `http://localhost:9968/?view=j2cl-root#/example.com/w+abc`
- With a selected wave open, background the tab long enough to miss any live update, modify/read the wave from another tab, then return and verify the visibility-refresh path still updates unread/read state without a route reset.
- While signed in after that round trip, force a transient reconnect and verify unread/read continuity survives the reconnect-driven bootstrap refresh without changing the return target.
- Verify:
  - selected wave stays selected across reload
  - back/forward preserve query + selected wave
  - reconnect messaging appears in the shell status strip and clears on recovery
  - reconnect/session-expired messaging stays `aria-live=\"polite\"` and does not steal focus
  - unread/read state survives reconnect and visibility refresh
  - fragment-window opens use the `#967` path rather than regressing to eager whole-wave behavior, but only after `#967` is merged into the lane and Task 5b is enabled
  - browser logs with the `[j2cl-root-live]` prefix tell a coherent start -> route -> reconnect -> recover story during debugging

Shutdown:

```bash
PORT=9968 bash scripts/wave-smoke.sh stop
```

## 9. Review And Traceability Gates

- [ ] Run Claude Opus 4.7 review on this plan before implementation starts.
- [ ] Implementation may start after this dependency refresh because `#965`, `#966`, `#967`, and `#993` are present on `origin/main` in this worktree.
- [ ] After implementation, run Claude Opus 4.7 review again on the implementation diff.
- [ ] Feature-flag decision for implementation:
  - default plan is to reuse only the existing `j2cl-root-bootstrap` seam
  - if a narrower `#968`-specific live-surface flag becomes necessary, pause and update the issue + plan before adding it
- [ ] Add a changelog fragment at `wave/config/changelog.d/2026-04-23-root-live-surface.json` when implementation lands.
- [ ] Record on issue `#968`:
  - worktree path,
  - branch,
  - plan path,
  - seam findings,
  - dependency-readiness note,
  - plan-review result,
  - commit SHA(s),
  - verification commands/results when implementation eventually happens.

## 10. Dependency-Readiness Verdict

Planner verdict refreshed for 2026-04-24:

- `#968` has the required merged foundation to start the first narrow implementation slice.
- `#968` should consume the merged #965/#966/#967/#993 seams and avoid duplicate viewport transport, read-surface, or server-first host work.
- The first worker slice is root-scoped live-surface model/controller plus root-shell status publication wiring and focused J2CL tests.
- The listed verification commands are current in this worktree: `build.sbt` defines both `j2clSearchBuild` and `j2clSearchTest`.
