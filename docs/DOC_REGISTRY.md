# Doc Registry

This file lists every documentation path that requires the standard metadata
header block. The `scripts/check-doc-freshness.sh` guardrail reads this
registry and fails CI when any listed doc is missing or has incomplete metadata.

## Required metadata header

Every covered doc must have these four fields at the very top of the file,
before the first `#` heading:

```
Status: <value>
Owner: <value>
Updated: <YYYY-MM-DD>
Review cadence: <value>
```

**Status values**: `Canonical`, `Current`, `In Progress`, `Draft`, `Archive`.
**Review cadence values**: `monthly`, `quarterly`, `on-change`.

## How to add a new covered doc

1. Add the doc's repo-relative path to the list below.
2. Add the four metadata fields at the top of the doc.
3. Run `bash scripts/check-doc-freshness.sh` to confirm it passes.
4. Commit both changes together.

## Covered docs

<!-- One path per line. Lines starting with # or empty lines are ignored. -->

docs/runbooks/README.md
docs/runbooks/browser-verification.md
docs/runbooks/change-type-verification-matrix.md
docs/runbooks/mongo-migrations.md
docs/runbooks/worktree-diagnostics.md
docs/runbooks/worktree-lane-lifecycle.md
docs/README.md
docs/DEV_SETUP.md
docs/BUILDING-sbt.md
docs/SMOKE_TESTS.md
docs/current-state.md
docs/github-issues.md
docs/agents/README.md
docs/architecture/README.md
docs/deployment/README.md
