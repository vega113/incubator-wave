# J2CL / GWT 3 Decision Memo

Status: Current
Updated: 2026-04-19
Owner: Project Maintainers
Task: `#919`

## Decision Summary

Decision: do not start a full-application J2CL / GWT 3 migration yet.

Reason:

- the current client still depends heavily on JSNI and `JavaScriptObject`
- UiBinder and `GWT.create(...)` remain central to UI composition
- the GWT client build is still an isolated legacy toolchain in `build.sbt`
- the remaining browser-facing test harness still carries legacy
  `GWTTestCase` debt even though `#898` now makes the JVM/browser split
  explicit
- there is still no broad existing JsInterop / Elemental2 bridge layer
- the J2CL sidecar now exists, but it is still only a partial parallel client

The important update is not the decision itself; it is the baseline. The repo
is now better prepared than the March snapshot implied because several
prerequisite cleanups are already complete, but it is still not at a safe
full-app migration starting point.

## Evidence Basis

See [docs/j2cl-gwt3-inventory.md](./j2cl-gwt3-inventory.md).

Current short version:

- `129` `.gwt.xml` files
- `84` `GWT.create(...)` callsites
- `114` JSNI / `JavaScriptObject`-heavy files
- `238` JSNI native methods
- `27` UiBinder-related Java files
- `23` `.ui.xml` templates
- `19` remaining direct `GWTTestCase` Java files after `#898`
- `21` was the reconciled direct starting baseline for `#898`
- `11` inherited editor/test-base descendants still need a browser-facing home
- no broad JsInterop / Elemental2 bridge layer
- the J2CL sidecar build now exists
- the first J2CL UI slice now exists on `/j2cl-search/index.html`
- `guava-gwt` already removed
- gadget/htmltemplate client cleanup already landed
- `WaveContext` already uses the shared `BlipReadStateMonitor` contract

See
[docs/j2cl-gwttestcase-verification-matrix.md](./j2cl-gwttestcase-verification-matrix.md)
for the suite-by-suite test-home map.

## Go / No-Go

- Full-app migration now: `No-go`
- Staged migration continuation: `Go`
- Isolated J2CL build scaffold: `Complete`
- Narrow transport replacement path: `Complete`
- First UI vertical slice after scaffold and transport: `Complete`
- Read-only selected-wave sidecar slice: `Go`
- Route-state / write-path / root-bootstrap follow-on work: `Go`

## Narrowest Viable Next Wave

The next wave should still avoid trying to move the whole app.

The narrowest viable sequence is now:

1. refresh the stale tracker/docs to the post-`#901` baseline
2. add a read-only selected-wave panel to the sidecar
3. add route/history state for the J2CL sidecar shell
4. prove the smallest write path on top of that shell
5. add a reversible opt-in root bootstrap before any default cutover

Only after that should the project revisit a broader compiler/runtime switch.

## Active Follow-On Issues

These are the current dependency-ordered follow-on issues:

1. [#904](https://github.com/vega113/supawave/issues/904) Track the staged J2CL / GWT 3 migration from the merged sidecar/search baseline
2. [#919](https://github.com/vega113/supawave/issues/919) Refresh the J2CL tracker/docs after the merged search-sidecar slice
3. [#920](https://github.com/vega113/supawave/issues/920) Add a read-only selected-wave panel to the J2CL search sidecar
4. [#921](https://github.com/vega113/supawave/issues/921) Add sidecar route state and split-view navigation for the J2CL shell
5. [#922](https://github.com/vega113/supawave/issues/922) Add the first J2CL write-path pilot for create/reply/plain-text submit
6. [#923](https://github.com/vega113/supawave/issues/923) Add an opt-in root bootstrap flag for the J2CL client
7. [#924](https://github.com/vega113/supawave/issues/924) Cut over the default root route from GWT to J2CL
8. [#925](https://github.com/vega113/supawave/issues/925) Retire the legacy GWT client path and packaging steps

## Explicit Non-Starter Moves

These are still not recommended as the next step:

- trying to flip the entire client from GWT 2.x to J2CL in one pass
- rewriting `WebClient`, `StageThree`, the editor internals, and the wavepanel
  simultaneously
- pretending the transport rewrite can be skipped while the client still runs
  through generated JSO message families and the GWT websocket wrapper
- deferring the test-harness replacement story until after UI migration has
  already started

## What Would Change This Decision

The decision can be revisited after these conditions are met:

- the sidecar can render a selected wave, not only a search list
- sidecar route/history state is durable enough for real navigation
- the first write path has shipped behind the staged migration path
- an opt-in J2CL root bootstrap exists and is locally verified
- the remaining GWT-only test debt keeps an explicit post-GWT home through the
  cutover

## Recommended Status

Treat the original “inventory and memo exist” milestone as complete, and treat
the current status as “staged follow-on execution underway.”

The migration itself should remain a later follow-on effort, not an implied
next commit.
