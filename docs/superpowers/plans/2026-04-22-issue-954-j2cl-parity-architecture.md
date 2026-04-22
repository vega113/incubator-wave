# Issue #954 J2CL Parity Architecture Investigation Plan

> **For agentic workers:** Treat this as an investigation/documentation slice, not an implementation spike. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce a repo-backed architecture memo that explains the remaining parity gap between the legacy GWT client and the current J2CL client, recommends the long-term J2CL UI framework/runtime approach, and defines how SupaWave should handle read-only-first load, backend coordination, and viewport-scoped wave loading during the rest of the migration.

**Architecture:** Start from the current post-`#953` coexistence baseline: the repo already ships a J2CL sidecar, selected-wave read flow, route state, write pilot, root shell, reversible bootstrap seam, legacy-GWT rollback path, server-side pre-render hooks, and viewport-aware fragment transport. The deliverable for `#954` is a committed markdown memo that ties those existing seams together into one migration architecture: map the old `StageOne` / `StageTwo` / `StageThree` responsibilities to J2CL-era equivalents, compare realistic UI framework options for the J2CL view layer, and recommend a parity-first sequence for server rendering, hydration/upgrade, and incremental wave loading.

**Tech Stack:** Markdown docs under `docs/`, existing J2CL Maven sidecar under `j2cl/`, legacy GWT client under `wave/src/main/java/org/waveprotocol/wave/client/**`, Jakarta servlet/rendering code, primary-source framework documentation, GitHub issue/PR traceability, and Claude review for plan plus implementation review loops.

---

## 1. Goal / Baseline / Why This Exists

Issue `#954` exists because the staged J2CL migration is now far enough along that the remaining gap is no longer “can J2CL render anything?” It is “what architecture gets the repo from the current sidecar/root-shell proof to practical parity with GWT without backing into another UI dead end?”

Observed repo facts that make this memo necessary:

- `wave/src/main/java/org/waveprotocol/wave/client/StageOne.java` still defines the first real reader-stage runtime boundary around `WavePanelImpl`, focus, collapse, thread navigation, DOM view providers, and CSS-backed wave rendering.
- `wave/src/main/java/org/waveprotocol/wave/client/StageTwo.java` still owns connector activation, feature installation, fragments wiring, dynamic rendering, and selected-wave runtime behavior.
- `wave/src/main/java/org/waveprotocol/box/webclient/client/StagesProvider.java` still assembles the real GWT app around those stages and then publishes `WaveContext`, toolbar wiring, archive/pin hooks, and history behavior.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSearchPanelView.java`, `J2clSelectedWaveView.java`, `J2clSidecarComposeView.java`, and `j2cl/root/J2clRootShellView.java` currently build the J2CL UI directly with Elemental2 DOM calls and manual render methods; there is no higher-level component model yet.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSearchGateway.java` already coordinates with the backend through root-bootstrap HTML scraping, `/search`, and `/socket`, but it still carries transitional seams like `document.cookie` WebSocket auth.
- `wave/src/main/java/org/waveprotocol/box/server/rpc/render/WavePreRenderer.java` and `wave/config/reference.conf` already define a server-side pre-render + shell-swap seam for the legacy client, proving the repo already has a place to discuss read-only-first rendering rather than inventing it from scratch.
- `wave/src/main/java/org/waveprotocol/box/server/frontend/WaveClientRpcImpl.java`, `RemoteViewServiceMultiplexer.java`, and the dynamic-rendering/fragments code already define viewport-hinted partial-wave transport, so large-wave incremental loading should be part of the parity architecture instead of a later afterthought.
- `wave/src/main/java/org/waveprotocol/wave/client/StageThree.java` and `wave/src/main/java/org/waveprotocol/wave/client/Stages.java` still matter because parity is not only read-only rendering; the GWT runtime still has a staged lifecycle for editing, toolbars, and feature activation that the memo must either preserve conceptually or replace deliberately.

The memo should answer the remaining architectural question: what is the repo’s intended post-GWT UI stack and migration shape now that the first staged J2CL slices are already real?

## 2. Scope And Non-Goals

### In Scope

- compare the current GWT runtime architecture and the shipped J2CL runtime architecture
- explain the parity gap in runtime/framework terms, not only widget inventory
- map the old stage model (`StageOne`, `StageTwo`, `StageThree`) to J2CL-era responsibilities
- evaluate realistic framework options for the J2CL UI layer and recommend one
- define how server-rendered read-only content, client hydration/upgrade, and backend transport should fit together in this repo
- define how large-wave loading should use viewport/fragments seams instead of whole-wave bootstrap
- produce one committed markdown memo under `docs/`

### Explicit Non-Goals

- no implementation of the chosen framework
- no production cutover of `/` back to J2CL
- no removal of the legacy GWT client
- no broad code refactors outside the new memo and any small doc-index updates needed to reference it
- no attempt to solve the J2CL auth hardening or unread-state follow-up issues as part of this doc slice

## 3. Exact Files To Read And Likely Files To Change

### Primary Repo Evidence Inputs

- `docs/j2cl-gwt3-decision-memo.md`
- `docs/j2cl-preparatory-work.md`
- `docs/superpowers/plans/j2cl-full-migration-plan.md`
- `docs/runbooks/j2cl-sidecar-testing.md`
- `wave/src/main/java/org/waveprotocol/wave/client/StageOne.java`
- `wave/src/main/java/org/waveprotocol/wave/client/StageTwo.java`
- `wave/src/main/java/org/waveprotocol/wave/client/StageThree.java`
- `wave/src/main/java/org/waveprotocol/wave/client/Stages.java`
- `wave/src/main/java/org/waveprotocol/box/webclient/client/StagesProvider.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/root/J2clRootShellController.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/root/J2clRootShellView.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSearchGateway.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSearchPanelView.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveView.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSidecarComposeView.java`
- `wave/src/main/java/org/waveprotocol/box/server/rpc/render/WavePreRenderer.java`
- `wave/src/main/java/org/waveprotocol/box/server/frontend/WaveClientRpcImpl.java`
- `wave/src/main/java/org/waveprotocol/box/webclient/client/RemoteViewServiceMultiplexer.java`
- `wave/config/reference.conf`

### External Anchors

- `docs/runbooks/j2cl-sidecar-testing.md`
- `docs/j2cl-gwt3-decision-memo.md` as the prior migration-baseline memo that the new doc must explicitly complement or supersede
- issue `#904` and merged PR `#953` as the live coexistence/rollback anchors for the current dual-root state

### Likely New / Updated Files

- `docs/j2cl-parity-architecture.md`
- optional: `docs/README.md` or `docs/DOC_REGISTRY.md` only if the new memo needs an index entry

## 4. Investigation Questions The Memo Must Answer

### Runtime Architecture

- [ ] What responsibilities do `StageOne`, `StageTwo`, and `StageThree` currently own in the GWT client, and which of those are still missing or only partially represented in the J2CL path?
- [ ] How do `Stages.java` and `StagesProvider.java` sequence those stage responsibilities today, and which of those sequencing/lifecycle seams must survive in the J2CL-era architecture?
- [ ] Which current J2CL seams are already good foundations for parity, and which ones are intentionally temporary?
- [ ] Which gaps are UI-only, and which are framework/runtime gaps (rendering model, lifecycle, routing, state ownership, backend bootstrap, incremental transport)?
- [ ] How would J2CL-owned Java models, events, and view-state cross the boundary into the chosen framework/runtime, and what interop seam keeps that bridge narrow instead of spreading framework code through the whole client?
- [ ] How does the post-`#953` contract constrain the recommendation: GWT remains the default root UI while J2CL sidecar/root diagnostics stay available during the coexistence window?

### Framework Decision

- [ ] What UI framework/runtime options are realistic for a J2CL-based SupaWave client?
- [ ] Which option best supports:
  - J2CL-hosted application logic
  - SSR/read-only-first rendering with later client upgrade
  - incremental, component-scoped updates for large waves
  - gradual migration instead of a big-bang rewrite
  - thin interop with existing backend/bootstrap seams
  - tolerable build/tooling impact on the current SBT + J2CL Maven sidecar pipeline
  - preserving accessibility and localization parity while GWT and J2CL coexist
- [ ] Keep the framework comparison intentionally small: at most one short comparison table plus a concise recommendation section, not a broad ecosystem survey.
- [ ] The memo must compare at least:
  - continuing with plain Elemental2/manual DOM
  - a lightweight component/view model that works with plain custom elements / Web Components
  - React as the concrete mainstream SSR/hydration framework comparison point
- [ ] The memo must make one clear recommendation and explain why the rejected options are weaker for this repo.

### Backend / Loading Model

- [ ] How should the root bootstrap, session bootstrap, auth state, and selected-wave open flow coordinate between server and J2CL client?
- [ ] What is the recommended “read-only first, interactive later” flow for SupaWave, and where should the server stop versus where should the client take over?
- [ ] How should large waves use viewport-scoped fragment loading and dynamic rendering rather than whole-wave loading?
- [ ] Which current repo seams make that feasible now, and what follow-on gaps remain?

## 5. Concrete Task Breakdown

### Task 1: Freeze The Investigation Boundary

- [ ] Keep `#954` scoped to one architecture memo plus any small index link updates needed for discoverability.
- [ ] Treat `#904` as the parent tracker and avoid widening this slice into implementation work that belongs to the later child issues.
- [ ] Record the plan path in the issue comments before the main doc lands.

### Task 2: Capture The Current Repo Baseline

- [ ] Read the current migration docs and the live J2CL/GWT code seams listed above.
- [ ] Extract repo-specific evidence for:
  - old GWT staging/runtime boundaries
  - current J2CL view architecture
  - existing server prerender support
  - existing viewport/fragments support
- [ ] Avoid generic statements unless the repo evidence or primary-source docs support them.

### Task 3: Research External Framework Options From Primary Sources

- [ ] Use current primary-source documentation for the candidate framework options.
- [ ] Keep the candidate shortlist explicit and bounded:
  - plain Elemental2/manual DOM as the status-quo baseline
  - a Web Components-oriented option (for example Lit-style component composition)
  - React as the mainstream SSR/hydration framework option with a larger runtime boundary
- [ ] Constrain the research to framework capabilities that materially matter for SupaWave:
  - SSR/hydration or resumable upgrade
  - incremental rendering/state granularity
  - interop tolerance for J2CL-driven logic
  - migration ergonomics from manual DOM or server-rendered HTML
  - build/toolchain cost in the current repo
  - a11y/i18n parity implications
- [ ] Keep the final comparison short and decision-oriented rather than producing a general framework survey: one short table, three options maximum, one explicit recommendation.

### Task 4: Write The Architecture Memo

- [ ] Create `docs/j2cl-parity-architecture.md`.
- [ ] Structure the memo roughly as:
  - current baseline
  - remaining parity gap
  - stage-model remapping
  - framework options and recommendation
  - J2CL-to-framework interop seam
  - read-only-first + hydration architecture
  - viewport-scoped wave loading architecture
  - rollback/coexistence constraints from the current GWT + J2CL dual-root state, grounded in `docs/runbooks/j2cl-sidecar-testing.md`, `#904`, and merged PR `#953`
  - relationship to `docs/j2cl-gwt3-decision-memo.md` and whether this memo complements or supersedes it
  - proposed next slices / decision checklist tied back to `#904`
- [ ] Make every repo-behavior claim traceable to specific files or current docs.
- [ ] Keep the recommendation opinionated enough to drive future issue slicing.
- [ ] Describe the auth/session bootstrap seam as a current constraint when relevant, but do not widen the memo into solving `document.cookie` WebSocket auth hardening in this slice.

### Task 5: Review Loop Before PR

- [ ] Self-review the memo for scope drift, unsupported claims, and missing parity seams.
- [ ] Run the required Claude review loop on the plan first.
- [ ] Address any valid Claude plan-review findings, rerun, and stop only when the plan review is effectively clean or blocked for a documented provider reason.
- [ ] After the memo is written, run a second Claude review on the implementation diff, address valid findings, and rerun until clean or until a real blocker remains.

### Task 6: Traceability And PR Closeout

- [ ] Add issue comments with:
  - worktree path
  - plan path
  - review outcomes
  - commit SHAs
  - PR link
- [ ] Because this is a doc-only slice, verification should focus on:
  - doc completeness and internal consistency
  - clean diff (`git diff --check`)
  - successful review loops
- [ ] Do not update `docs/superpowers/plans/j2cl-full-migration-plan.md` or older migration docs unless the final memo needs a minimal pointer for discoverability.
- [ ] Start a PR monitor after the PR is opened and keep it alive until the PR is actually merged or truly blocked.

## 6. Review / Verification Commands

Run these from `/Users/vega/devroot/worktrees/issue-954-j2cl-parity-architecture`.

### Diff Sanity

```bash
git diff --check
```

Expected result:

- no whitespace or patch-format issues in the doc changes

### Plan Review

Use the repo-required Claude review flow against the plan file diff or plan file content, targeting the newest Claude Opus 4.7 model available in the local workflow.

Expected result:

- plan review produces either no actionable findings or a bounded set of comments that get addressed and rerun before memo authoring continues

### Implementation Review

Use the same Claude review flow against the final doc diff before PR.

Expected result:

- no remaining actionable findings on the committed memo diff

## 7. Definition Of Done

- `docs/j2cl-parity-architecture.md` exists and is committed
- the memo explains the parity gap in terms of SupaWave’s actual runtime seams, not only abstract migration advice
- the memo makes one explicit UI framework/runtime recommendation and justifies it against realistic alternatives
- the memo explains how server-side read-only-first rendering, later client hydration/upgrade, and viewport-scoped large-wave loading should work together in this repo
- the memo states how the current rollback/coexistence contract constrains the recommended architecture during the remaining migration window
- the memo ties the recommendation back to concrete follow-on slices under `#904`
- `git diff --check` has been run clean on the final doc changes
- the issue and PR both contain the plan path, review evidence, and final doc traceability
- the PR is opened and placed under active monitoring until merged or truly blocked
