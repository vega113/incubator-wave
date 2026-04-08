# AGENTS.md — Repo Entry Point for Incubator Wave

This is the top-level operator entrypoint for `incubator-wave`.

Read in this order:
- `docs/github-issues.md` for the live issue workflow and Beads archive policy.
- `docs/agents/tool-usage.md` for Codex-specific model/tool/MCP routing.
- `docs/superpowers/plans/2026-03-18-agent-orchestration-plan.md` for the detailed multi-agent execution flow.

## Operating Model
- Keep the main thread focused on intake, routing, integration, and final verification.
- Route substantive planning, implementation, and review work to dedicated lanes in isolated worktrees.
- Use GitHub Issues as the live tracker for new work.
- Treat `.beads/` as archive-only for historical context.
- If this file and the orchestration plan disagree, the orchestration plan is authoritative.

## Session Memory
- At session start, read `MEMORY.md` from `.claude/projects/` for this repo.
- Read only the memory files relevant to the current task.
- Treat memory as persistent workflow/architecture guidance across sessions.

## Jakarta Dual-Source Rule
- Runtime-active Jakarta replacements live under `wave/src/jakarta-overrides/java/`.
- Legacy source remains under `wave/src/main/java/`.
- If an override exists, edit the override copy; main-tree-only edits will not change runtime behavior.

## Role Summary
- Lead: intake, routing, synthesis, and final checks.
- Planner: create/verify issue-level plan and acceptance slices.
- Architect: investigate constraints and produce complex implementation plans.
- Worker: implement assigned slice in dedicated worktree.
- Reviewer: review implementation and produce actionable findings.

For detailed role behavior and sequencing, follow:
`docs/superpowers/plans/2026-03-18-agent-orchestration-plan.md`.

## Worktree And Branch Guardrails
- Every agent that edits code/docs must work in its own git worktree.
- Prefer multiple independent worktrees/lanes over single-threaded execution when the task can be safely parallelized.
- Do not implement from `/Users/vega/devroot/incubator-wave`.
- Use `/Users/vega/devroot/worktrees/<branch-name>` for worktree paths.
- Never create worktrees under `.claude/worktrees/`.
- Launch tmux lanes from the worktree directory, not from the main repo tree.
- Do not run `git checkout` or `git switch` inside the main repo during lane execution; it flips shared HEAD for open sessions.
- Do not mix lane edits in the main working tree.

## Task Lifecycle
- Ensure the linked issue has an adequate plan before implementation.
- If no adequate plan exists, switch to plan mode, write plan, run Claude review, then implement.
- Keep issue comments current during execution, not only at the end.
- After implementation, run reviewer flow, address findings, then prepare PR.

## PR Readiness Rules
- Before PR for app-affecting changes, run a narrow local sanity verification relevant to changed area.
- Record exact verification command and result in the linked GitHub Issue.
- Address review conversations; do not resolve threads just to bypass monitoring or branch protection.
- Keep issue, commits, and PR traceability aligned.

## GitHub Issue Updates
- Record worktree path and branch.
- Record plan path used for implementation.
- Record commit SHAs with one-line summaries.
- Record verification commands and outcomes.
- Record review findings plus follow-up resolution notes.
- Record PR number/URL.

Use `docs/github-issues.md` as the canonical evidence format and workflow reference.

## Changelog And Code Rules
- Any PR changing user-facing behavior must add a new changelog fragment under `wave/config/changelog.d/`; do not hand-edit generated `wave/config/changelog.json`.
- Run the changelog assemble/validate workflow so `wave/config/changelog.json` is regenerated, and validate with `scripts/validate-changelog.py` before merge/deploy.
- Follow `CODE_GUIDELINES.md` for repo-wide style and contribution rules.
