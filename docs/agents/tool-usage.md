# Codex Tool Usage for incubator-wave

This document is Codex-specific guidance for `incubator-wave`.
Use it with [AGENTS.md](../../AGENTS.md) for repo rules and with
[docs/superpowers/plans/2026-03-18-agent-orchestration-plan.md](../superpowers/plans/2026-03-18-agent-orchestration-plan.md)
for end-to-end role sequencing.

## Role Boundary
- `AGENTS.md` defines repo workflow rules and guardrails.
- This document defines Codex execution mechanics: model routing, tool routing,
  MCP usage, and lane-level implementation behavior.

## Lead Thread Execution Mechanics
- Main Codex thread should coordinate intake, routing, synthesis, and final verification.
- Substantive planning, implementation, and review should execute in dedicated worktree lanes.
- Use GitHub Issue scope as the boundary for each lane.
- Keep updates short and evidence-based (what was run, what changed, what remains).

## Model Tiers
- Complex planning, architecture, and ambiguous bugs:
  - `gpt-5.4` with `xhigh` reasoning.
- General implementation and medium-complexity coding:
  - `gpt-5.4` with `high` reasoning.
- Narrow, bounded edits that do not need broad context:
  - `gpt-5.4-mini` with `high` reasoning.
- Code review passes:
  - use Codex review flow (plus Claude review when required by workflow).

## Tool Routing
- Prefer direct repo and connector tooling before broad web browsing.
- Prefer MCP/connector tools for GitHub metadata, issue context, and PR state.
- Keep tool calls minimal, scoped, and purposeful.
- Avoid speculative exploration that is outside the active issue scope.

Recommended sequence for issue execution:
1. Read issue and acceptance criteria.
2. Verify plan exists (or create one and review it).
3. Implement in worktree lane.
4. Run targeted verification.
5. Run review pass (`claude-review` plus Codex review as required by workflow).
6. Update issue evidence.
7. Open PR.

## MCP Guidance
- `Context7`:
  - Use for version-specific library/framework docs.
  - Prefer official sources and cite resolved version in reasoning.
- `Memento` (if available):
  - Use for prior failed approaches, user preferences, and historical decisions.
- For any MCP tool:
  - inspect tool schema before use.
  - constrain domain/scope of calls.
  - summarize outcomes instead of dumping raw output.

## Worktree File-Store Guidance (Codex)
When a new worktree needs existing file-based persistence state:
- Use skill: `incubator-wave-worktree-file-store`.
- Run from target worktree:
  - `scripts/worktree-file-store.sh --source $HOME/devroot/incubator-wave`
- Prefer default `symlink` mode to reuse `_accounts`, `_attachments`, and `_deltas`.
- Use `--mode copy` only when isolated persistence is explicitly required.

Mandatory worktree constraints:
- Implement from `/Users/vega/devroot/worktrees/<branch-name>`.
- Do not run implementation from the main repo checkout.
- Do not create worktrees under `.claude/worktrees/`.
- Launch tmux lanes from the worktree directory.

## Shell Tool Rubric
- Find files: `fd`
- Find text: `rg`
- Find code structure in TypeScript or TSX: `ast-grep`
  - `.ts` -> `ast-grep --lang ts -p '<pattern>'`
  - `.tsx` -> `ast-grep --lang tsx -p '<pattern>'`
  - Other languages -> set `--lang` accordingly.
- Select among matches: `fzf`
- JSON: `jq`
- YAML/XML: `yq`
- Prefer `ast-grep` for structural queries when supported; use `rg` for plain-text or regex.

## Scope And Safety
- State scope explicitly before acting: target files, issue id, and verification command plan.
- Do not widen scope mid-task without updating issue/plan context.
- Respect private/sensitive data boundaries in logs and command output.
- Keep verification proportional and relevant to changed behavior.

## Review Command
- Use `claude-review` for required external review passes (plan and implementation review checkpoints).
- Keep review evidence in the linked GitHub Issue: findings, resolutions, and rerun outcome when applicable.
