# Compact Inline Avatar Same-Row Layout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep compact inline blip avatars on the same row as the author/metabar at depths 0-2 without reintroducing the old left-gutter width loss.

**Architecture:** Reuse the existing `compact-inline-blips` runtime body class and current DOM structure. Replace the current compact depth 0-2 stacked-column rules with a small grid layout where the avatar and metabar share the top row and the content container spans both columns below, while depth 3+ reset rules keep the baseline slide-nav layout unchanged.

**Tech Stack:** GWT CssResource CSS (`Blip.css`), JVM CSS contract test (`CompactInlineBlipCssContractTest`), changelog fragment + changelog assembly validation.

---

**Issue:** [#981](https://github.com/vega113/supawave/issues/981)
**Worktree:** `/Users/vega/devroot/worktrees/issue-981-compact-inline-avatar-row`
**Branch:** `codex/issue-981-compact-inline-avatar-row`

## Scope

- Keep using the existing `compact-inline-blips` feature flag.
- No new flag, no new Java flag plumbing, no DOM builder changes unless the CSS-only approach proves impossible.
- Preserve root blip layout and the depth 3+ reset behavior added after PR #930.
- Add a regression contract that proves the compact layout is same-row on desktop and mobile.

## Fixed layout contract

- Compact depth 0-2 `.meta` uses `display: grid`.
- Compact depth 0-2 `.meta` uses `grid-template-columns: auto minmax(0, 1fr)`.
- Compact depth 0-2 `.meta` uses explicit spacing instead of a stacked avatar margin:
  - desktop: `column-gap: 0.35em`, `row-gap: 0.25em`, `padding-left: 0.75em`
  - mobile: `column-gap: 0.3em`, `row-gap: 0.2em`, `padding-left: 0.5em`
- Compact depth 0-2 `.avatar` is `float: none`, `grid-column: 1`, `grid-row: 1`, with `margin-left: 0` and `margin-bottom: 0`.
- Compact depth 0-2 `.metabar` is `grid-column: 2`, `grid-row: 1`, and `min-width: 0`.
- Compact depth 0-2 `.contentContainer` is `grid-column: 1 / -1` and `grid-row: 2`.
- Avatar sizes stay fixed at the current compact values unless the CSS contract is updated in the same change:
  - desktop depth 0/1/2: `24px`, `22px`, `20px`
  - mobile depth 0/1/2: `22px`, `20px`, `20px`

## Files

- Modify: `wave/src/test/java/org/waveprotocol/box/server/util/CompactInlineBlipCssContractTest.java`
- Modify: `wave/src/main/resources/org/waveprotocol/wave/client/wavepanel/view/dom/full/Blip.css`
- Create: `wave/config/changelog.d/2026-04-23-compact-inline-avatar-row.json`

### Task 1: Lock the intended CSS shape with a failing regression test

**Files:**
- Modify: `wave/src/test/java/org/waveprotocol/box/server/util/CompactInlineBlipCssContractTest.java`

- [ ] Update the desktop compact regex assertions so depths 0-2 require a same-row layout instead of the current stacked avatar rules.
- [ ] Require the compact depth 0-2 `.meta` block to declare a two-column grid with a narrow avatar column and content below.
- [ ] Require the compact depth 0-2 `.meta` block to declare `grid-template-columns: auto minmax(0, 1fr)` plus the explicit desktop/mobile `column-gap`, `row-gap`, and `padding-left` values from the fixed layout contract.
- [ ] Require the compact depth 0-2 `.avatar` block to stop floating, sit in the first row, and drop the stack-specific bottom margin.
- [ ] Require the compact depth 0-2 `.metabar` block to sit in the first row next to the avatar and keep `min-width: 0`.
- [ ] Require the compact depth 0-2 `.contentContainer` block to span both columns so the body text reuses the width under the avatar.
- [ ] Mirror the same-row expectations for `.compact-inline-blips-mobile`.
- [ ] Require the old stacked-column markers (`display: flex`, `flex-direction: column`, avatar `margin-bottom`) to disappear from the compact depth 0-2 rule blocks themselves instead of merely being shadowed elsewhere.
- [ ] Assert the current mobile selector name verbatim as `.compact-inline-blips-mobile` so the test cannot silently drift to a renamed selector.
- [ ] Assert the compact avatar sizes in the desktop/mobile depth 0-2 rule blocks so size changes require an intentional contract update.
- [ ] Run the focused test and confirm it fails for the expected reason before touching production CSS.

Run:
```bash
sbt "wave/testOnly org.waveprotocol.box.server.util.CompactInlineBlipCssContractTest"
```

Expected before the CSS fix:
- FAIL because the current CSS still matches the stacked avatar assertions from PR #930.

### Task 2: Replace the stacked compact layout with a same-row grid

**Files:**
- Modify: `wave/src/main/resources/org/waveprotocol/wave/client/wavepanel/view/dom/full/Blip.css`

- [ ] Change compact depth 0-2 `.meta` rules from `display: flex` / `flex-direction: column` to a small grid that places the avatar beside the metabar and the content below them.
- [ ] Keep compact depth 0-2 `padding-left` reduced relative to baseline so the old avatar gutter does not return.
- [ ] Move compact depth 0-2 avatars out of float positioning, place them in the first grid row, and preserve the current compact avatar sizes for each depth.
- [ ] Place compact depth 0-2 `.metabar` in the first grid row with `min-width: 0` so the author line can still ellipsize correctly.
- [ ] Make compact depth 0-2 `.contentContainer` span the full grid width below the header row.
- [ ] Apply the same-row pattern to `.compact-inline-blips-mobile` with the existing mobile padding scale, again without reintroducing the legacy gutter.
- [ ] Delete the previous compact stacked-column rules rather than leaving them in place underneath the grid rules.
- [ ] Keep the existing depth 3+ reset selectors restoring baseline `.meta` / `.avatar` behavior.
- [ ] Run the focused CSS contract test and confirm it passes.

Run:
```bash
sbt "wave/testOnly org.waveprotocol.box.server.util.CompactInlineBlipCssContractTest"
```

Expected after the CSS fix:
- PASS for desktop same-row assertions, mobile same-row assertions, and depth 3+ reset assertions.

### Task 3: Record the user-facing change and validate the changelog

**Files:**
- Create: `wave/config/changelog.d/2026-04-23-compact-inline-avatar-row.json`

- [ ] Add a new changelog fragment describing the compact inline follow-up: avatar stays on the same row as the author line while remaining behind the existing `compact-inline-blips` rollout.
- [ ] Use wording equivalent to: `Compact inline replies now keep the avatar on the same row as the author line to save space on desktop and mobile when compact-inline-blips is enabled.`
- [ ] Rebuild `wave/config/changelog.json`.
- [ ] Validate the assembled changelog.

Run:
```bash
python3 scripts/assemble-changelog.py
python3 scripts/validate-changelog.py --changelog wave/config/changelog.json
```

Expected:
- `assemble-changelog.py` exits 0 and regenerates `wave/config/changelog.json`.
- `validate-changelog.py` exits 0.

### Task 4: Local verification, self-review, external review, and PR flow

**Files:**
- Re-read the diff in the three files above before review and PR.

- [ ] Reuse the main repo file store in this worktree for realistic threaded-wave verification.
- [ ] Rebuild the GWT client so the `Blip.css` changes actually reach the served browser bundle.
- [ ] Start a local server from the worktree on a free port.
- [ ] Enable or confirm the existing `compact-inline-blips` flag in the local admin UI or store; do not create a new flag.
- [ ] Verify in a browser that a nested inline reply shows avatar + author row on the same line on desktop width.
- [ ] Verify the same layout at a `375px` mobile viewport width.
- [ ] Verify root blips still use the baseline layout and depth 3+ still falls back to the existing reset behavior.
- [ ] Verify long author text still ellipsizes correctly and the metabar does not overlap the avatar or content.
- [ ] Verify reading order remains sane by checking the DOM order is unchanged and keyboard focus/menu interaction still works.
- [ ] Verify the reaction row still renders correctly beneath the content container after the compact grid change.
- [ ] Capture one desktop and one mobile screenshot for the PR body.
- [ ] Run a self-review over the final diff for scope creep, mobile regressions, and metabar/content overlap risks.
- [ ] Run required external review on the implementation diff via `claude-review` and address any findings before PR.
- [ ] Update issue #981 with plan path, verification commands/results, review outcome, commit SHA, and PR link.
- [ ] Open the PR against `main`, then monitor CI and merge status until it lands.

Run:
```bash
scripts/worktree-file-store.sh --source /Users/vega/devroot/incubator-wave
```

Local verification commands:
```bash
bash scripts/worktree-boot.sh --port <free-port> --shared-file-store
```

Targeted regression/build commands:
```bash
sbt "wave/testOnly org.waveprotocol.box.server.util.CompactInlineBlipCssContractTest"
sbt compileGwt
python3 scripts/assemble-changelog.py
python3 scripts/validate-changelog.py --changelog wave/config/changelog.json
```

## Non-goals

- No StageTwo feature-flag changes unless investigation proves the existing body-class plumbing is broken.
- No edits to `KnownFeatureFlags.java` unless we discover the current flag metadata itself blocks rollout.
- No slide-nav redesign or depth-limit changes.
- No root blip chrome redesign.
- No reaction-row DOM/CSS redesign beyond verifying it still renders correctly beneath the compact grid.
- No RTL-specific redesign in this follow-up; preserve current DOM order and note any RTL issue separately if encountered during verification.
