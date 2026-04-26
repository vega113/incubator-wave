# Issue 979 Bootstrap Session Contract Verification

Date: 2026-04-25
Worktree: /Users/vega/devroot/worktrees/issue-979-bootstrap-session-contract
Branch: codex/issue-979-bootstrap-session-contract

## Decision

/bootstrap.json omits SessionConstants.ID_SEED. Legacy inline HTML globals keep their existing seed until #978 removes the overlap path.

## Commands

- python3 scripts/assemble-changelog.py && python3 scripts/validate-changelog.py
- sbt -batch "jakartaTest:testOnly org.waveprotocol.box.server.rpc.J2clBootstrapServletTest org.waveprotocol.box.server.rpc.WaveClientServletJ2clRootShellTest"
- sbt -batch j2clSearchTest
- sbt -batch j2clLitTest
- sbt -batch j2clSearchTest j2clProductionBuild j2clLitTest j2clLitBuild
- rg -n "\bidSeed\b" j2cl/src j2cl/lit
- rg -n "session\.id|SESSION_ID|ID_SEED|idSeed" docs/runbooks/j2cl-sidecar-testing.md wave/src/main/java/org/waveprotocol/box/common/J2clBootstrapContract.java wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/J2clBootstrapServlet.java j2cl/lit/src/input/json-shell-input.js
- git diff --check (whitespace gate)

## Notes On SBT Routing

The plan's sample command used `testOnly ... org.waveprotocol.box.j2cl.transport.SidecarTransportCodecTest`. That test is owned by the J2CL Maven wrapper (j2cl/pom.xml, search-sidecar profile), not the SBT JUnit harness. It is exercised here through `sbt j2clSearchTest`, which delegates to the Maven wrapper internally — still SBT-orchestrated, no direct command-line Maven invocation. Surefire report at `j2cl/target/surefire-reports/TEST-org.waveprotocol.box.j2cl.transport.SidecarTransportCodecTest.xml` is the per-test evidence (36 tests, 0 failures including the new `bootstrapJsonIgnoresLegacySessionIdSeed` and `bootstrapValueObjectDoesNotExposeJsonSessionSeed`).

The Jakarta JUnit suites (`J2clBootstrapServletTest`, `WaveClientServletJ2clRootShellTest`) live under the `JakartaTest` SBT config, so they are run via `sbt jakartaTest:testOnly ...` rather than the default `Test / testOnly` (which has no sources).

## Pre-existing Unrelated Failure

`WaveClientServletJ2clRootShellTest.j2clRootViewPreservesCurrentRouteStateInSignedOutChrome` fails on `origin/main` at HEAD (94e3a5025) prior to any #979 edits. Confirmed by stashing the working tree and rerunning the same focused command; the failure is independent of this change and out of scope.

## Out-Of-Scope idSeed Mention

`rg "\bidSeed\b" j2cl/src j2cl/lit` still surfaces `j2cl/lit/src/input/inline-shell-input.js` and its `inline-shell-input.test.js`. Per the plan and the "Do not modify" list, the inline shell input feeds off the legacy `window.__session` global path that issue #978 owns. The inline path keys off `session.idSeed`, not `session.id`, so no GWT/inline rollback consumer is affected by this change.

## Results

- `python3 scripts/assemble-changelog.py && python3 scripts/validate-changelog.py` — exit=0; assembled 259 entries -> wave/config/changelog.json; validated 259 entries.
- `sbt -batch "jakartaTest:testOnly org.waveprotocol.box.server.rpc.J2clBootstrapServletTest org.waveprotocol.box.server.rpc.WaveClientServletJ2clRootShellTest"` — exit=1 with the only failure being the pre-existing `j2clRootViewPreservesCurrentRouteStateInSignedOutChrome` baseline failure on origin/main; the 6/6 J2clBootstrapServletTest tests (including the new `signedInRequestReturnsSessionAndSocketAndShell`, `signedOutRequestOmitsAddressAndFeatures`, `repeatedBootstrapJsonResponsesDoNotExposeVolatileIdSeed`) and 15/16 root-shell tests (including the new rollback-safety assertion in `signedInJ2clRootShellStillExposesLegacyBootstrapGlobals`) pass.
- `sbt -batch j2clSearchTest` — exit=0; surefire `TEST-org.waveprotocol.box.j2cl.transport.SidecarTransportCodecTest.xml` reports `tests="36" errors="0" failures="0"`.
- `sbt -batch j2clLitTest` — exit=0; "Chromium: 23/23 test files | 111 passed, 0 failed".
- `sbt -batch j2clSearchTest j2clProductionBuild j2clLitTest j2clLitBuild` — exit=0; per-task results recorded; benign Vertispan DiskCache shutdown noise per #1027/#1032 is acceptable when the final SBT exit is 0 and every requested task succeeds.
- `rg -n "\bidSeed\b" j2cl/src j2cl/lit` — exit=0; matches limited to `json-shell-input.js` (assigned ""), `lit-shell-input.js` (snapshot type/default), inline-shell-input adapter+test (out-of-scope per #978), and json-shell-input test assertions.
- `rg -n "session\.id|SESSION_ID|ID_SEED|idSeed" docs/runbooks/j2cl-sidecar-testing.md wave/src/main/java/org/waveprotocol/box/common/J2clBootstrapContract.java wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/J2clBootstrapServlet.java j2cl/lit/src/input/json-shell-input.js` — exit=0; remaining hits are intentional explanatory references documenting the omission. No documentation advertises `/bootstrap.json.session.id` as a current response field.
- `git diff --check` — exit=0; no whitespace errors.
