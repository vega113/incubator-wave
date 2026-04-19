# Issue #898 GWTTestCase Verification Split Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the remaining `GWTTestCase` debt with an explicit split between plain JVM tests and documented browser-only verification so later J2CL UI migration work has a defined test home.

**Architecture:** Treat issue `#898` as test-harness triage, not a UI migration. First reconcile the live inventory in this worktree, then move only clearly non-DOM cases off `GWTTestCase`, and finally commit a dedicated browser-only verification matrix for the suites that still depend on DOM, JSNI, widgets, or browser event semantics.

**Tech Stack:** Java, legacy GWT test harnesses, JUnit 3/4, SBT, `rg`, existing worktree boot/smoke scripts, manual browser verification runbooks, existing J2CL sidecar entrypoints.

---

## 1. Goal / Root Cause

Issue `#898` exists because the repository still carries browser-era `GWTTestCase`
coverage even though the staged J2CL plan now expects a deliberate JVM/browser
split instead of a single legacy hosted-test bucket.

Root cause:

- the migration docs still describe `GWTTestCase` debt as a blocker, but do not
  yet give each remaining suite an explicit target home
- several direct `GWTTestCase` suites are pure or near-pure logic and should be
  plain JVM tests instead of staying behind the legacy GWT harness
- several editor, wavepanel, JS overlay, and widget suites still depend on DOM,
  browser events, JSNI, or GWT widget behavior and therefore need a documented
  browser-only home instead of an implied future cleanup
- the current docs are already slightly stale: the live worktree has `21` Java
  files with `GWTTestCase`, while `docs/j2cl-gwt3-decision-memo.md` and related
  docs still describe `24`

The narrow outcome for this issue is not “migrate the editor” or “finish J2CL.”
It is “make the remaining test debt explicit and reduce the low-risk legacy
surface now.”

## 2. Scope And Non-Goals

### In Scope

- inventory the live `GWTTestCase` surface in this worktree and reconcile doc
  counts with the measured tree
- classify each remaining direct `GWTTestCase` suite and each affected
  base-class family into one of:
  - convert to plain JVM tests now
  - move to an explicit browser-runner / J2CL-facing verification bucket
  - retain temporarily with a written reason
- convert the low-risk, non-DOM cases off `GWTTestCase`
- add a committed browser-only verification matrix document for the remaining
  suites
- update the J2CL migration docs so issue `#898` leaves a stable test-home map
  for later issues

### Explicit Non-Goals

- no pure model or concurrency-control migration already tracked by `#903`
- no full editor migration
- no full wavepanel migration
- no attempt to move every remaining browser-only suite into a real J2CL test
  runner in this issue
- no production app behavior change beyond any minimal test-only support code
  needed to let low-risk suites run on the JVM

## 3. Exact Files Likely To Change

### Inventory And Migration Docs

- `docs/j2cl-gwt3-inventory.md`
- `docs/j2cl-gwt3-decision-memo.md`
- `docs/j2cl-preparatory-work.md`
- `docs/superpowers/plans/j2cl-full-migration-plan.md`
- `docs/runbooks/browser-verification.md`
- `docs/runbooks/change-type-verification-matrix.md`
- `docs/j2cl-gwttestcase-verification-matrix.md` (new browser-only matrix output)

### Direct `GWTTestCase` Suites To Classify

- `wave/src/test/java/org/waveprotocol/wave/client/common/util/FastQueueGwtTest.java`
- `wave/src/test/java/org/waveprotocol/wave/client/editor/content/EditorGwtTestCase.java`
- `wave/src/test/java/org/waveprotocol/wave/client/editor/content/img/ImgDoodadGwtTest.java`
- `wave/src/test/java/org/waveprotocol/wave/client/editor/event/EditorEventHandlerGwtTest.java`
- `wave/src/test/java/org/waveprotocol/wave/client/editor/extract/PasteExtractorGwtTest.java`
- `wave/src/test/java/org/waveprotocol/wave/client/editor/extract/PasteFormatRendererGwtTest.java`
- `wave/src/test/java/org/waveprotocol/wave/client/editor/extract/RepairerGwtTest.java`
- `wave/src/test/java/org/waveprotocol/wave/client/editor/extract/TypingExtractorGwtTest.java`
- `wave/src/test/java/org/waveprotocol/wave/client/editor/gwt/GwtRenderingMutationHandlerGwtTest.java`
- `wave/src/test/java/org/waveprotocol/wave/client/editor/impl/NodeManagerGwtTest.java`
- `wave/src/test/java/org/waveprotocol/wave/client/editor/integration/CleanupGwtTest.java`
- `wave/src/test/java/org/waveprotocol/wave/client/editor/integration/TestBase.java`
- `wave/src/test/java/org/waveprotocol/wave/client/editor/keys/KeyBindingRegistryIntegrationGwtTest.java`
- `wave/src/test/java/org/waveprotocol/wave/client/editor/selection/content/AggressiveSelectionHelperGwtTest.java`
- `wave/src/test/java/org/waveprotocol/wave/client/scheduler/BrowserBackedSchedulerGwtTest.java`
- `wave/src/test/java/org/waveprotocol/wave/client/scheduler/DelayedJobRegistryGwtTest.java`
- `wave/src/test/java/org/waveprotocol/wave/client/util/ExtendedJSObjectGwtTest.java`
- `wave/src/test/java/org/waveprotocol/wave/client/util/UrlParametersGwtTest.java`
- `wave/src/test/java/org/waveprotocol/wave/client/util/WrappedJSObjectGwtTest.java`
- `wave/src/test/java/org/waveprotocol/wave/client/wavepanel/block/xml/XmlStructureGwtTest.java`
- `wave/src/test/java/org/waveprotocol/wave/client/wavepanel/event/EventDispatcherPanelGwtTest.java`

### Base-Class Families And Descendant Suites Likely To Move With The Classification

- `wave/src/test/java/org/waveprotocol/wave/client/editor/content/ContentTestBase.java`
- `wave/src/test/java/org/waveprotocol/wave/client/editor/content/NodeEventRouterGwtTest.java`
- `wave/src/test/java/org/waveprotocol/wave/client/editor/content/LazyPersistentContentDocumentGwtTest.java`
- `wave/src/test/java/org/waveprotocol/wave/client/editor/content/DomGwtTest.java`
- `wave/src/test/java/org/waveprotocol/wave/client/editor/content/ContentElementGwtTest.java`
- `wave/src/test/java/org/waveprotocol/wave/client/editor/content/ContentTextNodeGwtTest.java`
- `wave/src/test/java/org/waveprotocol/wave/client/editor/integration/ElementTestBase.java`
- `wave/src/test/java/org/waveprotocol/wave/client/editor/integration/OperationGwtTest.java`
- `wave/src/test/java/org/waveprotocol/wave/client/editor/integration/MobileWebkitFocusGwtTest.java`
- `wave/src/test/java/org/waveprotocol/wave/client/editor/integration/MobileImeFlushGwtTest.java`
- `wave/src/test/java/org/waveprotocol/wave/client/editor/integration/ParagraphGwtTest.java`

### Legacy GWT Test Resource Modules Likely To Change

- `wave/src/test/resources/org/waveprotocol/wave/client/common/util/tests.gwt.xml`
- `wave/src/test/resources/org/waveprotocol/wave/client/editor/content/Tests.gwt.xml`
- `wave/src/test/resources/org/waveprotocol/wave/client/editor/event/Tests.gwt.xml`
- `wave/src/test/resources/org/waveprotocol/wave/client/editor/extract/Tests.gwt.xml`
- `wave/src/test/resources/org/waveprotocol/wave/client/editor/gwt/Tests.gwt.xml`
- `wave/src/test/resources/org/waveprotocol/wave/client/editor/impl/Tests.gwt.xml`
- `wave/src/test/resources/org/waveprotocol/wave/client/editor/integration/Tests.gwt.xml`
- `wave/src/test/resources/org/waveprotocol/wave/client/editor/keys/Tests.gwt.xml`
- `wave/src/test/resources/org/waveprotocol/wave/client/editor/selection/Tests.gwt.xml`
- `wave/src/test/resources/org/waveprotocol/wave/client/scheduler/tests.gwt.xml`
- `wave/src/test/resources/org/waveprotocol/wave/client/util/tests.gwt.xml`
- `wave/src/test/resources/org/waveprotocol/wave/client/wavepanel/block/Tests.gwt.xml`
- `wave/src/test/resources/org/waveprotocol/wave/client/wavepanel/event/Tests.gwt.xml`

### Most Likely Low-Risk JVM Conversion Targets

- `wave/src/test/java/org/waveprotocol/wave/client/common/util/FastQueueGwtTest.java`
- `wave/src/test/java/org/waveprotocol/wave/client/scheduler/DelayedJobRegistryGwtTest.java`
- `wave/src/test/java/org/waveprotocol/wave/client/util/UrlParametersGwtTest.java` if the test stays on the constructor/query-parser path and any native browser lookup remains outside the JVM target

### Likely Deferred Browser-Only Buckets

- JS overlay / JSNI utilities:
  - `wave/src/test/java/org/waveprotocol/wave/client/util/ExtendedJSObjectGwtTest.java`
  - `wave/src/test/java/org/waveprotocol/wave/client/util/WrappedJSObjectGwtTest.java`
- Wavepanel DOM structure / event routing:
  - `wave/src/test/java/org/waveprotocol/wave/client/wavepanel/block/xml/XmlStructureGwtTest.java`
  - `wave/src/test/java/org/waveprotocol/wave/client/wavepanel/event/EventDispatcherPanelGwtTest.java`
- Editor DOM / selection / widget / IME families:
  - all editor files listed above under the direct and descendant editor buckets

### Likely Temporary Retain-With-Reason Bucket

- `wave/src/test/java/org/waveprotocol/wave/client/scheduler/BrowserBackedSchedulerGwtTest.java`
  because it is logic-heavy but still tied to the browser-backed scheduler seam
  and should only move if the remaining GWT widget/timing assumptions can be
  isolated without widening scope

## 4. Concrete Task Breakdown

### Task 1: Reconcile The Live Inventory Before Any Conversion

**Files:**
- Inspect and update: `docs/j2cl-gwt3-inventory.md`
- Inspect and update: `docs/j2cl-gwt3-decision-memo.md`
- Inspect and update: `docs/j2cl-preparatory-work.md`

- [ ] Measure the live direct `GWTTestCase` surface in this worktree instead of
      relying on older counts copied forward from prior docs.
- [ ] Record both:
      - the direct `GWTTestCase` Java suites and abstract bases
      - the descendant suites that inherit through `EditorGwtTestCase` or
        `TestBase`
- [ ] Update the J2CL inventory docs so `#898` starts from a reproducible
      count and not a stale one.

### Task 2: Publish The Classification Table

**Files:**
- Create: `docs/j2cl-gwttestcase-verification-matrix.md`
- Update: `docs/j2cl-gwt3-inventory.md`
- Update as needed: `docs/superpowers/plans/j2cl-full-migration-plan.md`

- [ ] Build a flat classification table with one row per direct suite and one
      explicit row for each editor base-chain family.
- [ ] Each row must include:
      - file path
      - current harness path
      - category: `plain JVM now`, `browser/J2CL-facing later`, or
        `temporary retain`
      - written reason
      - expected follow-on issue or migration phase
- [ ] Make the table the canonical answer for “where does this legacy test
      live after `#898`?”

### Task 3: Convert The Low-Risk Non-DOM Cases Off `GWTTestCase`

**Files:**
- Modify or replace: `wave/src/test/java/org/waveprotocol/wave/client/common/util/FastQueueGwtTest.java`
- Modify or replace: `wave/src/test/java/org/waveprotocol/wave/client/scheduler/DelayedJobRegistryGwtTest.java`
- Modify or replace: `wave/src/test/java/org/waveprotocol/wave/client/util/UrlParametersGwtTest.java` if it stays narrow
- Modify or delete matching `.gwt.xml` modules if those suites no longer need them:
  - `wave/src/test/resources/org/waveprotocol/wave/client/common/util/tests.gwt.xml`
  - `wave/src/test/resources/org/waveprotocol/wave/client/scheduler/tests.gwt.xml`
  - `wave/src/test/resources/org/waveprotocol/wave/client/util/tests.gwt.xml`

- [ ] Convert only cases that do not require real DOM nodes, JSNI execution,
      GWT widget attachment, browser events, or browser-global query-string
      access.
- [ ] Keep the replacement narrow:
      - same package
      - same assertions
      - plain JVM `TestCase` / JUnit execution
      - no speculative cleanup in unrelated client code
- [ ] If `UrlParametersGwtTest` requires touching production code to separate
      constructor parsing from browser-global lookup, keep that refactor minimal
      and leave JS overlay behavior out of scope.

### Task 4: Document The Deferred Browser-Only Home

**Files:**
- Create: `docs/j2cl-gwttestcase-verification-matrix.md`
- Update as needed: `docs/runbooks/browser-verification.md`
- Update as needed: `docs/runbooks/change-type-verification-matrix.md`

- [ ] Put all JS overlay, editor DOM, wavepanel DOM/event, widget-rendering,
      popup, selection, IME, and browser-scheduler seams in the browser-only
      bucket.
- [ ] For each deferred bucket, document:
      - why plain JVM execution is insufficient
      - what narrow browser behavior must be proven
      - the future home: browser-runner / J2CL-facing verification path
      - the nearest follow-on issue or phase (`#901`, `#904`, later editor DOM
        work, or later wavepanel migration)
- [ ] Use the existing runbooks as the execution baseline rather than inventing
      a new browser framework in this issue.

### Task 5: Leave Temporary Retains Explicit, Small, And Auditable

**Files:**
- Update classification rows for any suite not converted yet, especially:
  - `wave/src/test/java/org/waveprotocol/wave/client/scheduler/BrowserBackedSchedulerGwtTest.java`

- [ ] Any suite that remains on `GWTTestCase` after this issue must have a
      written reason in the committed matrix.
- [ ] The allowed temporary reasons are narrow:
      - browser timer / widget integration still leaks into the seam
      - browser event semantics still define correctness
      - JSNI / `JavaScriptObject` behavior is the thing under test
- [ ] “Did not get to it” is not an acceptable retain reason.

## 5. Exact Verification Commands

Run these from `/Users/vega/devroot/worktrees/issue-898-gwttest-split`.

### Inventory And Count Gates

```bash
printf 'direct GWTTestCase java files: '
rg -l 'extends GWTTestCase' wave/src/test/java | wc -l

printf 'all Java files mentioning GWTTestCase: '
rg -l 'GWTTestCase' wave/src/test/java | wc -l

printf 'editor/test-base descendants: '
rg -n 'extends (EditorGwtTestCase|ContentTestBase|ElementTestBase|TestBase)' \
  wave/src/test/java/org/waveprotocol/wave/client | wc -l

rg -l 'extends GWTTestCase' wave/src/test/java | sort

find wave/src/test/resources -name '*.gwt.xml' | sort
```

Expected result:

- the first command reflects the current direct-suite baseline before edits and
  drops after low-risk conversions land
- the second and third commands provide the classification input for direct
  suites plus inherited editor harness debt
- the sorted file lists are copied into the issue notes or matrix doc while
  classification is being verified

### Scope Guard For Low-Risk JVM Conversions

```bash
rg -n 'com\.google\.gwt\.dom|com\.google\.gwt\.user|JavaScriptObject|native .*/\*' \
  wave/src/test/java/org/waveprotocol/wave/client/common/util/FastQueueGwtTest.java \
  wave/src/test/java/org/waveprotocol/wave/client/scheduler/DelayedJobRegistryGwtTest.java \
  wave/src/test/java/org/waveprotocol/wave/client/util/UrlParametersGwtTest.java
```

Expected result:

- `FastQueueGwtTest.java` and `DelayedJobRegistryGwtTest.java` should stay clear
  of DOM/widget/JSNI dependencies
- if `UrlParametersGwtTest.java` trips a browser-only dependency after the
  planned narrow refactor is examined, move it out of the low-risk conversion
  bucket and document the reason instead of forcing it

### Targeted JVM Test Runs After Conversion

```bash
sbt -batch \
  "testOnly org.waveprotocol.wave.client.common.util.FastQueueTest \
  org.waveprotocol.wave.client.scheduler.DelayedJobRegistryTest"
```

If `UrlParametersGwtTest` is converted in-scope, rerun with:

```bash
sbt -batch \
  "testOnly org.waveprotocol.wave.client.common.util.FastQueueTest \
  org.waveprotocol.wave.client.scheduler.DelayedJobRegistryTest \
  org.waveprotocol.wave.client.util.UrlParametersTest"
```

Expected result: the converted suites pass on the plain JVM without invoking
`GWTTestCase`.

### Compile Gates

```bash
sbt -batch compile
sbt -batch Test/compile
```

If any `.gwt.xml` module wiring is removed or changed, also run:

```bash
sbt -batch Universal/stage
```

Expected result: compile succeeds, test compile succeeds, and staging still
builds if test-module wiring changed.

### Required Local Server / Browser Sanity Before PR

Even though this issue is mostly test-harness and docs work, the repo rules
still require a local lane sanity pass before PR. Use the worktree lifecycle
runbook and record the exact commands printed by `worktree-boot.sh`.

```bash
bash scripts/worktree-boot.sh --port 9900
PORT=9900 JAVA_OPTS='...' bash scripts/wave-smoke.sh start
PORT=9900 bash scripts/wave-smoke.sh check
PORT=9900 bash scripts/wave-smoke.sh stop
```

If the implementation touched any `.gwt.xml`, client packaging path, or GWT
build wiring, add the narrow browser pass required by the packaging/build row
of `docs/runbooks/change-type-verification-matrix.md`:

```text
Open http://localhost:9900/
Confirm the staged home or sign-in page loads
Confirm the served client asset path still resolves (for example the page loads
its normal web client bootstrap without a missing-asset error)
```

Record whether the matrix required the browser pass and what exact route and
result were checked.

## 6. Acceptance Criteria

- the repo has a committed issue-specific matrix document that names the
  remaining legacy suites and assigns each one to `plain JVM now`,
  `browser/J2CL-facing later`, or `temporary retain`
- the J2CL inventory / decision docs no longer rely on stale `GWTTestCase`
  counts for this worktree
- at least the clearly low-risk non-DOM suites are no longer implemented as
  `GWTTestCase`
- any direct suite left on `GWTTestCase` has a written reason in the committed
  matrix
- editor, wavepanel, JS overlay, and widget-driven suites are explicitly marked
  as browser-only and tied to a future verification home instead of vague
  “later cleanup”
- targeted JVM tests, compile gates, and required local smoke/browser sanity
  are recorded in the issue traceability notes before PR

## 7. Issue / PR Traceability Notes

- Worktree: `/Users/vega/devroot/worktrees/issue-898-gwttest-split`
- Branch: `issue-898-gwttest-split`
- Plan path: `docs/superpowers/plans/2026-04-19-issue-898-gwttest-split.md`
- Keep GitHub issue comments current with:
  - the reconciled direct `GWTTestCase` count and the descendant-harness count
  - the exact classification outputs or matrix path
  - which suites were actually converted in this issue
  - exact `sbt` and worktree smoke commands plus results
  - whether browser verification was required by the matrix
  - the PR URL once opened
- Keep the PR body short and derived from the issue comment:
  - reduced low-risk `GWTTestCase` surface
  - committed browser-only matrix path
  - remaining temporary retains and reasons
