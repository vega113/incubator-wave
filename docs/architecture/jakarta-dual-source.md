# Jakarta Dual-Source Architecture

Status: Current
Updated: 2026-04-12
Owner: Project Maintainers

## Goal

Explain how the live Jakarta runtime is selected from two source trees after
issue `#714` removed the old same-path shadow copies.

## Source Trees

Apache Wave keeps two Java source trees in the `wave` module:

- `wave/src/main/java/`
  - the long-lived main tree
  - contains shared code plus a small set of legacy main-only classes that are
    still excluded from the Jakarta/SBT compile surface
- `wave/src/jakarta-overrides/java/`
  - Jakarta EE 10 runtime classes and replacements for code that moved from
    `javax.*` to `jakarta.*`

The repo still uses two source roots, but it no longer keeps same-path
duplicates across them. Runtime classes that used to be shadowed by curated
exact excludes now live in a single authoritative file, usually under
`wave/src/jakarta-overrides/java/`.

## How The Build Selects The Runtime Copy

`build.sbt` still adds both source trees to
`Compile / unmanagedSourceDirectories`, then filters
`Compile / unmanagedSources` so the Jakarta build can:

The selection logic does three important things:

1. Includes `wave/src/main/java/` for the general codebase.
2. Includes `wave/src/jakarta-overrides/java/` for Jakarta runtime classes.
3. Applies only the remaining main-only legacy compile skips and directory-level
   filters that are still required for the Jakarta/SBT build.

The curated exact same-path exclude lists are now intentionally empty. If a
future task adds a Jakarta replacement that reintroduces a duplicate class
path, update `build.sbt` deliberately instead of assuming the old shadow-copy
model still exists.

## Runtime-Facing Areas That Usually Live In Overrides

The most common Jakarta-owned surfaces are now:

- server bootstrap and module wiring
- servlet registration and HTTP entrypoints
- WebSocket endpoint plumbing
- authentication and session classes
- robot and Data API entrypoints
- security and timing filters
- Jakarta-specific helpers

The active runtime seam is easiest to verify by checking the Jakarta paths
first, especially under:

- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/`
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/`
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/authentication/`
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/`

## Editing Rules

Use this workflow when touching runtime code:

1. Search both source trees for the class.
2. If the class exists in `src/jakarta-overrides/java`, treat that file as the
   runtime source of truth.
3. If the class exists only in `src/main/java`, verify whether it is shared
   code or part of the remaining main-only compile skips before moving it.
4. If you add a Jakarta replacement for a class that already exists in
   `src/main/java`, either move to a single authoritative file or update
   `build.sbt` intentionally for the new duplicate path.
5. Prefer new Jakarta integration coverage in `wave/src/jakarta-test/java` for
   runtime-facing Jakarta changes. Some existing tests in this directory are
   historical workarounds that still exercise `javax` implementations; verify
   a test's intent before moving or converting it, such as
   `wave/src/jakarta-test/java/org/waveprotocol/box/server/stat/StatuszServletConfigTest.java`.

Editing only the main-tree copy of a class that already has a Jakarta override
will still miss the live server behavior, but same-path duplicates should now
be treated as an exception that needs explicit build ownership instead of a
normal steady state.

## Relationship To Other Docs

- Use [runtime-entrypoints.md](runtime-entrypoints.md) for the live bootstrap
  and servlet seams.
- Use [dev-persistence-topology.md](dev-persistence-topology.md) for the safe
  local storage defaults that the Jakarta runtime boots with.
- Use [../jetty-migration.md](../jetty-migration.md) for the historical
  migration ledger and test history.
