# Harness Phase 0C AGENTS Slim-Down Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete issue `#584` by slimming `AGENTS.md` into a navigational/high-signal rule doc and preserving Codex-specific tool/model/MCP routing guidance in stable docs.

**Architecture:** Keep `AGENTS.md` as the short top-level entrypoint and operational guardrail summary, then move durable Codex execution guidance into `docs/agents/tool-usage.md`. Preserve all critical routing links by updating docs-map pages where needed.

**Tech Stack:** Markdown docs, GitHub Issues workflow, local `gh` + `git` verification.

---

## Pre-Execution Gate

- [x] Plan reviewed with `claude-review` and required plan amendments applied before implementation tasks.

---

## File-by-File Edit Map

- Modify: `AGENTS.md`
  - Replace the long-form operational sections with a concise map + high-signal repo rules.
  - Keep critical constraints: GitHub Issues as source of truth, memory usage, Jakarta dual-source edit rule, worktree isolation, and review-before-PR requirements.
  - Condense Agent Roles to a short role summary and link to the orchestration plan for full role behavior.
  - Keep concise changelog/code-rule reminders and link to canonical docs for detail.
- Modify: `docs/agents/tool-usage.md`
  - Expand Codex-specific guidance so durable tool/model/MCP routing lives here (not in `AGENTS.md`).
  - Keep the content explicitly Codex-specific and repository-scoped.
  - Relocate Codex-specific worktree file-store usage guidance from `AGENTS.md` into this doc.
- Modify: `docs/agents/README.md`
  - Ensure the agent docs map clearly routes to `AGENTS.md` for rules and `tool-usage.md` for Codex tool/model guidance.
- Modify: `docs/README.md`
  - Ensure top-level docs map points cleanly to the updated agent docs split.
- Verify only: `docs/current-state.md`, `README.md`
  - Confirm existing links remain valid after the split and do not require content changes.

---

### Task 1: Baseline and Rule Inventory

**Files:**
- Modify: `docs/superpowers/plans/2026-04-08-harness-phase-0c-agents-slim-plan.md` (checkboxes only)
- Inspect: `AGENTS.md`, `docs/agents/tool-usage.md`, `docs/agents/README.md`, `docs/README.md`

- [x] **Step 1: Capture baseline before edits**

Run:
```bash
git -C /Users/vega/devroot/worktrees/issue-584-agents-slim status --short --branch
```

Expected: clean branch `issue-584-agents-slim`.

- [x] **Step 2: Inventory durable Codex guidance currently in AGENTS**

Run:
```bash
rg -n "Codex|MCP|model tier|tool rout|worktree-file-store|Jakarta" \
  /Users/vega/devroot/worktrees/issue-584-agents-slim/AGENTS.md
```

Expected: explicit list of guidance points to retain or relocate.

- [x] **Step 3: Mark this task complete in the plan**

Update this file by checking off Task 1 once inventory is complete.

- [ ] **Step 4: Commit checkpoint (optional)**

Run:
```bash
git -C /Users/vega/devroot/worktrees/issue-584-agents-slim add docs/superpowers/plans/2026-04-08-harness-phase-0c-agents-slim-plan.md
git -C /Users/vega/devroot/worktrees/issue-584-agents-slim commit -m "docs(plan): add issue #584 file-by-file implementation plan"
```

Expected: plan tracked for review context.

---

### Task 2: Slim AGENTS.md to Navigation + High-Signal Rules

**Files:**
- Modify: `AGENTS.md`
- Verify references: `docs/github-issues.md`, `docs/agents/tool-usage.md`, `docs/superpowers/plans/2026-03-18-agent-orchestration-plan.md`

- [x] **Step 1: Write a concise AGENTS structure**

Target sections:
- purpose and start-here links
- non-negotiable operating rules
- session memory reminder
- Jakarta dual-source rule
- worktree/branch isolation guardrails
- task lifecycle summary (plan -> implement -> review -> PR)
- issue evidence requirements
- agent roles (condensed summary only; detailed behavior lives in orchestration plan)
- concise changelog/code-guideline reminders with links to canonical docs
- GitHub Issue Updates section disposition: keep as a short inline rules block linking to `docs/github-issues.md` for full detail

- [x] **Step 2: Remove long-form duplicated operational detail**

Delete or condense sections that belong in deeper docs/orchestration plan while preserving the actual constraints via links.
Move Codex-specific worktree file-store procedure details into `docs/agents/tool-usage.md`.
Keep `GitHub Issue Updates` in condensed form (do not delete), with a pointer to `docs/github-issues.md`.

- [x] **Step 3: Preserve mandatory guardrails exactly**

Keep explicit rules for:
- GitHub Issues as live tracker
- `.beads` as archive only
- no edits in main tree for implementation work
- launch tmux lanes from worktree
- never create worktrees under `.claude/worktrees/`
- local sanity verification before PR when app behavior changes
- do not resolve review threads just to bypass monitoring/branch protection
- changelog validation requirement for user-facing behavior
- code guideline linkage via `CODE_GUIDELINES.md`

- [x] **Step 4: Verify AGENTS is now map-first**

Run:
```bash
wc -l /Users/vega/devroot/worktrees/issue-584-agents-slim/AGENTS.md
```

Expected: materially shorter than baseline while still covering high-signal constraints.
Target: `70-110` lines after slim-down.

- [ ] **Step 5: Commit AGENTS changes**

Run:
```bash
git -C /Users/vega/devroot/worktrees/issue-584-agents-slim add AGENTS.md
git -C /Users/vega/devroot/worktrees/issue-584-agents-slim commit -m "docs(agents): slim AGENTS to navigation plus high-signal rules"
```

---

### Task 3: Move Durable Codex Guidance into docs/agents/tool-usage.md

**Files:**
- Modify: `docs/agents/tool-usage.md`

- [x] **Step 1: Expand Codex-specific sections**

Add/update sections for:
- thread role (lead coordination vs implementation lanes)
  - Scope boundary: AGENTS keeps human role definitions; tool-usage covers Codex execution mechanics and model/tool routing by role.
- tool routing order and scope discipline
- model tier mapping for planning/implementation/review (expand existing `Model Tiers` section in place)
- MCP usage boundaries and citation expectations
- shell/tool rubric for local repo work (expand existing `Shell Tool Rubric` section in place)
- worktree file-store usage guidance for Codex lanes

- [x] **Step 2: Ensure this doc remains Codex-specific**

Keep wording explicit: this is not a generic cross-harness document.

- [x] **Step 3: Cross-check no critical Codex guidance was dropped**

Run:
```bash
rg -n "Codex|Model|MCP|Tool|Routing|Review" \
  /Users/vega/devroot/worktrees/issue-584-agents-slim/docs/agents/tool-usage.md
```

- [ ] **Step 4: Commit tool-usage update**

Run:
```bash
git -C /Users/vega/devroot/worktrees/issue-584-agents-slim add docs/agents/tool-usage.md
git -C /Users/vega/devroot/worktrees/issue-584-agents-slim commit -m "docs(agents): centralize codex tool and model routing guidance"
```

---

### Task 4: Align Docs Maps with the AGENTS/Tool-Usage Split

**Files:**
- Modify: `docs/agents/README.md`
- Modify: `docs/README.md`
- Verify: `README.md`, `docs/current-state.md`

- [x] **Step 1: Update agent docs map wording**

Ensure the map distinguishes:
- `AGENTS.md` = repo workflow rules + guardrails
- `docs/agents/tool-usage.md` = Codex tool/model/MCP routing

- [x] **Step 2: Update top-level docs map wording**

Ensure links and labels in `docs/README.md` reflect the same split.

- [x] **Step 3: Verify cross-file links**

Run:
```bash
rg -n "AGENTS\.md|tool-usage\.md|orchestration-plan|github-issues\.md" \
  /Users/vega/devroot/worktrees/issue-584-agents-slim/docs/README.md \
  /Users/vega/devroot/worktrees/issue-584-agents-slim/docs/agents/README.md \
  /Users/vega/devroot/worktrees/issue-584-agents-slim/README.md \
  /Users/vega/devroot/worktrees/issue-584-agents-slim/docs/current-state.md \
  /Users/vega/devroot/worktrees/issue-584-agents-slim/docs/github-issues.md \
  /Users/vega/devroot/worktrees/issue-584-agents-slim/docs/superpowers/plans/2026-03-18-agent-orchestration-plan.md
```

- [ ] **Step 4: Commit docs map updates**

Run:
```bash
git -C /Users/vega/devroot/worktrees/issue-584-agents-slim add docs/agents/README.md docs/README.md
git -C /Users/vega/devroot/worktrees/issue-584-agents-slim commit -m "docs(map): align docs maps with AGENTS and codex tool-usage split"
```

---

### Task 5: Verification, Reviews, and PR Prep

**Files:**
- Verify: `AGENTS.md`, `docs/agents/tool-usage.md`, `docs/agents/README.md`, `docs/README.md`

- [x] **Step 1: Run local doc hygiene checks**

Run:
```bash
git -C /Users/vega/devroot/worktrees/issue-584-agents-slim diff --check
git -C /Users/vega/devroot/worktrees/issue-584-agents-slim status --short --branch
```

Expected: no whitespace errors; clean staged/committed state before PR.

- [x] **Step 2: Claude review round 1 (plan review)**

Completed in pre-execution gate; findings were applied to this plan before implementation tasks.

- [x] **Step 3: Claude review round 2 (implementation review)**

Run `claude-review` against implementation diff and resolve any significant findings.

- [ ] **Step 4: Update issue #584 execution evidence**

Post comments including:
- plan path
- commits and one-line summary
- review findings and resolution notes
- verification commands run

- [ ] **Step 5: Create PR only after review findings are addressed**

Create PR with:
- link to issue `#584`
- acceptance criteria checklist
- explicit summary of AGENTS slimming and guidance relocation

---

## Self-Review Checklist

- Scope coverage: includes all in-scope files from issue #584 and excludes out-of-scope role-model rewrites.
- Placeholder scan: no TODO/TBD placeholders in execution steps.
- Consistency: all file paths and command paths are explicit and reusable in the worktree.
