# Task Query Help Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expand task-search semantics so `tasks:all`, `tasks:me`, and user-targeted task queries are explicit and consistent, then update the search help UI to document the supported task query forms accurately.

**Architecture:** Keep the implementation narrow by preserving the existing `tasks:` token path in `QueryHelper`, `TaskQueryNormalizer`, and `SimpleSearchProviderImpl`. Add one reserved task token value, `all`, that means “any wave containing at least one task annotation that is already visible to the current user,” while keeping explicit-address and bare-name assignee matching unchanged. Keep task semantics authoritative in the legacy provider and let Lucene continue to narrow only text queries, so `tasks:all`, `tasks:me`, and mixed task-plus-text searches stay consistent. Update only the search help panel copy/examples needed to reflect the parser’s real behavior; do not widen the task toolbar button behavior in this issue.

**Tech Stack:** Java, GWT UiBinder, junit3, sbt, GitHub issue workflow

---

## Investigation Summary

- Existing task search already supports:
  - `tasks:me`
  - `tasks:user@domain`
  - bare names such as `tasks:alice`, which normalize to `alice@<current-domain>`
- The missing behavior is `tasks:all`.
  - Today `TaskQueryNormalizer.normalize("all", user)` produces `all@<current-domain>`.
  - That means `tasks:all` currently behaves like an assignee search instead of “any task visible to me.”
- The current search model can support `tasks:all` without new indexing work.
  - Search results are already limited to waves the current user can access.
  - `filterByTasks(...)` already walks wave data and can be extended to match “has any task assignee” instead of “has one of these assignees.”
- Lucene must not own task semantics.
  - `Lucene9SearchProviderImpl` intersects Lucene candidates with legacy results when text filters are present.
  - `Lucene9QueryCompiler` therefore needs to leave `tasks:` semantics to the legacy provider; otherwise `tasks:all` degenerates into a bogus `all@<domain>` term query.
- Compatibility constraint:
  - Reserving bare `tasks:all` means it can no longer mean the local-domain user `all@<domain>`.
  - The explicit-address form `tasks:all@example.com` still remains queryable and unambiguous.
- Non-goals for this slice:
  - do not change the unread-task polling query (`tasks:me unread:true`)
  - do not change the toolbar Tasks button behavior
  - do not add new task badges, counts, or task-status semantics

## Acceptance Mapping

- `tasks:all` returns accessible waves that contain at least one task.
- `tasks:me` continues to return accessible waves with tasks assigned to the signed-in user.
- `tasks:user@domain` continues to return accessible waves with tasks assigned to that user.
- Bare-name queries such as `tasks:alice` remain supported and are documented in help as shorthand for the local domain.
- Search help copy/examples explain the supported task query variants and the local-domain shorthand.

## Task 1: Add Failing Tests for the New Task Query Semantics

**Files:**
- Modify: `wave/src/test/java/org/waveprotocol/box/server/waveserver/TaskQueryNormalizerTest.java`
- Modify: `wave/src/test/java/org/waveprotocol/box/server/waveserver/SimpleSearchProviderImplTest.java`
- Modify: `wave/src/test/java/org/waveprotocol/box/server/waveserver/lucene9/Lucene9QueryModelTest.java`
- Create: `wave/src/test/java/org/waveprotocol/box/server/waveserver/lucene9/Lucene9QueryCompilerTest.java`
- Create: `wave/src/test/java/org/waveprotocol/box/server/waveserver/lucene9/Lucene9SearchProviderImplTest.java`

- [ ] Add a failing normalizer test showing `tasks:all` stays reserved as `all` instead of becoming `all@<domain>`.
- [ ] Add a failing normalizer test showing the explicit address `tasks:all@example.com` stays queryable and is not swallowed by the reserved bare token.
- [ ] Add a failing search-provider regression test showing `tasks:all` returns waves with any task assignee visible to the current user.
- [ ] Add a failing combined search test showing `tasks:all unread:true` still filters correctly after task matching.
- [ ] Add/update Lucene expectations so `tasks:all` is passed through to the legacy path unchanged and pure task queries bypass Lucene candidate lookup.
- [ ] Add a failing Lucene compiler test showing mixed queries such as `tasks:all title:meeting` keep task semantics out of the Lucene candidate query.
- [ ] Run: `sbt "testOnly org.waveprotocol.box.server.waveserver.TaskQueryNormalizerTest org.waveprotocol.box.server.waveserver.lucene9.Lucene9QueryModelTest org.waveprotocol.box.server.waveserver.lucene9.Lucene9QueryCompilerTest org.waveprotocol.box.server.waveserver.lucene9.Lucene9SearchProviderImplTest org.waveprotocol.box.server.waveserver.SimpleSearchProviderImplTest"`
  Expected: the new `tasks:all` cases fail before the implementation change.

## Task 2: Implement the Narrow Search Semantics Change

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/box/server/waveserver/TaskQueryNormalizer.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/server/waveserver/SimpleSearchProviderImpl.java`
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/waveserver/lucene9/Lucene9QueryCompiler.java`
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/waveserver/lucene9/Lucene9SearchProviderImpl.java`

- [ ] Reserve bare `all` in `TaskQueryNormalizer` so it survives normalization unchanged.
- [ ] Extend the task-filter extraction path so the provider can distinguish “any task” from assignee-specific matching.
- [ ] Update `filterByTasks(...)` call-site logic so `tasks:all` matches waves with any extracted task assignee while preserving the current legacy `containsAll(...)` matching behavior for multiple explicit assignee filters.
- [ ] Keep the implementation backward-compatible for `tasks:me`, `tasks:user@domain`, and `tasks:bare-name`.
- [ ] Keep Lucene task handling legacy-only: pure task queries should return legacy results directly, and mixed task-plus-text queries should use Lucene only for text narrowing.
- [ ] Re-run: `sbt "testOnly org.waveprotocol.box.server.waveserver.TaskQueryNormalizerTest org.waveprotocol.box.server.waveserver.lucene9.Lucene9QueryModelTest org.waveprotocol.box.server.waveserver.lucene9.Lucene9QueryCompilerTest org.waveprotocol.box.server.waveserver.lucene9.Lucene9SearchProviderImplTest org.waveprotocol.box.server.waveserver.SimpleSearchProviderImplTest"`
  Expected: all targeted task-query tests pass.

## Task 3: Update Search Help UI To Match Real Semantics

**Files:**
- Modify: `wave/src/main/resources/org/waveprotocol/box/webclient/search/SearchWidget.ui.xml`
- Modify: `wave/src/main/java/org/waveprotocol/box/webclient/search/SearchWidget.java`

- [ ] Replace the single task-help row with task examples that cover `tasks:all`, `tasks:me`, and a user-targeted query.
- [ ] Document that bare names are shorthand for the local domain, for example `tasks:alice`.
- [ ] Keep examples clickable through the existing help wiring.
- [ ] Do not change the search-toolbar Tasks button query in this issue.

## Task 4: Verify, Review, and Record Issue Evidence

**Files:**
- Modify if needed: `wave/config/changelog.d/<new-fragment>.json`
- Modify if needed: `docs/superpowers/plans/2026-04-09-task-query-help-plan.md`

- [ ] Run the targeted task-query test suite and a compile-level verification command relevant to the touched code:
  - `python3 scripts/assemble-changelog.py --fragments wave/config/changelog.d --output wave/config/changelog.json`
  - `python3 scripts/validate-changelog.py --fragments wave/config/changelog.d --changelog wave/config/changelog.json`
  - `sbt "testOnly org.waveprotocol.box.server.waveserver.TaskQueryNormalizerTest org.waveprotocol.box.server.waveserver.lucene9.Lucene9QueryModelTest org.waveprotocol.box.server.waveserver.lucene9.Lucene9QueryCompilerTest org.waveprotocol.box.server.waveserver.lucene9.Lucene9SearchProviderImplTest org.waveprotocol.box.server.waveserver.SimpleSearchProviderImplTest"`
  - `sbt "wave/compile" compileGwt`
- [ ] Add a changelog fragment for the user-visible search semantics/help update and re-assemble `wave/config/changelog.json`.
- [ ] Run `claude-review` against the implementation diff and address any actionable findings.
- [ ] Record plan path, verification commands, review outcome, and behavior notes in GitHub issue `#755`.
