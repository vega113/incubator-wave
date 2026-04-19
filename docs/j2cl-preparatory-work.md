# J2CL / GWT 3 Preparatory Work

Status: Current
Updated: 2026-04-19
Parent: [j2cl-gwt3-decision-memo.md](./j2cl-gwt3-decision-memo.md)

This document originally tracked the first two recommended follow-on tasks from
the March decision memo: module graph reduction and client dependency cleanup.
Those preparatory tasks have now moved materially farther than the older text
here implied, so this page should be read as a completed-prep summary plus an
explicit handoff to the current GitHub issue chain.

## Preparatory Work Already Landed

### Module graph reduction

The measured `.gwt.xml` baseline is now:

- `104` production modules under `wave/src/main/resources/`
- `25` test modules under `wave/src/test/resources/`
- `0` duplicate test modules under `wave/src/test/java/`
- `129` total `.gwt.xml` files

This confirms the old duplicate-test-module cleanup is already reflected in the
live branch and should no longer be described as an in-progress reduction from
`163` to `136`.

### Client dependency cleanup

The earlier `guava-gwt` cleanup is no longer pending:

- `guava-gwt` has already been removed from `build.sbt`
- the stale docs that described `guava-gwt` as still present are now historical
  context, not the current repo state

### Additional prerequisite cleanup now complete

The repo is also past several earlier preparatory blockers:

- gadget and htmltemplate client trees are already removed
- `WaveContext` already uses the shared
  `org.waveprotocol.wave.model.document.BlipReadStateMonitor`
- the remaining blocker story is now centered on sidecar build, transport,
  browser interop, UiBinder, and legacy GWT-only tests

### Explicit `GWTTestCase` split now committed

Issue `#898` has now turned the stale test-harness blocker into an explicit
map:

- the direct `GWTTestCase` baseline was reconciled to `21` in the live
  worktree
- `DelayedJobRegistry` and `UrlParameters` moved to plain JVM tests in `#898`
- the remaining direct surface is now `19`
- the browser-only/editor-family home is documented in
  [docs/j2cl-gwttestcase-verification-matrix.md](./j2cl-gwttestcase-verification-matrix.md)

## Preparatory Work Still Open

The following items remain open after the landed cleanup:

- no broad JsInterop / Elemental2 bridge seam yet
- the transport / websocket / generated JSO message stack is still GWT-specific
- UiBinder and `GWT.create(...)` are still widespread in the client UI
- the remaining browser-facing `GWTTestCase` suites still need a real post-GWT
  browser runner even though the JVM/browser split is now documented
- the sidecar still needs selected-wave rendering, route state, and a write
  path before root cutover is realistic

## Current Follow-On Issue Chain

The active GitHub-native sequence is now:

Completed staged foundation:

1. [#899](https://github.com/vega113/supawave/issues/899) Refresh the J2CL / GWT 3 baseline docs after the merged Phase 0 cleanup
2. [#900](https://github.com/vega113/supawave/issues/900) Stand up the isolated J2CL sidecar build and SBT entrypoints
3. [#903](https://github.com/vega113/supawave/issues/903) Make `wave/model` and `wave/concurrencycontrol` J2CL-safe pure logic
4. [#902](https://github.com/vega113/supawave/issues/902) Replace the JSO transport stack and GWT WebSocket shim with J2CL-friendly codecs
5. [#898](https://github.com/vega113/supawave/issues/898) Replace the remaining GWTTestCase debt with an explicit JVM/browser verification split
6. [#901](https://github.com/vega113/supawave/issues/901) Migrate the search results panel as the first J2CL UI vertical slice

Current pending sequence:

1. [#904](https://github.com/vega113/supawave/issues/904) Track the staged J2CL / GWT 3 migration from the merged sidecar/search baseline
2. [#919](https://github.com/vega113/supawave/issues/919) Refresh the J2CL tracker/docs after the merged search-sidecar slice
3. [#920](https://github.com/vega113/supawave/issues/920) Add a read-only selected-wave panel to the J2CL search sidecar
4. [#921](https://github.com/vega113/supawave/issues/921) Add sidecar route state and split-view navigation for the J2CL shell
5. [#922](https://github.com/vega113/supawave/issues/922) Add the first J2CL write-path pilot for create/reply/plain-text submit
6. [#923](https://github.com/vega113/supawave/issues/923) Add an opt-in root bootstrap flag for the J2CL client
7. [#924](https://github.com/vega113/supawave/issues/924) Cut over the default root route from GWT to J2CL
8. [#925](https://github.com/vega113/supawave/issues/925) Retire the legacy GWT client path and packaging steps

## Summary

The preparatory phase should now be described as “completed enough to enable
the first staged sidecar client, but not yet ready for root cutover,” not as if
the repo were still waiting on `guava-gwt` removal, duplicate-module cleanup,
or the first J2CL slice itself. The next execution work is issue-driven, not
discovery-driven.
