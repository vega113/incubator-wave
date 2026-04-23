# Issue #963 J2CL Bootstrap JSON And Shell Metadata Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the current J2CL bootstrap path that scrapes `/` HTML for `window.__session` / `window.__websocket_address` with an explicit, server-owned bootstrap JSON endpoint plus typed shell metadata, while preserving the existing coexistence/rollback seam and coordinating with the `#933` auth-hardening work.

**Architecture:** Add a new Jakarta servlet at `/bootstrap.json` that serves the existing `SessionConstants` payload (domain/address/id_seed/role/features) plus the websocket address and an explicit set of shell metadata fields (build commit, server build time, release id, route return target). Route the J2CL client (`SandboxEntryPoint`, `J2clSearchGateway`, `J2clRootShellController`) through a new typed loader that calls this endpoint instead of fetching `/` and regex-parsing HTML. Keep the inline `var __session` / `var __websocket_address` script block in the existing HTML renderers for one more release so rollback to an older J2CL build is possible, and keep the feature-flag bootstrap decision in `WaveClientServlet#doGet` untouched. Do **not** widen into `#933`: the new JSON endpoint stays authenticated via the same `HttpSession`/`SessionManager` contract that the existing HTML page already uses.

**Tech Stack:** Java, Jakarta servlets, SBT, the existing `SessionManager` / `AccountStore` / `FeatureFlagService` / `VersionServlet`, `WaveClientServlet`, `HtmlRenderer`, the J2CL sidecar/root-shell assets under `j2cl/`, `SidecarTransportCodec` JSON parsing helpers, and local browser/smoke verification.

---

## Problem Framing

The J2CL sidecar bootstraps itself today by issuing `GET /`, receiving the server-rendered root HTML, and then regex-scraping two inline globals out of it:

- `window.__session = {...};` – the `SessionConstants` payload (`domain`, `address`, `id`, `role`, `features`)
- `window.__websocket_address = "...";` – the presented WebSocket `host:port`

`SidecarSessionBootstrap.fromRootHtml` in `j2cl/src/main/java/org/waveprotocol/box/j2cl/transport/SidecarSessionBootstrap.java` does this scraping with a hand-rolled `String.indexOf` + `findMatchingBrace` parser. `J2clSearchGateway.fetchRootSessionBootstrap` and `SandboxEntryPoint.SidecarProofRunner.run` both call it on `GET /`.

This coupling breaks two pieces of downstream parity work:

1. Server-rendered first paint (`#965`) and the Lit root shell (`#964`) need the freedom to change the `/` HTML body – chrome, server-rendered selected wave, etc. – without breaking the J2CL bootstrap because something moved relative to the `__session` literal.
2. The existing scraping contract is inline-script-in-HTML, so every consumer has to re-implement the parser (the hand-rolled brace matcher already lives in `SidecarSessionBootstrap` *and* the existing test `SidecarTransportCodecTest#extractSessionBootstrapAddressFromRootHtml` enshrines the fragile format).

`#963` replaces that seam with an explicit server-owned bootstrap JSON endpoint and makes `SidecarSessionBootstrap` consume that JSON directly. It also formalises the shell-level metadata (build commit, release id, return target, websocket address) that the HTML currently exposes via `<meta>` tags and `data-*` attributes so that the J2CL client can read one typed document instead of reaching into the DOM.

`#963` is explicitly **not**:

- auth hardening (HttpOnly cookie work is `#933`)
- a change to the `j2cl-root-bootstrap` feature-flag rollout decision (`#923`)
- a change to the default `/` experience
- a JSON representation of any data the HTML page does not already expose to the client

## Coordination With `#933`

`#933` will later replace the `document.cookie` + `ProtocolAuthenticate` socket auth path with a signed bootstrap token or a server-owned handshake. To stay compatible with that work:

- The bootstrap JSON response schema reserves a top-level `socket` object. In `#963` it carries only `{ "address": "..." }`. `#933` can add a `socket.token` or `socket.authToken` field without a schema migration.
- The new endpoint serves the same origin the existing `/` page does and relies on the servlet's `HttpSession` for authn; it does **not** put session identifiers in the response body.
- The HTTP contract requires `Cache-Control: no-store` so a future `socket.token` never lives in a shared cache.

This keeps `#963` narrow while leaving room for `#933`.

## Exact Seams / Files

### New server-side endpoint

- Add: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/J2clBootstrapServlet.java`
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/ServerMain.java`
  - Register the new servlet at `/bootstrap.json` ahead of the existing `/` mapping.
- Inspect first, modify only if factoring is needed: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/WaveClientServlet.java`
  - The `getSessionJson(WebSession)` and `resolveWebsocketAddressForPage(HttpServletRequest)` helpers are the canonical sources for the session payload and websocket address; the new servlet must use the same code paths (extract into package-private helpers if needed, no logic change).

### Shared bootstrap contract

- Add: `wave/src/main/java/org/waveprotocol/box/common/J2clBootstrapContract.java`
  - A tiny class that pins the endpoint path (`"/bootstrap.json"`) and the JSON field names (`session`, `socket.address`, `shell.buildCommit`, `shell.serverBuildTime`, `shell.currentReleaseId`, `shell.routeReturnTarget`). Session field names stay aligned with `SessionConstants`. Used by the servlet, tests, and docs as the canonical server-owned contract; no behavior.

### J2CL client wiring

- Modify: `j2cl/src/main/java/org/waveprotocol/box/j2cl/transport/SidecarSessionBootstrap.java`
  - Add `fromBootstrapJson(String json)` that decodes via `SidecarTransportCodec.parseJsonObject`.
  - Keep the `fromRootHtml(String)` method in place for one release; mark it `@Deprecated` with a javadoc pointing to `fromBootstrapJson`. Remove all **call sites** (`J2clSearchGateway.fetchRootSessionBootstrap`, `SandboxEntryPoint.SidecarProofRunner.run`) inside this PR; the method itself is deleted in follow-up issue `#978` after the new contract has soaked.
- Modify: `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSearchGateway.java`
  - Change `fetchRootSessionBootstrap(...)` to `GET /bootstrap.json` and decode via `fromBootstrapJson`.
- Modify: `j2cl/src/main/java/org/waveprotocol/box/j2cl/sandbox/SandboxEntryPoint.java`
  - In `SidecarProofRunner.run()`, fetch `/bootstrap.json` instead of `/` and decode via `fromBootstrapJson`.
- Inspect first, touch only if test fixtures need it: `j2cl/src/main/java/org/waveprotocol/box/j2cl/root/J2clRootShellController.java`
  - The root-shell controller currently reuses `J2clSearchGateway`; once the gateway's fetch path is swapped, no controller change is required.

### Server HTML compatibility

- Inspect first, modify only if a factor-out helper avoids duplication: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java`
  - Keep the existing `var __session = ...; var __websocket_address = ...;` inline script block in both `renderWaveClientPage` and `renderJ2clRootShellPage` so an older cached J2CL build can still scrape the HTML during the overlap window. This plan does **not** remove that inline script; doing so is a follow-up after the new contract has soaked.

### Tests

- Add: `wave/src/jakarta-test/java/org/waveprotocol/box/server/rpc/J2clBootstrapServletIT.java` (or a plain unit test under `wave/src/test/...` if a Jakarta test harness is unnecessary)
  - Signed-in request returns `session.address`, `session.domain`, `session.role`, `session.features`, `socket.address`, `shell.buildCommit`, `shell.serverBuildTime`, `shell.currentReleaseId`.
  - Signed-out request returns the same shape with `session.address` absent but still HTTP 200 and `application/json`.
  - Response is `Cache-Control: no-store` and `Content-Type: application/json;charset=UTF-8`.
- Modify: `j2cl/src/test/java/org/waveprotocol/box/j2cl/transport/SidecarTransportCodecTest.java`
  - Replace / augment `extractSessionBootstrapAddressFromRootHtml` with a `fromBootstrapJson` variant that exercises the same fields.
  - Keep the `fromRootHtml` test only if the method stays for the deprecation window; otherwise delete the test in the same commit that deletes the method.
- Modify or extend: `wave/src/test/java/org/waveprotocol/box/server/rpc/WaveClientServletTest.java` or a peer JSON-endpoint test to confirm the session-JSON helper still emits the same fields for the HTML and JSON paths.

### Release note

- Add: `wave/config/changelog.d/2026-04-22-j2cl-bootstrap-json.json`
- Regenerate only: `wave/config/changelog.json` (via the existing assemble/validate workflow).

### Documentation

- Modify: `docs/runbooks/j2cl-sidecar-testing.md`
  - Add a short section describing how to hit `/bootstrap.json` manually and what the response schema is.
- Modify: `docs/j2cl-parity-issue-map.md`
  - Append the resolved `#963` status line in section 4.3 once the PR merges.

## Minimal Acceptance Slices

### Slice 1: Server-owned bootstrap JSON endpoint

- `GET /bootstrap.json` returns a well-formed JSON document whose shape is pinned by `J2clBootstrapContract`.
- Authenticated requests include `session.address` and `session.features`.
- Unauthenticated requests still return 200 with a session object that omits `address`/`features` but includes `domain`.
- The response always includes `socket.address` and the `shell.*` metadata (`buildCommit`, `serverBuildTime`, `currentReleaseId`, `routeReturnTarget`).
- The servlet reuses the same session-JSON and websocket-address code paths as `WaveClientServlet` so the two surfaces cannot drift on fields other than the per-request `session.id` seed.
- Response carries `Cache-Control: no-store`, `Pragma: no-cache`, `Vary: Cookie`, `X-Content-Type-Options: nosniff`, and `Content-Type: application/json;charset=UTF-8`.
- Non-GET methods return HTTP 405.

### Slice 2: J2CL client consumes the JSON

- `SidecarSessionBootstrap.fromBootstrapJson(String)` decodes the new endpoint and returns the existing `SidecarSessionBootstrap` value object.
- `J2clSearchGateway.fetchRootSessionBootstrap` fetches `/bootstrap.json` and uses `fromBootstrapJson`.
- `SandboxEntryPoint.SidecarProofRunner.run()` fetches `/bootstrap.json` and uses `fromBootstrapJson`.
- Runtime behavior (socket open, search call, selected-wave open) is unchanged.

### Slice 3: Coexistence / rollback preserved

- `HtmlRenderer.renderWaveClientPage` and `HtmlRenderer.renderJ2clRootShellPage` still emit `var __session = ...; var __websocket_address = ...;` so a previously deployed J2CL build that still scrapes HTML keeps working during rolling deploys.
- `WaveClientServlet#doGet` is untouched; the `j2cl-root-bootstrap` feature flag still chooses the root shell.
- Turning off the J2CL root shell via the existing feature flag still renders the legacy GWT root without code rollback.
- The new JSON endpoint is mounted regardless of the flag state: it is a data contract, not a rollout control.

### Slice 4: `#933` compatibility surface

- The JSON schema includes a `socket` object nested under the top-level payload, not a flat `websocketAddress` field.
- `SidecarSessionBootstrap.fromBootstrapJson` ignores unknown keys under `socket` so `#933` can add `socket.token` without a client change.
- The endpoint returns `Cache-Control: no-store`, `Pragma: no-cache`, `Vary: Cookie`, and `X-Content-Type-Options: nosniff` (Task 2 pins the headers).
- Only GET is supported; non-GET methods return 405.

## Implementation Tasks

### Task 1: Define the shared bootstrap contract

**Files:**
- Add: `wave/src/main/java/org/waveprotocol/box/common/J2clBootstrapContract.java`

- [ ] Pin the endpoint path constant (`"/bootstrap.json"`) and the JSON field names (`session`, `socket`, `shell`, `buildCommit`, `serverBuildTime`, `currentReleaseId`, `routeReturnTarget`, `features`).
- [ ] Do not put behavior in this class; it is a naming contract shared between servlet and tests.
- [ ] Keep it in `wave/src/main/java/org/waveprotocol/box/common` so it is visible to both Jakarta-overrides and J2CL tests.

### Task 2: Implement the bootstrap JSON servlet

**Files:**
- Add: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/J2clBootstrapServlet.java`
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/ServerMain.java`
- Inspect first, modify only if factoring is required: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/WaveClientServlet.java`

- [ ] Implement a `@Singleton` Jakarta servlet that builds the response from the same `SessionManager` / `AccountStore` / `FeatureFlagService` / `Config` inputs `WaveClientServlet` already consumes, plus `VersionServlet.getBuildCommit()` / `VersionServlet.getBuildTime()` / `VersionServlet.getCurrentReleaseId()` for the `shell.*` fields (same accessors `WaveClientServlet` calls in its constructor).
- [ ] Reuse `WaveClientServlet.getSessionJson` by extracting it to a package-private helper (or inject a `SessionJsonProvider`) rather than duplicating the role/feature lookup. Do not copy-paste the logic.
- [ ] Inject the already-resolved `websocketPresentedAddress` field the same way `WaveClientServlet` does (its `resolveWebsocketAddressForPage` is literally `return websocketPresentedAddress;`); no per-request work.
- [ ] Implement **GET only**; override `doPost` / `doPut` / `doDelete` to return HTTP 405 so the CSRF-irrelevance argument is explicit.
- [ ] Emit `Cache-Control: no-store`, `Pragma: no-cache`, `Content-Type: application/json;charset=UTF-8`, `Vary: Cookie` (defensive against intermediaries that ignore `no-store`), and `X-Content-Type-Options: nosniff`.
- [ ] Register the servlet in `ServerMain#addServlet("/bootstrap.json", J2clBootstrapServlet.class)` **before** the final `addServlet("/", WaveClientServlet.class)` mapping so the literal path wins over the root catch-all.
- [ ] Log only counts/failures; never log the session JSON body.

### Task 3: Add a JSON-aware bootstrap decoder on the J2CL client

**Files:**
- Modify: `j2cl/src/main/java/org/waveprotocol/box/j2cl/transport/SidecarSessionBootstrap.java`

- [ ] Add `public static SidecarSessionBootstrap fromBootstrapJson(String json)` that parses via `SidecarTransportCodec.parseJsonObject`.
- [ ] Require `session.address` when present; if the caller is the signed-out path, throw an `IllegalStateException` with a user-ready message so the caller can surface "please sign in".
- [ ] Require `socket.address` and fail with a descriptive message if it is missing or empty.
- [ ] Do not leak the raw JSON into the error message.
- [ ] Keep `fromRootHtml` for now, but javadoc it as deprecated with a pointer to `fromBootstrapJson`.

### Task 4: Switch the J2CL bootstrap call sites to the JSON endpoint

**Files:**
- Modify: `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSearchGateway.java`
- Modify: `j2cl/src/main/java/org/waveprotocol/box/j2cl/sandbox/SandboxEntryPoint.java`

- [ ] Use the `J2clBootstrapContract.PATH` constant (import the shared class) for the fetch URL.
- [ ] Decode with `fromBootstrapJson`.
- [ ] Preserve the existing error-surfacing contract: the existing `onError.accept(String)` pattern must still fire with a human-readable message on decode failure.
- [ ] Do not change `readCookie`/`JSESSIONID` behavior; that is `#933`'s scope.
- [ ] Do not change the socket open sequence except for the fetch source.

### Task 5: Update the fromRootHtml test coverage for the new decoder

**Files:**
- Modify: `j2cl/src/test/java/org/waveprotocol/box/j2cl/transport/SidecarTransportCodecTest.java`

- [ ] Add `extractSessionBootstrapFromBootstrapJson` that feeds a realistic JSON envelope through `fromBootstrapJson` and asserts address + websocket address.
- [ ] Add a "missing socket.address" negative test and a "missing session.address for signed-in payload" negative test.
- [ ] Keep the legacy `extractSessionBootstrapAddressFromRootHtml` test while `fromRootHtml` remains; remove it in the same PR only if the deprecated method is removed.

### Task 6: Cover the new servlet

**Files:**
- Add: `wave/src/jakarta-test/java/org/waveprotocol/box/server/rpc/J2clBootstrapServletIT.java` (preferred) or a unit peer under `wave/src/test/...` that mocks the Jakarta request/response.

- [ ] Signed-in request returns `session.address`, `session.domain`, `session.role`, `session.features`, `socket.address`, `shell.buildCommit`, `shell.serverBuildTime`, `shell.currentReleaseId`, `shell.routeReturnTarget`.
- [ ] Signed-out request returns HTTP 200, `application/json`, includes `session.domain` and `socket.address`, omits `session.address` and `session.features`.
- [ ] Response headers: `Cache-Control: no-store`, `Pragma: no-cache`, `Vary: Cookie`, `X-Content-Type-Options: nosniff`, `Content-Type: application/json;charset=UTF-8`.
- [ ] Non-GET methods (POST/PUT/DELETE) return HTTP 405 with no body.
- [ ] `session.features` never includes `j2cl-root-bootstrap`'s internal decision; it mirrors whatever `WaveClientServlet.getSessionJson` already emits for the flag list (no new leak surface).
- [ ] `session.id` is present but allowed to differ between this response and any concurrent HTML page load (`ID_SEED` is regenerated per call); that is by design.

### Task 7: Preserve HTML compatibility and rollback

**Files:**
- Inspect first, modify only if a factor-out helper avoids duplication: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java`

- [ ] Do not remove the existing `var __session = ...; var __websocket_address = ...;` inline script block.
- [ ] Do not rename existing `data-*` attributes on the J2CL root shell (`data-j2cl-root-shell`, `data-j2cl-root-return-target`).
- [ ] If a helper is needed so the servlet and the HTML renderer produce the same session JSON shape, factor it into a shared method rather than duplicating the logic. Note: `SessionConstants.ID_SEED` is regenerated per request today (`new RandomBase64Generator().next(10)` in `WaveClientServlet.getSessionJson`). This means the `session.id` returned by `/bootstrap.json` will differ from the `id` embedded in the HTML for the same browser load. This is by design (the ID seed has no cross-request meaning) and the existing J2CL bootstrap only consumes `address`/`domain`/`role`/`features`, so the divergence is behaviorally safe. Document this in the servlet's class-level javadoc so future maintainers do not "fix" the divergence by introducing a shared cache.

### Task 8: Record the rollout contract

**Files:**
- Add: `wave/config/changelog.d/2026-04-22-j2cl-bootstrap-json.json`
- Regenerate only: `wave/config/changelog.json`
- Modify: `docs/runbooks/j2cl-sidecar-testing.md`
- Modify: `docs/j2cl-parity-issue-map.md`

- [ ] Add a changelog fragment that says the J2CL sidecar/root shell now bootstraps from `/bootstrap.json` and no longer scrapes the root HTML page.
- [ ] Keep the changelog focused on the bootstrap contract; do not advertise `#933` work.
- [ ] Add a `/bootstrap.json` verification section to the sidecar testing runbook with a sample response and the exact `curl` commands.
- [ ] Annotate section 4.3 of `docs/j2cl-parity-issue-map.md` with the PR link once opened.

## Verification Matrix

All commands run from `/Users/vega/devroot/worktrees/issue-963-bootstrap-json`.

### Mode A: targeted unit/integration tests

```bash
sbt -batch "testOnly org.waveprotocol.box.j2cl.transport.SidecarTransportCodecTest"
sbt -batch "testOnly org.waveprotocol.box.server.rpc.J2clBootstrapServletIT"
sbt -batch "testOnly org.waveprotocol.box.server.rpc.WaveClientServletJ2clRootShellTest org.waveprotocol.box.server.rpc.WaveClientServletJ2clBootstrapTest"
sbt -batch j2clSandboxTest j2clSearchTest
```

Note: `WaveClientServletJ2clBootstrapTest` already exists under `wave/src/test/java/.../rpc/` as the existing feature-flag test. This PR only runs it (unchanged) to prove the existing flag rollout still works; it does not add new cases to it.

Expected:

- the new bootstrap-JSON decoder tests pass
- the servlet test matrix (signed-in, signed-out, cache headers) passes
- the existing J2CL root-bootstrap and root-shell tests are still green
- the J2CL sidecar and search modules still compile and pass their JVM-side tests

### Mode B: server smoke + bootstrap JSON contract

```bash
sbt -batch compileGwt Universal/stage
bash scripts/worktree-boot.sh --port 9914
PORT=9914 bash scripts/wave-smoke.sh start

# legacy GWT root still works
curl -fsS http://localhost:9914/ | grep -F 'webclient/webclient.nocache.js'

# bootstrap JSON endpoint shape
curl -fsS -H 'Accept: application/json' http://localhost:9914/bootstrap.json \
  | jq -e '.session.domain and .socket.address and .shell.buildCommit' > /dev/null

# response headers pin caching and content type
curl -fsSI http://localhost:9914/bootstrap.json | grep -Fi 'cache-control: no-store'
curl -fsSI http://localhost:9914/bootstrap.json | grep -Fi 'content-type: application/json'

PORT=9914 bash scripts/wave-smoke.sh stop
```

Expected:

- the legacy root path is unchanged
- `/bootstrap.json` returns a typed payload with the three top-level keys
- cache headers pin `no-store` and `application/json`

### Mode C: J2CL sidecar still boots end-to-end

Manual browser step (record outcome in the issue):

1. Start the worktree on port 9914.
2. Open `http://localhost:9914/j2cl-search/index.html`.
3. Confirm the search panel mounts without any `__session` bootstrap error in the browser console (a clean worktree may have zero digests; the assertion is on the bootstrap path, not on populated search results).
4. Open `http://localhost:9914/?view=j2cl-root`.
5. Confirm the root shell still renders and the sidecar mounts without console errors.

Expected:

- no `__session` scraping error
- no regression in search/selected-wave/compose flows
- no console warning about the HTML format change

## Rollback / Fallback

- Rollback is a revert of this PR. The contract is additive:
  - `/bootstrap.json` can stay mounted safely because it is read-only.
  - The existing inline `var __session = ...; var __websocket_address = ...;` block stays in the HTML so an older J2CL build that still calls `fromRootHtml` continues to work during a rolling deploy.
- If the new endpoint misbehaves in production, the immediate mitigation is to revert the four J2CL client files (`SidecarSessionBootstrap`, `J2clSearchGateway`, `SandboxEntryPoint`, plus the shared contract import). Server-side mitigation is to disable routing by removing the servlet registration, which has zero blast radius on other surfaces.
- The `#933` handoff point is the `socket` object in the response body; if `#933` lands first and wants a different shape, it can change that block without touching the JSON field names shipped in this PR.

## Risks

- **Schema drift between HTML and JSON.** If the new servlet duplicates session-JSON building, the two surfaces will diverge. Mitigation: reuse the same `getSessionJson(WebSession)` path via factoring, not duplication.
- **Cache poisoning.** If a proxy caches `/bootstrap.json` for a signed-out user and then serves it to a signed-in user, the response would hide per-user features. Mitigation: `Cache-Control: no-store`, plus explicit test coverage.
- **`#933` lock-in.** If the JSON shape commits to a flat `websocketAddress` at the top level, `#933` cannot add a socket-token without breaking clients. Mitigation: nest under `socket` from day one.
- **Removing the inline HTML globals too early.** If the inline `__session`/`__websocket_address` script is deleted in the same PR that adds the JSON endpoint, an older cached J2CL build will break on a rolling deploy. Mitigation: keep the inline block in both renderers for this PR; plan the removal as a follow-up.
- **Signed-out bootstrap.** The J2CL sidecar already tolerates a signed-out `__session` that lacks `address`. The new decoder must preserve this so the root shell can still display a signed-out chrome and log in the user.
- **Known-flag filtering.** `session.features` must continue to reflect the same feature flags `WaveClientServlet.getSessionJson` already publishes; adding more flags in the JSON body is out of scope for this PR.

## Self-Check

- Coverage check: every acceptance slice maps to at least one implementation task (Slice 1 → Task 2 + Task 6; Slice 2 → Tasks 3/4 + Task 5; Slice 3 → Task 7; Slice 4 → Tasks 1/2/3).
- Scope check: this plan adds one server-side JSON endpoint, rewires the J2CL bootstrap call sites, and does not touch auth/socket behavior, feature-flag rollout, or the default-root experience.
- Coexistence check: the inline HTML globals remain so a previously deployed J2CL build keeps working during rolling deploy.
- Fallback check: a revert of this PR leaves the system in its current scraping-based state with no data loss.
- `#933` check: the socket-auth surface stays unchanged; the JSON schema reserves a nested block for `#933` to extend.
- Verification check: Mode A covers unit/integration, Mode B covers server smoke + contract shape, Mode C covers the end-to-end J2CL boot.
