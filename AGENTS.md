# AGENTS.md — Using MCP Servers with Codex CLI

This file gives the agent concise, practical rules for using Model Context Protocol (MCP) servers with Codex CLI. Keep actions safe, explain choices briefly, and favor tool use over guesswork.

## Why this doc
- Clarifies which MCP tools are available and how to invoke them
- Standardizes how to plan, search, and document decisions
- Minimizes risky actions by following Codex approval/sandbox policies

## Agent rules for tool use
- Prefer MCP tools over free-form browsing when available.
- Discover tool schema before use:
    - List available tools; read names, input fields, and descriptions.
    - If inputs are unclear, ask for clarification or request tool introspection.
- Keep calls minimal and purposeful. Avoid large, unfocused fetches.
- Document key calls (inputs/goal/outcome) succinctly in the private journal.

### Safe usage patterns
- Be explicit about scope: domains, max size, and format.
- Respect `robots.txt` and site terms when applicable.
- Avoid fetching sensitive or private URLs without explicit user intent.
- Cache or summarize results to reduce repeated calls.

## Tool Usage Guidance (for this setup)

Below are practical, action-focused rules for each MCP server defined in `~/.codex/config.toml`.

### Context7 (Up-to-date Docs)
- When to use: You need the latest, version-specific API docs or examples for a library/framework.
- How to use: Resolve the library name/version, then request focused docs by topic/section. Keep token limits reasonable; prefer concise, relevant excerpts.
- Good prompts: “Fetch docs for `/org/project@version` focused on ‘routing’”, “Get usage for `functionX` with examples for `v14`”.
- Output handling: Summarize key APIs, cite the version explicitly, and link doc URLs when provided.
- Safety: Prefer official sources; avoid relying on outdated snippets. Double-check versions before applying changes.

### Private Journal (Learning & Memory Management)
- YOU MUST use the journal tool frequently to capture technical insights, failed approaches, and user preferences.
- Before starting complex tasks, search the journal for relevant past experiences and lessons learned.
- Document architectural decisions and their outcomes for future reference.
- Track patterns in user feedback to improve collaboration over time.
- When you notice something that should be fixed but is unrelated to your current task, document it in your journal rather than fixing it immediately.
- Useful actions: `search_journal` (find precedents), `list_recent_entries` (quick review), `read_journal_entry` (deep-dive), `process_thoughts` (write new notes).



## Planning and documentation
- Planning (required): Use structured thinking before large actions.
    - Outline the steps, dependencies, and validation points.
    - Keep plans short and update as you proceed.
- Documentation (required): Log decisions and outcomes.
    - Summarize why a tool was chosen and the result.
    - Capture gotchas and follow-ups.

Suggested phrasing to the tools in this environment:
- Sequential thinking: “I will plan steps, verify assumptions, then proceed.”
- Private journal: “Record: tool chosen, inputs, key result, and next step.”

## Quick examples
- Web lookup then summarize:
    - Plan: identify sources → fetch one authoritative page → extract key facts → summarize.
    - Call fetch with constraints (domain, timeout, format=markdown or json if supported).
    - Journal the source URL and 2–3 bullet insights.

- Local file augmentation:
    - Plan: read file → compute change → write patch → verify formatting.
    - Use filesystem tool only within allowed root; avoid touching VCS metadata.
    - Journal diff summary and verification step.


## Agent Guidelines
- You are an agent - please keep going until the user's query is completely resolved, before ending your turn and yielding back to the user.
- Only terminate your turn when you are sure that the problem is solved.
- Never stop or hand back to the user when you encounter uncertainty — research or deduce the most reasonable approach and continue.
- Do not ask the human to confirm or clarify assumptions, as you can always adjust later — decide what the most reasonable assumption is, proceed with it, and document it for the user's reference after you finish acting
- Use the journal tool frequently to capture technical insights, failed approaches, and user preferences.
- Before starting complex tasks, search the journal for relevant past experiences and lessons learned.
- Document architectural decisions and their outcomes for future reference.
- Track patterns in user feedback to improve collaboration over time.
- When you notice something that should be fixed but is unrelated to your current task, document it.
- When working on complex tasks, use the Sequential Thinking tool, always start with a short step-by-step plan and an estimate of thoughts; mark `nextThoughtNeeded` until the solution is verified.

## Working with Git
- Before you finish your turn, if you have made any changes to files in a git repository, you MUST run `git status` and `git diff` to review your changes.
- If you have made changes that you want to keep, you MUST run commit all your changes with a clear, concise commit message describing what you have done.
- If needed group changes by relevant topics into separate commits.
- If you have made changes that you do not want to keep, you MUST revert those changes

## Code Guidelines
- Do not use FQN in your code, instead import from the appropriate module.
- Do not write one line code blocks inside brackets.
- Make sure to follow the [Codex Code Guidelines](CODE_GUIDELINES.md)

### Java code style (WaveStyle)
These rules summarize the Eclipse formatter profile in `eclipse-formatter-style.xml` (profile name: "WaveStyle"). Apply them when writing or editing Java code:

- Indentation
  - Use spaces only; no tabs. Tab width = 2, indent size = 2.
  - Continuation indent = +2 indents (i.e., +4 spaces on wrapped lines).
- Line length
  - Code lines: 100 chars max. Prefer wrapping before binary operators.
  - Javadoc/comments: wrap at 80 chars.
- Braces and layout
  - K&R style: opening brace on the same line for types, methods, blocks, constructors, enums, switch, etc.
  - Always put one space before an opening brace: `if (x) {`.
  - Do not keep then/else on the same line; prefer `} else if (...) {}` for chained conditions.
- Keyword and parentheses spacing
  - Control flow keywords followed by a space before `(`: `if (..)`, `for (..)`, `while (..)`, `switch (..)`, `try (..)`, `catch (..)`, `synchronized (..)`. 
  - Methods: no space before `(` in declarations or invocations: `void foo(int x)` and `foo(x)`.
  - No extra spaces just inside parentheses: `method(a, b)`, not `method( a, b )`.
- Operators and punctuation
  - Put spaces around binary and assignment operators: `a + b`, `x = y`.
  - No spaces for unary prefix/postfix operators: `i++`, `--i`, `!flag`.
  - Commas: space after, none before: `f(a, b)`.
  - Ternary: spaces around `?` and `:`: `cond ? a : b`.
- Generics, arrays, casts
  - No spaces inside `<...>` and around wildcards: `List<? extends T>`.
  - Array brackets have no inner spaces: `int[] a`, `a[0]`.
  - Array initializers use a space after `{` and before `}` when on one line: `{ 1, 2 }`.
  - Casts have no inner space: `(Type) value`.
- Blank lines and structure
  - 1 blank line after `package`.
  - 1 blank line before imports; 1 after imports; keep 1 between import groups.
  - 0 blank lines before fields; 1 blank line before methods; 1 before member types; 1 between top-level types.
- Switch formatting
  - `case` labels align with `switch`. Statements under a `case` are indented. `break` aligns with those statements.
- Comments and Javadoc
  - Format Javadoc, insert new lines at boundaries, and indent parameter descriptions. Use `@param`/`@return` each on its own line.
- Misc
  - Prefer wrapping long expressions before operators. Avoid manual alignment; let the formatter handle wrapping.

How to apply
- Eclipse: Import `eclipse-formatter-style.xml` and select profile "WaveStyle".
- IntelliJ IDEA: Use the Eclipse Code Formatter plugin with the provided XML, or mirror the settings above manually.


## When you need to call tools from the shell, use this rubric:

- Find Files: `fd`
- Find Text: `rg` (ripgrep)
- Find Code Structure (TS/TSX): `ast-grep`
    - **Default to TypeScript:**
        - `.ts` → `ast-grep --lang ts -p '<pattern>'`
        - `.tsx` (React) → `ast-grep --lang tsx -p '<pattern>'`
    - For other languages, set `--lang` appropriately (e.g., `--lang rust`).
- Select among matches: pipe to `fzf`
- JSON: `jq`
- YAML/XML: `yq`

If ast-grep is available avoid tools `rg` or `grep` unless a plain‑text search is explicitly requested.