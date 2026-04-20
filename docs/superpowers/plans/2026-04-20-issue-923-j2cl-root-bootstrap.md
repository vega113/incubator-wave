# Issue #923 J2CL Root Bootstrap Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a reversible, server-controlled bootstrap seam so plain `/` can intentionally boot the J2CL-owned root shell while legacy GWT remains the default fallback.

**Architecture:** Build on the post-`#928/#937` baseline already on `main`: the J2CL root shell HTML exists, `WaveClientServlet` is already the root decision point, and `FeatureFlagService` can evaluate global and per-user rollout state. `#923` should make the server-side feature flag the authoritative bootstrap control plane for `/`, keep `/?view=j2cl-root` as a direct diagnostic route, and let `getSessionJson()` mirror enabled flags only for visibility/debugging.

**Tech Stack:** Java, Jakarta servlets, SBT, existing server feature-flag store/service, `HtmlRenderer`, `WaveClientServlet`, J2CL sidecar/root-shell assets under `j2cl/`, worktree boot/smoke scripts, and local browser verification.

---

## Problem Framing

The J2CL root shell is already in place from the `#928/#937` work on `main`, but the bootstrap control plane is still missing. Right now, the request path can either show legacy GWT or an explicit J2CL shell route; `#923` needs a server-owned switch that decides which root shell HTML to render before the client loads.

That switch must be reversible without deploying code, must work for signed-out traffic, and must not depend on client flags as the primary control. Client-flag plumbing happens after HTML render, so it is the wrong authority for the root bootstrap decision.

The intended control plane is:

- a known feature flag named `j2cl-root-bootstrap`, default `false`
- `FeatureFlagService.isEnabled("j2cl-root-bootstrap", participantId)` as the server-side decision point
- `participantId == null` as the signed-out/global path
- `WaveClientServlet#doGet` as the actual place where `/` chooses GWT or J2CL root shell HTML
- `getSessionJson()` as a secondary mirror of enabled flags for page/debug visibility only

`/?view=j2cl-root` should remain available as a direct diagnostic path from `#928`, but it should not be the primary rollout mechanism.

## Exact Seams / Files

### Primary server-control-plane files

- Modify: `wave/src/main/java/org/waveprotocol/box/server/persistence/KnownFeatureFlags.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/server/persistence/FeatureFlagSeeder.java`
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/ServerMain.java`
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/WaveClientServlet.java`

### Root-shell rendering and visibility

- Inspect first, modify only if a tiny helper is needed: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java`
- Inspect first, likely no change needed: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/FeatureFlagServlet.java`

### Tests

- Modify or add: `wave/src/test/java/org/waveprotocol/box/server/persistence/memory/FeatureFlagSeederJ2clBootstrapTest.java`
- Modify or add: `wave/src/jakarta-test/java/org/waveprotocol/box/server/rpc/WaveClientServletJ2clBootstrapTest.java`
- Extend if needed: `wave/src/jakarta-test/java/org/waveprotocol/box/server/rpc/WaveClientServletJ2clRootShellTest.java`

### Release note

- Add: `wave/config/changelog.d/2026-04-20-j2cl-root-bootstrap.json`
- Regenerate only: `wave/config/changelog.json`

### Committed verification matrix artifact

- Modify: `docs/runbooks/j2cl-sidecar-testing.md`

## Minimal Acceptance Slices

### Slice 1: Server-known flag exists and defaults off

- Add `j2cl-root-bootstrap` to `KnownFeatureFlags` so it appears in admin flag listings and cannot be deleted as an unknown free-form toggle.
- Seed the flag from server config or startup seeding code so the default is reproducibly `false`.
- Keep `FeatureFlagService` as the evaluation layer; do not add client-flag plumbing as the authoritative switch.

### Slice 2: `/` chooses the shell from the server flag

- `WaveClientServlet#doGet` should render legacy GWT for `/` when `j2cl-root-bootstrap` is off.
- The same method should render the J2CL root shell for `/` when `j2cl-root-bootstrap` is on.
- Preserve `/?view=j2cl-root` as a direct root-shell diagnostic route from `#928`, but do not make it the rollout control plane.
- `getSessionJson()` may continue to include enabled feature names for visibility; that is secondary to the HTML decision.

### Slice 3: Signed-out bootstrap is supported

- Global evaluation via `FeatureFlagService.isEnabled("j2cl-root-bootstrap", null)` must work.
- When the global flag is on, unsigned requests to `/` should intentionally receive the J2CL root shell HTML before login.
- Signed-out proof must stay deterministic and not rely on a client-side flag after page load.

### Slice 4: Rollback is immediate and boring

- Turning the flag off should restore the legacy GWT root on `/` without a code rollback.
- The J2CL shell route from `#928` remains available as a direct diagnostic path, but the production bootstrap decision for `/` returns to GWT when the flag is disabled.
- If flag refresh lags, the plan should use `refreshCache()` or a restart path in verification rather than assuming the first request proves the new state.

## Implementation Tasks

### Task 1: Add the server-side bootstrap flag

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/box/server/persistence/KnownFeatureFlags.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/server/persistence/FeatureFlagSeeder.java`
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/ServerMain.java`

- [ ] Register `j2cl-root-bootstrap` in the known-flag list with a concise rollout description and a default `false` state.
- [ ] Add a config-seeded startup path for the new flag so local dev and staged environments can flip the root bootstrap without editing client code.
- [ ] Make the seeding path preserve admin overrides if the flag already exists in the store.
- [ ] Ensure the startup path refreshes `FeatureFlagService` after seeding so the root decision sees the new value immediately.

### Task 2: Route plain `/` from the server flag

**Files:**
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/WaveClientServlet.java`
- Inspect first, modify only if a tiny helper is needed: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java`

- [ ] Add an explicit server-side branch for `/` that checks `FeatureFlagService.isEnabled("j2cl-root-bootstrap", participantId)`.
- [ ] Keep the existing `/?view=j2cl-root` direct path working as a diagnostic escape hatch for local verification.
- [ ] Preserve the legacy landing-page behavior for unauthenticated users when the flag is off and the request is not explicitly selecting the J2CL root shell.
- [ ] Leave client flags as a visibility mirror only; do not use them to decide which shell HTML gets rendered.
- [ ] Keep the J2CL root shell page HTML itself anchored in the already-committed `HtmlRenderer.renderJ2clRootShellPage(...)` baseline rather than reworking the shell layout.

### Task 3: Cover the control plane with focused tests

**Files:**
- Modify or add: `wave/src/test/java/org/waveprotocol/box/server/persistence/memory/FeatureFlagSeederJ2clBootstrapTest.java`
- Modify or add: `wave/src/jakarta-test/java/org/waveprotocol/box/server/rpc/WaveClientServletJ2clBootstrapTest.java`
- Extend if needed: `wave/src/jakarta-test/java/org/waveprotocol/box/server/rpc/WaveClientServletJ2clRootShellTest.java`

- [ ] Add a test that proves `j2cl-root-bootstrap` is discoverable through the known-flag registry and defaults to off.
- [ ] Add a test that proves the seeder path turns the flag on from config without breaking admin-owned persisted values.
- [ ] Add a test matrix for `WaveClientServlet` that covers legacy `/` with the flag off, `/` with the flag on, signed-out `/` with the global flag on, and `/?view=j2cl-root` as the direct diagnostic path.
- [ ] Add a test that proves the root-shell HTML does not regress the existing `/#928` marker behavior while the server-side decision changes.

### Task 4: Record the rollout contract

**Files:**
- Add: `wave/config/changelog.d/2026-04-20-j2cl-root-bootstrap.json`
- Regenerate only: `wave/config/changelog.json`
- Modify: `docs/runbooks/j2cl-sidecar-testing.md`

- [ ] Add a changelog fragment that says the J2CL root shell is now selectable by a server-side feature flag while legacy GWT remains the default fallback.
- [ ] Keep the changelog focused on the bootstrap control plane; do not describe the later client migration work.
- [ ] Add a committed dual-bootstrap verification matrix section to `docs/runbooks/j2cl-sidecar-testing.md` (or an adjacent J2CL runbook if implementation proves that file is the wrong home) so the bootstrap-off vs bootstrap-on checks are no longer only in issue text or the plan file.

## Verification Matrix

### Mode A: Legacy GWT root remains the default

Run from `/Users/vega/devroot/worktrees/issue-923-j2cl-root-bootstrap`:

```bash
sbt -batch "testOnly org.waveprotocol.box.server.persistence.memory.FeatureFlagSeederJ2clBootstrapTest org.waveprotocol.box.server.rpc.WaveClientServletJ2clBootstrapTest"
sbt -batch compileGwt Universal/stage
bash scripts/worktree-boot.sh --port 9914
PORT=9914 bash scripts/wave-smoke.sh start
curl -fsS http://localhost:9914/ | grep -F 'webclient/webclient.nocache.js'
curl -fsS http://localhost:9914/?view=j2cl-root | grep -F 'data-j2cl-root-shell'
PORT=9914 bash scripts/wave-smoke.sh stop
```

Expected result:

- `/` still serves the legacy GWT root HTML
- the explicit `/?view=j2cl-root` diagnostic route still serves the J2CL root shell
- `compileGwt` and `Universal/stage` stay green

### Mode B: J2CL root bootstrap is enabled server-side

Run from `/Users/vega/devroot/worktrees/issue-923-j2cl-root-bootstrap`:

```bash
cp wave/config/application.conf /tmp/issue-923-j2cl-root-bootstrap.application.conf
printf '\nui.j2cl_root_bootstrap_enabled=true\n' >> /tmp/issue-923-j2cl-root-bootstrap.application.conf
JAVA_OPTS='-Dwave.server.config=/tmp/issue-923-j2cl-root-bootstrap.application.conf' bash scripts/worktree-boot.sh --port 9914
PORT=9914 bash scripts/wave-smoke.sh start
curl -fsS http://localhost:9914/ | grep -F 'data-j2cl-root-shell'
curl -fsS http://localhost:9914/?view=j2cl-root | grep -F 'data-j2cl-root-shell'
PORT=9914 bash scripts/wave-smoke.sh stop
```

Expected result:

- plain `/` now serves the J2CL root shell because the server flag is on
- the J2CL shell still works through the direct diagnostic route
- toggling the config back to false restores the legacy GWT root without a code rollback

## Rollback / Fallback

- The rollback path is to disable `j2cl-root-bootstrap` in the feature-flag store or config seed, then refresh the cache or restart the server.
- If `FeatureFlagService` caching makes the switch look sticky, the plan should treat that as a refresh problem, not a new bootstrap branch.
- Keep `/?view=j2cl-root` as a diagnostic route, but do not widen it into the authoritative rollout knob.
- Client-flag mirrors in `getSessionJson()` are informational only; they must never become the bootstrap source of truth.

## Risks

- A per-user-only rollout would fail the signed-out bootstrap proof, so the plan must support the global `participantId == null` path.
- If `WaveClientServlet` keeps consulting client flags for the root decision, the bootstrap control plane will be in the wrong layer.
- If the known flag is not added to `KnownFeatureFlags`, the admin UI and delete guardrails will not treat it as a first-class rollout toggle.
- If the seeding path is omitted, local verification will be hard to reproduce and rollback will require manual DB edits.
- If the root-shell diagnostic route is accidentally coupled to the rollout flag, debugging the baseline from `#928` becomes harder, not easier.

## Self-Check

- Coverage check: every acceptance slice maps to at least one task.
- Scope check: this plan changes only the server bootstrap control plane and its tests; it does not reopen the J2CL root shell implementation from `#928/#937`.
- Fallback check: disabling the flag returns `/` to legacy GWT without code rollback.
- Verification check: both bootstrap modes have explicit commands, and both are testable in the worktree.
