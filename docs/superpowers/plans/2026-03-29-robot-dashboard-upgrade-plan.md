# Robot Dashboard Upgrade Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents are available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Issue:** #476  
**Worktree:** `/Users/vega/devroot/worktrees/incubator-wave/robot-dashboard-upgrade`  
**Branch:** `feat/robot-dashboard-upgrade`  
**Base branch:** `origin/main`

**Goal:** Upgrade the existing owner-scoped robot dashboard and robot persistence/runtime seams without rewriting them so robots gain persisted description and timestamps, paused-state management, masked-secret-safe editing, owner-only destructive/update actions, delete support, and a multi-step-ish create/manage flow that preserves secrets on non-rotation edits.

**Architecture:** Reuse the current Jakarta `RobotDashboardServlet` as the primary UI/action surface, the existing `RobotRegistrar` / `RobotRegistrarImpl` seam for robot mutations, `RobotAccountData` + proto serialization for persisted robot metadata, `DataApiTokenServlet` for robot JWT issuance enforcement, and `RobotsGateway` for passive runtime enforcement. Extend those seams narrowly rather than introducing a new dashboard stack or alternate robot persistence path.

**Tech Stack:** Java 17, Jakarta servlet overrides, Wave account persistence (proto + file/memory/Mongo stores), server-rendered HTML, existing robot runtime/data API endpoints, JUnit 3/Mockito tests, sbt build/test commands.

---

## Acceptance Criteria

- [ ] The existing signed-in robot dashboard flow is upgraded in place rather than replaced with a new UI stack or route family.
- [ ] Robot persistence gains backward-compatible `description`, `created/updated` timestamps, and `paused` metadata with safe defaults for legacy records.
- [ ] The dashboard supports create, activate/update callback URL, edit description, rotate secret, pause/unpause, delete, masked secret preview, and timestamp display.
- [ ] Non-rotation edits preserve the existing robot secret; only explicit rotation changes it.
- [ ] Paused robots cannot obtain robot JWTs and cannot execute passive robot flows.
- [ ] All dashboard mutations remain owner-only and reject non-owner access.
- [ ] Targeted tests, compile verification, local server sanity verification, changelog updates, and review steps are all completed before PR creation.

## File Ownership And Likely Touched Files

### Persistence And Backward-Compatibility Slice
- Modify: `wave/src/proto/proto/org/waveprotocol/box/server/persistence/protos/account-store.proto`
- Modify: `wave/src/main/java/org/waveprotocol/box/server/account/RobotAccountData.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/server/account/RobotAccountDataImpl.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/server/persistence/protos/ProtoAccountDataSerializer.java`
- Test: `wave/src/test/java/org/waveprotocol/box/server/persistence/protos/ProtoAccountDataSerializerTest.java`
- Test: `wave/src/test/java/org/waveprotocol/box/server/persistence/AccountStoreTestBase.java`
- Inherit coverage via: `wave/src/test/java/org/waveprotocol/box/server/persistence/memory/AccountStoreTest.java`
- Inherit coverage via: `wave/src/test/java/org/waveprotocol/box/server/persistence/file/AccountStoreTest.java`
- Inherit coverage via: `wave/src/test/java/org/waveprotocol/box/server/persistence/mongodb/AccountStoreTest.java` if the Mongo-backed target remains runnable in the task lane

### Mutation, JWT, And Runtime Enforcement Slice
- Modify: `wave/src/main/java/org/waveprotocol/box/server/robots/register/RobotRegistrar.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/server/robots/register/RobotRegistrarImpl.java`
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/register/RobotRegistrar.java`
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/register/RobotRegistrarImpl.java`
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/dataapi/DataApiTokenServlet.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/server/robots/passive/RobotsGateway.java`
- Test: `wave/src/test/java/org/waveprotocol/box/server/robots/register/RobotRegistrarImplTest.java`
- Test: `wave/src/test/java/org/waveprotocol/box/server/robots/dataapi/DataApiTokenServletTest.java`
- Test: `wave/src/test/java/org/waveprotocol/box/server/robots/passive/RobotsGatewayTest.java`

### Dashboard Flow And Owner-Only Management Slice
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/RobotDashboardServlet.java`
- Optional modify if copy or helper seams require it: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/RobotRegistrationServlet.java`
- Test: `wave/src/test/java/org/waveprotocol/box/server/robots/RobotDashboardServletTest.java`
- Test: `wave/src/test/java/org/waveprotocol/box/server/rpc/HtmlRendererRobotRegistrationTest.java`

### Release Traceability Slice
- Modify: `wave/config/changelog.json`
- Modify: `wave/src/main/resources/config/changelog.json`
- Verify: `scripts/validate-changelog.py`

## Exact Targeted Test And Verification Commands

Run these focused commands during implementation, then rerun the aggregate set before review/PR:

- Baseline before coding starts:
  - `sbt "testOnly org.waveprotocol.box.server.persistence.protos.ProtoAccountDataSerializerTest"`
  - `sbt "testOnly org.waveprotocol.box.server.persistence.memory.AccountStoreTest org.waveprotocol.box.server.persistence.file.AccountStoreTest org.waveprotocol.box.server.persistence.mongodb.AccountStoreTest"`
  - `sbt "testOnly org.waveprotocol.box.server.robots.register.RobotRegistrarImplTest"`
  - `sbt "testOnly org.waveprotocol.box.server.robots.dataapi.DataApiTokenServletTest"`
  - `sbt "testOnly org.waveprotocol.box.server.robots.passive.RobotsGatewayTest"`
  - `sbt "testOnly org.waveprotocol.box.server.robots.RobotDashboardServletTest org.waveprotocol.box.server.rpc.HtmlRendererRobotRegistrationTest"`
  - `sbt wave/compile`
  - `sbt compileGwt`
- `sbt "testOnly org.waveprotocol.box.server.persistence.protos.ProtoAccountDataSerializerTest"`
- `sbt "testOnly org.waveprotocol.box.server.persistence.memory.AccountStoreTest org.waveprotocol.box.server.persistence.file.AccountStoreTest org.waveprotocol.box.server.persistence.mongodb.AccountStoreTest"`
- `sbt "testOnly org.waveprotocol.box.server.robots.register.RobotRegistrarImplTest"`
- `sbt "testOnly org.waveprotocol.box.server.robots.dataapi.DataApiTokenServletTest"`
- `sbt "testOnly org.waveprotocol.box.server.robots.passive.RobotsGatewayTest"`
- `sbt "testOnly org.waveprotocol.box.server.robots.RobotDashboardServletTest org.waveprotocol.box.server.rpc.HtmlRendererRobotRegistrationTest"`
- `sbt wave/compile`
- `sbt compileGwt`
- `python3 scripts/validate-changelog.py wave/config/changelog.json wave/src/main/resources/config/changelog.json`

## Local Verification Plan

- [ ] Run `sbt prepareServerConfig run` from the task worktree.
- [ ] Sign in as a real owner-scoped test user and open the existing robot dashboard route.
- [ ] Create a robot through the dashboard without forcing the full final callback URL up front, confirm the UI supports the staged create/manage flow, and record the secret shown at creation time.
- [ ] Confirm the dashboard displays a masked secret preview, description controls, created/updated timestamps, and owner-only action controls.
- [ ] Update description and callback URL without rotating the secret, then verify the secret preview remains tied to the original secret.
- [ ] Pause the robot, confirm pause state appears in the UI, and verify robot JWT issuance is rejected while paused.
- [ ] Exercise a narrow passive-runtime check while paused so the robot does not execute passive flow work, then unpause and confirm the flow resumes.
- [ ] Delete the robot and confirm it is removed from the owner dashboard.
- [ ] Record the exact command(s), route(s), and observed results in Beads before PR creation.

## Changelog Requirement

- [ ] Add one new top-of-file entry to both `wave/config/changelog.json` and `wave/src/main/resources/config/changelog.json`.
- [ ] Keep the new entry aligned between both files, newest first, with a stable `releaseId`, `date`, `title`, `summary`, and `sections` entries covering the robot dashboard upgrade.
- [ ] Run `python3 scripts/validate-changelog.py wave/config/changelog.json wave/src/main/resources/config/changelog.json` before final review.

## Explicit Out Of Scope

- [ ] No rewrite of the robot dashboard into GWT, React, or a new client framework.
- [ ] No replacement of the existing robot registrar or persistence stack with a new storage model.
- [ ] No broad redesign of unrelated account settings, topbar navigation, or non-robot Data API UX unless a tiny supporting copy/link update is unavoidable.
- [ ] No schema/data backfill job for legacy robots beyond safe read-time defaults for newly added fields.
- [ ] No unrelated cleanup in robot runtime, registration, or servlet code outside the seams needed for issue #476.

---

## Chunk 1: Persist Robot Metadata With Safe Legacy Defaults

### Task 1: Extend persisted robot metadata in the existing account model

**Why this chunk exists:** All dashboard/runtime changes depend on robot records carrying description, timestamps, and paused state in a backward-compatible way.

- [ ] **Step 1: Add failing persistence coverage first.**

Add tests that prove:
- robot account proto round-trips preserve `description`, `createdAt`, `updatedAt`, and `paused`
- legacy robot records that do not contain the new proto fields still deserialize safely
- store-level robot reads expose default values for legacy robots without throwing or mutating secrets
- the new robot metadata assertions live in `AccountStoreTestBase` so memory/file stores and the Mongo-backed store inherit the same coverage when that target is enabled

- [ ] **Step 2: Run the focused persistence targets and verify they fail.**

Run:
- `sbt "testOnly org.waveprotocol.box.server.persistence.protos.ProtoAccountDataSerializerTest"`
- `sbt "testOnly org.waveprotocol.box.server.persistence.memory.AccountStoreTest org.waveprotocol.box.server.persistence.file.AccountStoreTest org.waveprotocol.box.server.persistence.mongodb.AccountStoreTest"`

Expected: the new metadata/default assertions fail because the persisted robot model does not yet carry these fields.

- [ ] **Step 3: Extend the existing proto/model/serializer seam without changing its overall shape.**

Add optional persisted robot fields for description, created timestamp, updated timestamp, and paused state in the existing proto/model path. Keep defaults explicit and backward-compatible:
- description defaults to empty text
- timestamps default to `0` or another documented legacy-safe sentinel already used in this area
- paused defaults to `false`

Do not introduce a second robot metadata container or a separate persistence channel.

- [ ] **Step 4: Re-run the focused persistence targets.**

Run:
- `sbt "testOnly org.waveprotocol.box.server.persistence.protos.ProtoAccountDataSerializerTest"`
- `sbt "testOnly org.waveprotocol.box.server.persistence.memory.AccountStoreTest org.waveprotocol.box.server.persistence.file.AccountStoreTest org.waveprotocol.box.server.persistence.mongodb.AccountStoreTest"`

Expected: the new persistence and legacy-default coverage passes.

- [ ] **Step 5: Commit the persistence slice.**

```bash
git add docs/superpowers/plans/2026-03-29-robot-dashboard-upgrade-plan.md \
  wave/src/proto/proto/org/waveprotocol/box/server/persistence/protos/account-store.proto \
  wave/src/main/java/org/waveprotocol/box/server/account/RobotAccountData.java \
  wave/src/main/java/org/waveprotocol/box/server/account/RobotAccountDataImpl.java \
  wave/src/main/java/org/waveprotocol/box/server/persistence/protos/ProtoAccountDataSerializer.java \
  wave/src/test/java/org/waveprotocol/box/server/persistence/protos/ProtoAccountDataSerializerTest.java \
  wave/src/test/java/org/waveprotocol/box/server/persistence/AccountStoreTestBase.java

git commit -m "feat: persist robot dashboard metadata"
```

### Task 2: Thread timestamps and secret-preserving updates through the registrar seam

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/box/server/robots/register/RobotRegistrar.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/server/robots/register/RobotRegistrarImpl.java`
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/register/RobotRegistrar.java`
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/register/RobotRegistrarImpl.java`
- Test: `wave/src/test/java/org/waveprotocol/box/server/robots/register/RobotRegistrarImplTest.java`

- [ ] **Step 1: Add failing registrar tests for description/timestamp/secret preservation rules.**

Cover these cases:
- creating a dashboard-managed robot initializes created/updated timestamps and description
- editing description or callback URL updates `updatedAt` but preserves the existing secret
- explicit secret rotation changes only the secret and `updatedAt`
- pending or partially configured create-flow updates reuse the original secret rather than minting a new one

- [ ] **Step 2: Run the focused registrar target and verify it fails.**

Run: `sbt "testOnly org.waveprotocol.box.server.robots.register.RobotRegistrarImplTest"`

Expected: the new metadata or secret-preservation assertions fail before the registrar is extended.

- [ ] **Step 3: Extend the registrar with narrow update operations instead of ad-hoc dashboard mutation logic.**

Add or refine registrar methods for description edits, callback edits/activation, pause toggling, delete support plumbing, and timestamp maintenance so the dashboard servlet delegates all persistent mutations through the registrar seam. Preserve the existing secret on every non-rotation edit path.

- [ ] **Step 4: Re-run the focused registrar target.**

Run: `sbt "testOnly org.waveprotocol.box.server.robots.register.RobotRegistrarImplTest"`

Expected: the new registrar coverage passes.

- [ ] **Step 5: Commit the registrar metadata slice.**

```bash
git add wave/src/main/java/org/waveprotocol/box/server/robots/register/RobotRegistrar.java \
  wave/src/main/java/org/waveprotocol/box/server/robots/register/RobotRegistrarImpl.java \
  wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/register/RobotRegistrar.java \
  wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/register/RobotRegistrarImpl.java \
  wave/src/test/java/org/waveprotocol/box/server/robots/register/RobotRegistrarImplTest.java

git commit -m "feat: extend robot registrar for dashboard metadata"
```

## Chunk 2: Enforce Paused State In JWT And Passive Runtime Paths

### Task 3: Block paused robots from robot JWT issuance

**Files:**
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/dataapi/DataApiTokenServlet.java`
- Test: `wave/src/test/java/org/waveprotocol/box/server/robots/dataapi/DataApiTokenServletTest.java`

- [ ] **Step 1: Add failing JWT issuance tests for paused robots.**

Add coverage that a paused robot owned by the caller cannot receive a robot JWT until it is unpaused, while active robots continue to work.

- [ ] **Step 2: Run the focused token target and verify it fails.**

Run: `sbt "testOnly org.waveprotocol.box.server.robots.dataapi.DataApiTokenServletTest"`

Expected: paused robots are still treated like normal robots, so issuance is incorrectly allowed.

- [ ] **Step 3: Extend the existing token-validation seam.**

Update the current robot JWT issuance checks to require a non-paused robot in addition to the existing ownership/verification requirements. Keep user JWT behavior unchanged.

- [ ] **Step 4: Re-run the focused token target.**

Run: `sbt "testOnly org.waveprotocol.box.server.robots.dataapi.DataApiTokenServletTest"`

Expected: paused issuance is rejected and active issuance still passes.

### Task 4: Block paused robots from passive execution

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/box/server/robots/passive/RobotsGateway.java`
- Test: `wave/src/test/java/org/waveprotocol/box/server/robots/passive/RobotsGatewayTest.java`

- [ ] **Step 1: Add failing passive-runtime coverage.**

Add a test proving a paused robot present on a wavelet does not execute passive robot work, while an equivalent active robot still does.

- [ ] **Step 2: Run the focused passive-runtime target and verify it fails.**

Run: `sbt "testOnly org.waveprotocol.box.server.robots.passive.RobotsGatewayTest"`

Expected: passive processing still runs because paused state is not yet enforced.

- [ ] **Step 3: Reuse the current runtime gating seam.**

Add paused-state enforcement at the existing robot eligibility check in `RobotsGateway` instead of introducing a second passive-runtime policy layer.

- [ ] **Step 4: Re-run the focused passive-runtime target.**

Run: `sbt "testOnly org.waveprotocol.box.server.robots.passive.RobotsGatewayTest"`

Expected: paused robots are skipped and active robots still execute.

- [ ] **Step 5: Commit the runtime enforcement slice.**

```bash
git add wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/dataapi/DataApiTokenServlet.java \
  wave/src/main/java/org/waveprotocol/box/server/robots/passive/RobotsGateway.java \
  wave/src/test/java/org/waveprotocol/box/server/robots/dataapi/DataApiTokenServletTest.java \
  wave/src/test/java/org/waveprotocol/box/server/robots/passive/RobotsGatewayTest.java

git commit -m "feat: enforce paused robot restrictions"
```

## Chunk 3: Upgrade The Existing Dashboard Management Flow In Place

### Task 5: Add owner-only dashboard actions for description, pause, delete, and staged create/edit flow

**Files:**
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/RobotDashboardServlet.java`
- Optional modify if existing registration copy or helper reuse makes it the narrowest seam: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/RobotRegistrationServlet.java`
- Test: `wave/src/test/java/org/waveprotocol/box/server/robots/RobotDashboardServletTest.java`
- Test: `wave/src/test/java/org/waveprotocol/box/server/rpc/HtmlRendererRobotRegistrationTest.java`

- [ ] **Step 1: Add failing dashboard tests before changing servlet behavior.**

Cover these paths in `RobotDashboardServletTest`:
- owner can create a robot through the staged dashboard flow
- owner can edit description and callback URL separately
- owner can pause/unpause a robot
- owner can delete a robot
- non-owner attempts at update/pause/delete are rejected
- the new POST actions reject missing or invalid XSRF tokens
- the servlet preserves the existing secret on non-rotation edits
- the rendered page shows masked secret preview in the format `abcd…wxyz` (first 4 chars, ellipsis, last 4 chars) plus created/updated timestamps

If the multi-step-ish create flow depends on registration-copy updates outside the dashboard servlet, add the smallest matching failing assertion in `HtmlRendererRobotRegistrationTest`.

- [ ] **Step 2: Run the focused dashboard-related targets and verify they fail.**

Run:
- `sbt "testOnly org.waveprotocol.box.server.robots.RobotDashboardServletTest org.waveprotocol.box.server.rpc.HtmlRendererRobotRegistrationTest"`

Expected: the dashboard does not yet support the new staged flow, metadata rendering, or owner-only management actions.

- [ ] **Step 3: Extend the existing dashboard servlet instead of replacing it.**

Keep the current route and action structure, then add the narrowest new actions/forms needed for:
- create-with-later-edit flow that fits the existing pending robot behavior
- description editing
- callback activation/update
- pause/unpause
- delete
- masked secret preview using the `abcd…wxyz` format for non-empty secrets
- timestamp display

Do not move this flow into a new frontend stack or replace the current owner lookup/XSRF handling.

- [ ] **Step 4: Re-run the focused dashboard-related targets.**

Run:
- `sbt "testOnly org.waveprotocol.box.server.robots.RobotDashboardServletTest org.waveprotocol.box.server.rpc.HtmlRendererRobotRegistrationTest"`

Expected: the dashboard-related tests pass with the upgraded owner flow.

- [ ] **Step 5: Commit the dashboard upgrade slice.**

```bash
git add wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/RobotDashboardServlet.java \
  wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/RobotRegistrationServlet.java \
  wave/src/test/java/org/waveprotocol/box/server/robots/RobotDashboardServletTest.java \
  wave/src/test/java/org/waveprotocol/box/server/rpc/HtmlRendererRobotRegistrationTest.java

git commit -m "feat: upgrade robot dashboard management flow"
```

## Chunk 4: Release Traceability, Review, And Final Verification

### Task 6: Record the user-facing change and run final quality gates

**Files:**
- Modify: `wave/config/changelog.json`
- Modify: `wave/src/main/resources/config/changelog.json`

- [ ] **Step 1: Add the matching changelog entries.**

Describe the robot dashboard upgrade, paused enforcement, masked secret handling, staged robot management flow, and delete/pause controls in both changelog files.

- [ ] **Step 2: Run the full targeted verification set.**

Run:
- `sbt "testOnly org.waveprotocol.box.server.persistence.protos.ProtoAccountDataSerializerTest"`
- `sbt "testOnly org.waveprotocol.box.server.persistence.memory.AccountStoreTest org.waveprotocol.box.server.persistence.file.AccountStoreTest org.waveprotocol.box.server.persistence.mongodb.AccountStoreTest"`
- `sbt "testOnly org.waveprotocol.box.server.robots.register.RobotRegistrarImplTest"`
- `sbt "testOnly org.waveprotocol.box.server.robots.dataapi.DataApiTokenServletTest"`
- `sbt "testOnly org.waveprotocol.box.server.robots.passive.RobotsGatewayTest"`
- `sbt "testOnly org.waveprotocol.box.server.robots.RobotDashboardServletTest org.waveprotocol.box.server.rpc.HtmlRendererRobotRegistrationTest"`
- `sbt wave/compile`
- `sbt compileGwt`
- `python3 scripts/validate-changelog.py wave/config/changelog.json wave/src/main/resources/config/changelog.json`

Expected: all commands exit successfully.

- [ ] **Step 3: Run the local server sanity flow.**

Run: `sbt prepareServerConfig run`

Then perform the local owner flow described earlier and record the exact commands, URLs, and outcomes in Beads.

- [ ] **Step 4: Review the final delta before PR creation.**

Run:
- `git status --short --branch`
- `git diff --stat origin/main...HEAD`
- `git diff -- docs/superpowers/plans/2026-03-29-robot-dashboard-upgrade-plan.md`

Confirm only the intended robot, persistence, runtime, tests, and changelog files were changed.

- [ ] **Step 5: Run the required review loop and prepare PR handoff.**

- Run a plan review on this file before coding starts and address every material finding.
- After implementation, run a reviewer pass plus Claude review (or document provider overload if blocked).
- Add Beads comments with the worktree, branch, plan path, verification commands/results, commit SHAs, review outcomes, and PR URL.
- Open the PR against `main` only after the targeted tests, local verification, and changelog validation all pass.
