# Issue #928 J2CL Root Shell Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first J2CL-owned root app shell on `/` so later bootstrap work can switch between a real GWT shell and a real J2CL shell, while the existing GWT root remains the default bootstrap in this slice.

**Architecture:** Start from the post-`#922` J2CL search/read/write sidecar baseline and add one explicit non-default root-route seam in `WaveClientServlet` that can render a server-owned J2CL root-shell page instead of the legacy GWT page. Keep the J2CL production assets under the existing `/j2cl/**` static path, add minimal server-rendered session/login/logout chrome for the root shell, and mount the current J2CL workflow controllers inside a dedicated root-shell controller rather than remounting the sidecar page wholesale.

**Tech Stack:** Java, SBT, Jakarta servlet layer, `HtmlRenderer`, `WaveClientServlet`, J2CL Maven sidecar under `j2cl/`, Elemental2 DOM, existing `J2clSearchPanelController` + `J2clSelectedWaveController` + `J2clSidecarComposeController`, `scripts/worktree-file-store.sh`, `scripts/worktree-boot.sh`, and manual browser verification against the local staged app.

---

## 1. Goal / Baseline / Root Cause

This plan is explicitly for the reviewed post-`#922` baseline represented in this worktree:

- the J2CL sidecar already supports search, selected-wave read flow, route state, and the first plain-text write path under `/j2cl-search/index.html`
- the production J2CL bundle already stages under `war/j2cl/**` through `j2clProductionBuild`
- the real `/` route is still owned by `WaveClientServlet` and `HtmlRenderer.renderWaveClientPage(...)`

Issue `#928` exists because the repo can currently choose only between:

- the real legacy GWT root shell on `/`
- an incomplete J2CL sidecar page on `/j2cl-search/index.html`

That is not enough for later cutover work. The next slice must create a real J2CL-owned root shell so the later bootstrap selector (`#923`) can choose between two real shells rather than between GWT and a sidecar proof.

Observed seams in the current tree:

- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/WaveClientServlet.java:123-200`
  - `/` always renders either the public landing page or the legacy GWT page; there is no J2CL root-shell selector yet.
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java:2454-2888`
  - the current signed-in root page is hard-wired to the GWT bootstrap script `webclient/webclient.nocache.js`.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/sandbox/SandboxEntryPoint.java:37-86`
  - the current J2CL mount path supports the search-sidecar flow only; there is no dedicated root-shell mode.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSearchPanelView.java:27-131`
  - the current UI is still a sidecar-specific search card and split layout, not a root-level app shell with navigation chrome and mount points.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSidecarComposeView.java:21-154`
  - create/reply controls already exist and can be reused once the root shell hosts the current workflow.
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java:4042-4118`
  - the legacy top bar already defines menu/logout semantics, but it is coupled to the GWT root page and includes many GWT-specific indicators that do not belong in the first J2CL shell.
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/SignOutServlet.java:52-87`
  - logout and safe local redirect behavior already exist and should be reused rather than reinvented.

The narrow root cause is therefore:

- there is no request-level seam on `/` to render a J2CL root shell intentionally
- there is no J2CL root shell with minimum signed-in/signed-out chrome
- the existing J2CL read/write workflow is still packaged as a sidecar page rather than as content hosted inside a root shell

## 2. Acceptance Criteria

`#928` is complete when all of the following are true:

- `/` still renders the legacy GWT app by default for signed-in users.
- There is an explicit non-default seam on `/` that intentionally renders a J2CL root shell instead of the GWT shell.
- That explicit seam is request-local only for this slice. It is not yet the persistent feature-flag/bootstrap selector from `#923`.
- The J2CL root shell is a real root shell, not just `/j2cl-search/index.html` remounted or iframe-wrapped.
- When `/?view=j2cl-root` is requested without a session, the behavior is deterministic and test-covered:
  - render a signed-out J2CL root shell with a login entry seam
  - preserve the explicit J2CL root return target through sign-in/sign-out redirects
- The J2CL root shell provides:
  - minimum navigation chrome
  - a session banner or signed-in identity summary
  - a user-menu/logout seam
  - route mount points for the current workflow host
  - a signed-out login entry seam
- The current J2CL read/write workflow can run inside the J2CL root shell without depending on the legacy GWT shell chrome.
- The required cross-path build gate passes:
  - `sbt -batch j2clSearchBuild j2clSearchTest compileGwt Universal/stage`
- Local boot plus browser verification show both:
  - default `/` still boots GWT
  - the explicit J2CL root-shell seam on `/` mounts intentionally and hosts the current workflow successfully

## 3. Scope And Non-Goals

### In Scope

- add one explicit non-default selector on `/` for the J2CL root shell
- render a signed-in J2CL root-shell page on `/` when that selector is requested
- render a signed-out J2CL root-shell page or login-entry state on `/` when that selector is requested without a session
- build minimal J2CL root chrome around the existing search/selected-wave/write workflow
- keep the current J2CL workflow logic reusable rather than rewriting it
- keep current `/j2cl-search/**` and `/j2cl/**` verification paths working
- add focused tests for the root selector, shell rendering, and J2CL mount mode

### Explicit Non-Goals

- no default bootstrap change on `/`; GWT remains default
- no persistent feature flag, user preference, or config-driven root selector; that belongs to `#923`
- no retirement of the legacy GWT root shell; that belongs to `#924` and `#925`
- no full parity port of the GWT top bar, save-status widgets, connection indicators, or admin badge logic
- no redesign of the current J2CL search/read/write workflow internals unless the shell boundary requires a narrow adapter
- no new J2CL editor parity, rich-text actions, attachment flow, or broader conversation navigation beyond hosting the existing `#922` workflow
- no new static route family like `/j2cl-root/**` unless implementation proves the existing `/j2cl/**` production assets cannot support the root shell cleanly

## 4. Recommended Non-Default `/` Seam

Use a request-only selector on `/` for this issue. Recommended contract:

- `/?view=j2cl-root`

Why this is the narrowest correct seam for `#928`:

- it keeps the selector local to the request and avoids stepping on `#923`
- it fits the existing `view=landing` pattern in `WaveClientServlet`
- it is explicit enough for local browser verification and issue evidence
- it allows signed-in and signed-out paths to be verified on the real `/` route without changing the default bootstrap
- the server-rendered J2CL root-shell page exposes one shell-unique marker (recommended: `data-j2cl-root-shell`) so route checks and browser verification can distinguish it from the legacy root deterministically

Guardrails:

- do not add database-backed feature flag behavior here
- do not widen `view=` into a broader routing system for the whole app
- if `view=j2cl-root` is absent, preserve current behavior exactly
- if `view=j2cl-root` is present, preserve safe login/logout redirects by returning to `/?view=j2cl-root`

## 5. Exact Files Likely To Change

### Server Root-Seam Files

- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/WaveClientServlet.java`
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java`

### Primary J2CL Root-Shell Files

- `j2cl/src/main/java/org/waveprotocol/box/j2cl/sandbox/SandboxEntryPoint.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSearchPanelView.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveView.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSidecarComposeView.java`
- `j2cl/src/main/webapp/assets/sidecar.css`

### Recommended New J2CL Root-Shell Files

These exact names are recommended to keep the shell separate from the existing sidecar workflow classes:

- `j2cl/src/main/java/org/waveprotocol/box/j2cl/root/J2clRootShellController.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/root/J2clRootShellView.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/root/J2clRootShellRoute.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/root/J2clRootShellSession.java`

### Likely Test Files

- `j2cl/src/test/java/org/waveprotocol/box/j2cl/sandbox/SandboxBuildSmokeTest.java`
- `j2cl/src/test/java/org/waveprotocol/box/j2cl/sandbox/SandboxHostPageAssetPathTest.java`
- `wave/src/jakarta-test/java/org/waveprotocol/box/server/rpc/WaveClientServletFragmentDefaultsTest.java`
- `wave/src/jakarta-test/java/org/waveprotocol/box/server/rpc/WaveClientServletJ2clRootShellTest.java`
- `wave/src/test/java/org/waveprotocol/box/server/rpc/HtmlRendererTopBarTest.java`
- `wave/src/test/java/org/waveprotocol/box/server/rpc/HtmlRendererAuthStateTest.java`

### Recommended New Tests

- `j2cl/src/test/java/org/waveprotocol/box/j2cl/root/J2clRootShellControllerTest.java`
- `j2cl/src/test/java/org/waveprotocol/box/j2cl/root/J2clRootShellViewTest.java`
- `wave/src/test/java/org/waveprotocol/box/server/rpc/HtmlRendererJ2clRootShellTest.java`

### Expected Changelog Fragment

- `wave/config/changelog.d/2026-04-20-j2cl-root-shell.json`

### Inspect-Only References

- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/SignOutServlet.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSearchPanelController.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSidecarRouteController.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSidecarComposeController.java`
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/ServerRpcProvider.java`

`ServerRpcProvider.java` should remain inspect-only unless the implementation proves the root shell needs a new static asset mount beyond the existing `/j2cl/**` production path.

## 6. Implementation Order

### Task 1: Freeze The Baseline And Keep `#928` Narrow

- [ ] Start from the reviewed post-`#922` baseline. If `#922` is not merged into the lane when implementation begins, stack `#928` on top of that reviewed lane first.
- [ ] Pin the explicit root seam to one request-only selector on `/`.
- [ ] Keep the output of this issue to “real J2CL shell exists and can be selected intentionally,” not “root bootstrap now configurable by flag.”
- [ ] Treat the current `/j2cl-search/index.html` workflow as the implementation payload to be hosted inside the new shell, not replaced.

### Task 2: Add The Server-Side `/` Selector And Root-Shell HTML

- [ ] Add a selector branch in `WaveClientServlet.doGet(...)` that recognizes the explicit J2CL root-shell request on `/`.
- [ ] Define precedence explicitly when multiple `view=` values collide on `/`:
  - recommended rule: `view=j2cl-root` wins over `view=landing` for this issue
  - add a unit test for that exact collision rather than leaving it to servlet parameter iteration order
- [ ] Preserve current behavior for:
  - signed-out default `/`
  - signed-in default `/`
  - `?view=landing`
- [ ] Add a dedicated `HtmlRenderer` method for the J2CL root-shell page rather than mutating `renderWaveClientPage(...)` into a dual-mode tangle.
- [ ] Render two root-shell variants through the same renderer:
  - signed-in shell with session banner/menu/logout seam
  - signed-out shell with login entry seam
- [ ] Make the signed-out seam explicit: `/?view=j2cl-root` should render the signed-out J2CL shell with a visible sign-in entry, not silently fall back to the public landing page and not auto-redirect before the user can confirm the root-shell selection worked.
- [ ] Keep redirects/login links explicit:
  - signed-in logout should target `/auth/signout?r=/%3Fview%3Dj2cl-root` or equivalent safe local redirect
  - signed-out sign-in entry should target `/auth/signin?r=/%3Fview%3Dj2cl-root`
- [ ] Anchor redirect-safety checks to the existing `SignOutServlet` local-redirect contract rather than inventing a second safety rule for the same return target.
- [ ] Preserve relevant route query on the explicit root-shell seam:
  - if `q=` and `wave=` are present with `view=j2cl-root`, pass them through to the hosted workflow mount rather than dropping them at the root boundary

### Task 3: Build A Real J2CL Root Shell Instead Of Reusing The Sidecar Card

- [ ] Add a dedicated root-shell controller/view pair in `j2cl/.../root/`.
- [ ] Define the first root-shell structure as:
  - navigation chrome or brand header
  - session banner/menu mount
  - route mount host for the current workflow
  - signed-out login-entry host
- [ ] Keep the J2CL root shell intentionally minimal:
  - brand
  - current user or signed-out state
  - menu/logout or login seam
  - workflow mount host
- [ ] Do not port GWT-only save-state, network-status, or admin indicator behavior into this slice.

### Task 4: Mount The Existing J2CL Workflow Inside The Root Shell

- [ ] Add a dedicated `root-shell` mount mode in `SandboxEntryPoint`.
- [ ] Reuse the current controllers for:
  - search
  - selected-wave rendering
  - compose/reply
  - route state
- [ ] Move sidecar-specific copy and CSS that assumes “isolated proof card” into a shell-aware layout so the same workflow can render inside:
  - `/j2cl-search/index.html`
  - the new root-shell mount host on `/?view=j2cl-root`
- [ ] Keep current workflow logic shared; if adapters are needed, keep them thin and shell-specific.

### Task 5: Define Minimal Root-Shell Route Mount Points

- [ ] Add one root-shell routing concept that can host the current workflow without widening into a full client router.
- [ ] Recommended first route contract:
  - `search` workflow mount as the default shell content when signed in
  - `signed-out` or `auth-entry` state when unauthenticated
- [ ] Keep the existing sidecar route state (`q + wave`) intact inside the hosted workflow.
- [ ] Do not redesign route persistence in this issue. The goal is root-shell hosting, not a second routing system.

### Task 6: Preserve Sidecar And Production Asset Paths

- [ ] Continue serving the root-shell JS/CSS from the existing `/j2cl/**` production assets produced by `j2clProductionBuild`.
- [ ] Use absolute asset URLs in the server-rendered root-shell page so `/` does not depend on relative `./assets/...` resolution.
- [ ] Keep `/j2cl-search/index.html` intact for sidecar verification and regression isolation.
- [ ] Only change `j2cl/src/main/webapp/index.html` if the implementation intentionally shares bootstrap helpers between the static sidecar host page and the server-rendered root shell.

### Task 7: Add Focused Tests And Manual Proof

- [ ] Add servlet tests proving:
  - default `/` remains GWT
  - `/?view=j2cl-root` selects the J2CL root shell
  - `view=j2cl-root` wins deterministically over `view=landing` on `/`
  - signed-out J2CL root mode exposes a login entry seam
  - sign-in/sign-out redirects preserve the explicit J2CL root selector
- [ ] Add redirect-safety coverage proving the encoded local return target for `view=j2cl-root` is accepted as local and is not mangled or widened into an external redirect.
- [ ] Add pass-through coverage proving `q=` and `wave=` survive the `/?view=j2cl-root` boundary and reach the hosted workflow mount on first render.
- [ ] Add HTML renderer tests proving:
  - the J2CL root-shell page does not include `webclient/webclient.nocache.js`
  - it does include the expected J2CL production asset URLs
  - it includes the minimum menu/logout or login-entry chrome
  - it includes the shell-unique marker used by route presence checks
- [ ] Add J2CL smoke tests proving:
  - `SandboxEntryPoint` recognizes `root-shell`
  - the current workflow can still mount in sidecar mode
  - shell-mode rendering does not regress the existing search-sidecar behavior

## 7. Exact Verification Commands

Run these from `/Users/vega/devroot/worktrees/issue-928-j2cl-root-shell`.

### Worktree File-Store Prep

```bash
cd /Users/vega/devroot/worktrees/issue-928-j2cl-root-shell
scripts/worktree-file-store.sh --source /Users/vega/devroot/incubator-wave
```

Expected result:

- `wave/_accounts`, `wave/_attachments`, and `wave/_deltas` are available in the issue worktree
- local browser verification can use realistic signed-in data and real waves

### Main Cross-Path Build Gate

```bash
sbt -batch test jakartaTest:test j2clSearchBuild j2clSearchTest compileGwt Universal/stage
```

Expected result:

- the new root-shell servlet/renderer unit tests pass under `test` and `jakartaTest:test`
- the current J2CL sidecar build/tests still pass
- any new root-shell tests pass
- the legacy GWT root still compiles and stages green

### Local Boot / Smoke

```bash
bash scripts/worktree-boot.sh --port 9914
```

Then run the exact printed helper commands, typically:

```bash
PORT=9914 JAVA_OPTS='...' bash scripts/wave-smoke.sh start
PORT=9914 bash scripts/wave-smoke.sh check
```

Keep the server running for the route and browser checks below.

### Route Presence Checks

```bash
curl -fsS -I http://localhost:9914/
curl -fsS -I "http://localhost:9914/?view=j2cl-root"
curl -fsS -I http://localhost:9914/j2cl-search/index.html
curl -fsS -I http://localhost:9914/j2cl/index.html
curl -fsS http://localhost:9914/ | grep -F "webclient/webclient.nocache.js"
curl -fsS "http://localhost:9914/?view=j2cl-root" | grep -F 'data-j2cl-root-shell'
curl -fsS "http://localhost:9914/?view=j2cl-root&q=in%3Ainbox&wave=local.net%2Fw%2BseedA" | grep -F 'data-j2cl-root-shell'
```

Expected result:

- `/` still serves the legacy root HTML
- `/?view=j2cl-root` serves the J2CL root-shell page instead of the GWT bootstrap page
- `/j2cl-search/index.html` still works for the existing sidecar verification route
- `/j2cl/index.html` still exists as the production-profile asset host
- the J2CL root-shell page exposes the shell-unique `data-j2cl-root-shell` marker
- if `q=` or `wave=` are supplied alongside `view=j2cl-root`, the root shell preserves them for the hosted workflow mount rather than stripping them at the server boundary

### Manual Browser Verification

Use one signed-in browser session and one signed-out or fresh session.

Before the signed-out round-trip, register or prepare one fresh local test user in the worktree-backed file-store so the sign-in return path can be completed end-to-end instead of stopping at the login page.

#### Signed-In Checks

- Open `http://localhost:9914/`
- Confirm the legacy GWT root still boots by default
- Open `http://localhost:9914/?view=j2cl-root`
- Confirm the J2CL root shell mounts intentionally
- Confirm the shell shows:
  - brand/navigation chrome
  - signed-in identity/banner
  - menu/logout seam
  - current J2CL search/open/reply workflow hosted inside the shell
- Exercise the existing J2CL workflow inside the shell:
  - query inbox/search
  - open a selected wave
  - create or reply with the existing plain-text write path
- Confirm this happens without GWT shell chrome

#### Signed-Out Checks

- Open `http://localhost:9914/?view=j2cl-root` in a signed-out or fresh browser session
- Confirm the J2CL root shell shows a login entry seam instead of falling straight back to the GWT landing behavior
- Confirm the sign-in link preserves the explicit J2CL root return target
- Complete sign-in and confirm the browser returns to the J2CL root shell rather than dropping back to the default GWT root

### Shutdown

```bash
PORT=9914 bash scripts/wave-smoke.sh stop
```

## 8. Risks / Edge Cases

- **Bootstrap-boundary drift:** It is easy to let the request selector in `WaveClientServlet` become the real opt-in bootstrap flag. Do not do that in `#928`; keep it request-only so `#923` still has a clear job.
- **Shell/workflow coupling:** Reusing the current sidecar workflow is required, but if shell-specific layout logic leaks into the search/write controllers, the sidecar route may regress. Keep the adaptation at the shell/view boundary.
- **Signed-out behavior drift:** Current default `/` for unauthenticated users is the landing page. The explicit J2CL root seam must not accidentally change the default signed-out behavior for ordinary `/`.
- **Redirect correctness:** Login and logout links must preserve the explicit J2CL root return target without creating unsafe redirects.
- **Asset-path confusion:** The root shell runs on `/`, but J2CL production assets live under `/j2cl/**`. The server-rendered page must use absolute asset URLs and must not depend on sidecar-relative `./...` paths.
- **Queue overlap with `#923`:** If `#928` adds feature-flag persistence or default selection logic, it will erase the boundary to `#923` and make the queue harder to reason about.

## 9. Ordering Relative To The Remaining Queue

Recommended dependency order from the current migration queue:

1. `#922` must be merged or stacked first because `#928` is supposed to host the current read/write workflow, not recreate it.
2. `#928` builds the real J2CL root shell with an explicit request-only seam on `/`.
3. `#923` should then replace or wrap that request-only seam with the real opt-in root bootstrap selector or feature flag.
4. `#924` should switch the default root bootstrap only after `#928` and `#923` both exist and dual-boot verification is documented.
5. `#925` should retire the legacy GWT root only after `#924` is proven stable.

Practical implication:

- `#928` should not add a persistent bootstrap flag.
- `#923` should consume the shell produced here rather than building another shell.
- `#924` should not be considered ready until the explicit `#928` shell and the `#923` selector can both be verified locally on the same build.

## 10. Recommended Implementation Order

The narrowest correct implementation sequence is:

1. Add the request-only `/` selector and server-rendered J2CL root-shell HTML.
2. Add a dedicated J2CL `root-shell` mount mode and root-shell controller/view.
3. Rehost the existing search/open/reply workflow inside that shell with minimal adapters.
4. Add signed-out login-entry handling and signed-in logout/menu handling.
5. Run the required build gate plus browser proof showing default GWT `/` and intentional J2CL `/` both work.
