# Issue 979 Bootstrap Session ID Contract Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the misleading volatile `session.id` value from the J2CL `/bootstrap.json` contract while preserving legacy inline HTML bootstrap behavior for the #978 soak window.

**Architecture:** Keep `WaveClientServlet` and `HtmlRenderer` behavior unchanged for GWT and rollback-safe inline globals. In `J2clBootstrapServlet`, continue reusing `WaveClientServlet#buildSessionJson(...)` for domain/address/role/features parity, but strip `SessionConstants.ID_SEED` before writing `/bootstrap.json`. Update the contract docs, servlet docs, Java tests, Lit adapter/tests, and runbook so future J2CL/Lit clients cannot treat `/bootstrap.json.session.id` as correlated with the HTML `window.__session.id` or the HTTP session.

**Tech Stack:** Jakarta servlet overrides, shared Java contract constants, JUnit/Mockito tests through SBT, J2CL transport tests through SBT, Lit unit tests through the repo `j2clLitTest` SBT task, changelog fragment workflow.

---

## Decision

Issue #979 exists because `/bootstrap.json` and the rendered HTML page currently each call `WaveClientServlet#buildSessionJson(...)`, and that helper regenerates `SessionConstants.ID_SEED` on every call. Stabilizing that value at HTTP-session scope would be unsafe because GWT/StageTwo uses it as a client ID seed and two `/bootstrap.json` consumers in the same HTTP session could then share the same seed. Keeping the volatile field in `/bootstrap.json` with only documentation would leave the same future-client footgun.

The chosen contract is therefore:

- `/bootstrap.json.session` exposes the existing non-seed session fields produced by `WaveClientServlet`: `domain` and `role`, plus signed-in `address` and `features`.
- `/bootstrap.json.session` does not expose `id` / `SessionConstants.ID_SEED`.
- Legacy inline `window.__session` continues to expose its existing `id` seed during the #978 overlap window.
- New J2CL/Lit clients must tolerate older servers that still include `session.id` during a rolling deploy, but must not consume that field from `/bootstrap.json`.
- If future J2CL StageTwo parity needs a client-lifecycle ID seed, add a deliberate J2CL-owned seed contract instead of reusing `/bootstrap.json.session.id`.

## Files

- Modify: `wave/src/main/java/org/waveprotocol/box/common/J2clBootstrapContract.java`
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/J2clBootstrapServlet.java`
- Modify: `wave/src/jakarta-test/java/org/waveprotocol/box/server/rpc/J2clBootstrapServletTest.java`
- Modify: `wave/src/jakarta-test/java/org/waveprotocol/box/server/rpc/WaveClientServletJ2clRootShellTest.java`
- Modify: `j2cl/src/test/java/org/waveprotocol/box/j2cl/transport/SidecarTransportCodecTest.java`
- Modify: `j2cl/lit/src/input/json-shell-input.js`
- Modify: `j2cl/lit/test/json-shell-input.test.js`
- Modify: `docs/runbooks/j2cl-sidecar-testing.md`
- Add: `wave/config/changelog.d/2026-04-25-issue-979-bootstrap-session-contract.json`
- Add: `journal/local-verification/2026-04-25-issue-979-bootstrap-session-contract.md` (force-add because `journal/` is ignored)

Do not modify:

- `WaveClientServlet#getSessionJson(...)`; it remains the source of the legacy inline HTML seed.
- `HtmlRenderer` inline `var __session` / `var __websocket_address` emission; #978 owns removing that overlap after the soak window.
- `SidecarSessionBootstrap#fromRootHtml(...)`; #978 owns removing the legacy parser.
- `j2cl/lit/src/input/inline-shell-input.js` and `j2cl/lit/test/inline-shell-input.test.js`. The inline shell input feeds off the legacy `window.__session` global path that #978 owns. Its `idSeed` field will surface in the Task 3 `rg "\bidSeed\b" j2cl/lit` sweep — that is intentional and the verification journal must record it as out-of-scope (today the inline path keys off `session.idSeed`, not `session.id`, so no GWT/inline rollback consumer is affected).

## Task 1: Encode The Server Contract

**Files:**
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/J2clBootstrapServlet.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/common/J2clBootstrapContract.java`
- Modify: `wave/src/jakarta-test/java/org/waveprotocol/box/server/rpc/J2clBootstrapServletTest.java`
- Modify: `wave/src/jakarta-test/java/org/waveprotocol/box/server/rpc/WaveClientServletJ2clRootShellTest.java`

- [ ] **Step 1: Update the servlet test to fail on the current contract**

First confirm the signed-out shape from the current producer code before writing assertions:

```bash
nl -ba wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/WaveClientServlet.java | sed -n '467,502p'
```

Observed contract before this change: `buildSessionJson(...)` always emits `domain`, `role`, and the volatile `id` seed; it emits `address` and `features` only when a user address exists. Therefore after `J2clBootstrapServlet` strips `id`, signed-out `/bootstrap.json.session` should contain exactly `domain` and `role`.

Confirm the contract constants used by the tests exist:

```bash
rg -n "SESSION_(ADDRESS|DOMAIN|ROLE|FEATURES)" wave/src/main/java/org/waveprotocol/box/common/J2clBootstrapContract.java
rg -n 'ID_SEED = "id"' wave/src/main/java/org/waveprotocol/box/common/SessionConstants.java
```

Change `signedInRequestReturnsSessionAndSocketAndShell` so it asserts that `/bootstrap.json` omits the ID seed:

```java
assertFalse(session.has(SessionConstants.ID_SEED));
```

Keep explicit assertions for the retained keys in the same test:

```java
assertEquals("alice@example.com", session.getString(J2clBootstrapContract.SESSION_ADDRESS));
assertEquals("example.com", session.getString(J2clBootstrapContract.SESSION_DOMAIN));
assertEquals(HumanAccountData.ROLE_USER, session.getString(J2clBootstrapContract.SESSION_ROLE));
assertTrue(session.has(J2clBootstrapContract.SESSION_FEATURES));
assertEquals(
    Set.of(
        J2clBootstrapContract.SESSION_ADDRESS,
        J2clBootstrapContract.SESSION_DOMAIN,
        J2clBootstrapContract.SESSION_ROLE,
        J2clBootstrapContract.SESSION_FEATURES),
    session.keySet());
```

Add the import:

```java
import java.util.Set;
import org.waveprotocol.box.common.SessionConstants;
```

Also strengthen `signedOutRequestOmitsAddressAndFeatures`:

```java
assertEquals("example.com", session.getString(J2clBootstrapContract.SESSION_DOMAIN));
assertEquals(HumanAccountData.ROLE_USER, session.getString(J2clBootstrapContract.SESSION_ROLE));
assertFalse(session.has(SessionConstants.ID_SEED));
assertFalse(session.has(J2clBootstrapContract.SESSION_ADDRESS));
assertFalse(session.has(J2clBootstrapContract.SESSION_FEATURES));
assertEquals(
    Set.of(J2clBootstrapContract.SESSION_DOMAIN, J2clBootstrapContract.SESSION_ROLE),
    session.keySet());
```

Add a repeated-request regression that proves the formerly volatile seed stays absent across multiple `/bootstrap.json` calls on independent request mocks for the same signed-in user path:

Before adding the test, confirm `J2clBootstrapServletTest` already has `signedInRequest()` and use it. If the response boilerplate is duplicated, add only the small `renderBootstrapJson(...)` helper shown by this plan.

```java
@Test
public void repeatedBootstrapJsonResponsesDoNotExposeVolatileIdSeed() throws Exception {
  J2clBootstrapServlet servlet = createServlet(ParticipantId.ofUnsafe("alice@example.com"));

  JSONObject firstSession =
      renderBootstrapJson(servlet, signedInRequest())
          .getJSONObject(J2clBootstrapContract.KEY_SESSION);
  JSONObject secondSession =
      renderBootstrapJson(servlet, signedInRequest())
          .getJSONObject(J2clBootstrapContract.KEY_SESSION);

  assertFalse(firstSession.has(SessionConstants.ID_SEED));
  assertFalse(secondSession.has(SessionConstants.ID_SEED));
  assertEquals(firstSession.keySet(), secondSession.keySet());
  assertEquals(
      firstSession.getString(J2clBootstrapContract.SESSION_ADDRESS),
      secondSession.getString(J2clBootstrapContract.SESSION_ADDRESS));
  assertEquals(
      firstSession.getString(J2clBootstrapContract.SESSION_DOMAIN),
      secondSession.getString(J2clBootstrapContract.SESSION_DOMAIN));
  assertEquals(
      firstSession.getString(J2clBootstrapContract.SESSION_ROLE),
      secondSession.getString(J2clBootstrapContract.SESSION_ROLE));
  assertEquals(
      firstSession.getJSONArray(J2clBootstrapContract.SESSION_FEATURES).length(),
      secondSession.getJSONArray(J2clBootstrapContract.SESSION_FEATURES).length());
}
```

If adding this test would duplicate response-rendering boilerplate, extract a small private `renderBootstrapJson(J2clBootstrapServlet servlet, HttpServletRequest request)` helper inside `J2clBootstrapServletTest`.

In `signedInJ2clRootShellStillExposesLegacyBootstrapGlobals`, add a rollback-safety assertion that parses the inline `__session` assignment and confirms the seed remains available for older GWT/J2CL bundles. This requires two new imports in `WaveClientServletJ2clRootShellTest.java` (the file does not yet reference either type):

```java
import org.json.JSONObject;
import org.waveprotocol.box.common.SessionConstants;
```

Then add the assertion:

```java
int sessionStart = html.indexOf("var __session = ");
assertTrue(sessionStart >= 0);
int sessionEnd = html.indexOf('\n', sessionStart);
assertTrue(sessionEnd > sessionStart);
String inlineSession = html.substring(sessionStart, sessionEnd);
String sessionJson = inlineSession.substring("var __session = ".length(), inlineSession.length() - 1);
assertTrue(new JSONObject(sessionJson).has(SessionConstants.ID_SEED));
```

Run:

```bash
sbt -batch "testOnly org.waveprotocol.box.server.rpc.J2clBootstrapServletTest org.waveprotocol.box.server.rpc.WaveClientServletJ2clRootShellTest"
```

Expected: FAIL before implementation because the current servlet includes `session.id`.

- [ ] **Step 2: Strip the volatile ID seed in `J2clBootstrapServlet`**

Add:

```java
import org.waveprotocol.box.common.SessionConstants;
```

After `buildSessionJson(...)`, remove the seed from the fresh JSON object:

```java
JSONObject sessionJson = waveClientServlet.buildSessionJson(webSession);
// /bootstrap.json must not expose the volatile HTML client ID seed.
sessionJson.remove(SessionConstants.ID_SEED);
```

Keep `body.put(J2clBootstrapContract.KEY_SESSION, sessionJson);` unchanged.

- [ ] **Step 3: Update servlet and contract documentation**

In `J2clBootstrapServlet` class javadoc, replace the current per-call ID divergence paragraph with the new contract:

```java
 * <p>The session block is produced by {@link WaveClientServlet#buildSessionJson}
 * so the HTML and JSON surfaces cannot drift on role/feature/domain/address.
 * This endpoint intentionally removes {@link
 * org.waveprotocol.box.common.SessionConstants#ID_SEED}: the value is a
 * per-render client ID seed for the legacy HTML bootstrap, not an HTTP/auth
 * session identifier and not a cross-request correlation key. Future J2CL
 * clients that need an ID seed must use a dedicated J2CL-owned seed contract.
```

Before removing the contract constant, run a usage sweep:

```bash
rg -n "J2clBootstrapContract\\.SESSION_ID|\\bSESSION_ID\\b" .
```

Expected before edits: only the contract declaration and the existing servlet test assertion reference the constant. If any other runtime reference appears, update the plan before implementation. This sweep is advisory; the focused SBT compile/test command is the primary guarantee that no Java static import or unqualified reference remains.

In `J2clBootstrapContract`, remove `SESSION_ID` and update the schema example so the `session` object lists only:

```java
 *   "session": { "domain": "...", "address": "...", "role": "...", "features": [...] },
```

Add a short paragraph after the schema:

```java
 * <p>The J2CL JSON contract intentionally omits {@code SessionConstants.ID_SEED}
 * even though the rollback-safe inline HTML globals still expose it during the
 * inline-HTML rollback overlap.
 * The remaining session fields keep the same signed-in/signed-out presence rules
 * as {@code WaveClientServlet#buildSessionJson(WebSession)}.
```

- [ ] **Step 4: Verify the server contract test passes**

Run:

```bash
sbt -batch "testOnly org.waveprotocol.box.server.rpc.J2clBootstrapServletTest org.waveprotocol.box.server.rpc.WaveClientServletJ2clRootShellTest"
```

Expected: the focused servlet/root-shell tests pass with no direct Maven invocation. If the SBT resource step fails because `wave/config/changelog.json` is missing, run `python3 scripts/assemble-changelog.py` and rerun the same SBT command; the full changelog validation gate runs after the fragment is added in Task 4.

## Task 2: Keep Client Decoders Tolerant But Non-Dependent

**Files:**
- Modify: `j2cl/src/test/java/org/waveprotocol/box/j2cl/transport/SidecarTransportCodecTest.java`
- Modify: `j2cl/lit/src/input/json-shell-input.js`
- Modify: `j2cl/lit/test/json-shell-input.test.js`

- [ ] **Step 1: Update Java bootstrap JSON fixtures**

Scope note: only the `extractSessionBootstrapFromBootstrapJson` JSON fixture loses its `"id":"seed"` entry. The neighbouring `extractSessionBootstrapRejectsMissingAddress` test (and any other `fromRootHtml` test in this file) intentionally keeps `"id":"abc"` because that fixture is parsing the legacy inline `__session={...}` HTML format that #978 still owns. Do not touch those.

In `extractSessionBootstrapFromBootstrapJson`, remove `"id":"seed"` from the JSON fixture.

Add a focused compatibility test proving Java tolerates older servers that still include the legacy seed:

```java
@Test
public void bootstrapJsonIgnoresLegacySessionIdSeed() {
  String json =
      "{\"session\":{\"domain\":\"example.com\",\"address\":\"user@example.com\","
          + "\"id\":\"legacy-seed\",\"future\":\"ignored\"},"
          + "\"socket\":{\"address\":\"socket.example.test:7443\",\"token\":\"future-933-token\"}}";

  SidecarSessionBootstrap bootstrap = SidecarSessionBootstrap.fromBootstrapJson(json);

  Assert.assertEquals("user@example.com", bootstrap.getAddress());
  Assert.assertEquals("socket.example.test:7443", bootstrap.getWebSocketAddress());
}
```

Add a reflection guard that documents Java `SidecarSessionBootstrap` has no public seed-bearing API to populate from JSON:

```java
@Test
public void bootstrapValueObjectDoesNotExposeJsonSessionSeed() {
  for (java.lang.reflect.Method method : SidecarSessionBootstrap.class.getMethods()) {
    if (method.getReturnType().equals(Void.TYPE)) {
      continue;
    }
    String name = method.getName().toLowerCase(java.util.Locale.ROOT);
    Assert.assertFalse(
        "Unexpected seed accessor: " + method.getName(),
        name.startsWith("get") && name.contains("seed"));
    Assert.assertFalse(
        "Unexpected session id accessor: " + method.getName(), name.equals("getsessionid"));
  }
}
```

Run:

```bash
sbt -batch "testOnly org.waveprotocol.box.j2cl.transport.SidecarTransportCodecTest"
```

Expected: PASS; Java already ignores unknown session keys.

Contract-source note: the Java servlet test is the producer-side source of truth that `/bootstrap.json` omits `id`; the Lit tests only prove consumer behavior for omitted and legacy fields.

- [ ] **Step 2: Stop Lit JSON shell input from consuming `session.id`**

Confirm no Lit shell runtime code depends on a non-empty JSON `idSeed` before changing the adapter:

```bash
rg -n "\\bidSeed\\b" j2cl/src j2cl/lit
```

Expected: uses are limited to shell input snapshot shape, adapters, and tests; no Java J2CL code, Lit element, or controller depends on a non-empty JSON seed. Record this in the verification journal.

Change `json-shell-input.js` from:

```javascript
idSeed:
  typeof session.id === "string"
    ? session.id
    : typeof session.idSeed === "string"
      ? session.idSeed
      : "",
```

to:

```javascript
// /bootstrap.json intentionally does not provide a client ID seed.
idSeed: "",
```

This keeps the current JSON bootstrap contract from becoming an accidental seed source. A future J2CL-owned seed contract should add its own explicit field and tests when it is designed.

- [ ] **Step 3: Update Lit tests for the omitted seed**

Change `json-shell-input.test.js` so the main fixture omits `session.id` and asserts:

```javascript
expect(snap.idSeed).to.equal("");
```

Add a regression test:

```javascript
it("ignores legacy session.id from bootstrap JSON", () => {
  window.__bootstrap = {
    session: {
      address: "a@b.c",
      role: "owner",
      domain: "b.c",
      id: "legacy-seed",
      features: []
    },
    socket: {
      address: "ws.example:443"
    }
  };

  expect(createJsonShellInput(window).read().idSeed).to.equal("");
});
```

Test isolation requirement: keep the existing `beforeEach` that resets `window.__bootstrap` and do not share the legacy-id fixture between tests.

Verify the test file uses the existing Mocha/Open WC style and cleanup hook before editing:

```bash
rg -n "beforeEach|expect\\(.*\\)\\.to\\.equal" j2cl/lit/test/json-shell-input.test.js
```

Run:

```bash
sbt -batch j2clLitTest
```

Expected: PASS.

## Task 3: Update Operator Documentation

**Files:**
- Modify: `docs/runbooks/j2cl-sidecar-testing.md`

- [ ] **Step 1: Rewrite the bootstrap contract field list**

Replace the current `session.id` bullet with:

```markdown
- `/bootstrap.json` intentionally omits `SessionConstants.ID_SEED`; that volatile HTML `session.id` seed remains only in the legacy inline `window.__session` bootstrap during the #978 overlap window
```

- [ ] **Step 2: Update the rollback note**

Replace the final sentence that says the divergence is tracked in #979 with:

```markdown
The former `session.id` divergence was closed by issue `#979`: `/bootstrap.json` no longer publishes the volatile HTML client ID seed, so new J2CL/Lit clients must not use it as a session or correlation identifier.
```

- [ ] **Step 3: Confirm no docs still advertise `/bootstrap.json.session.id`**

Run:

```bash
rg -n "session\\.id|SESSION_ID|ID_SEED|idSeed" docs/runbooks/j2cl-sidecar-testing.md wave/src/main/java/org/waveprotocol/box/common/J2clBootstrapContract.java wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/J2clBootstrapServlet.java j2cl/lit/src/input/json-shell-input.js
```

Expected: only intentional explanatory references remain; no expected-field documentation remains for `/bootstrap.json.session.id`.

The runbook may mention former `session.id` behavior only in past tense. It must not say or imply that current `/bootstrap.json` responses include the field.

Record this scan in the verification journal so the PR evidence captures the contract-doc regression check.

## Task 4: Changelog And Verification Journal

**Files:**
- Add: `wave/config/changelog.d/2026-04-25-issue-979-bootstrap-session-contract.json`
- Add: `journal/local-verification/2026-04-25-issue-979-bootstrap-session-contract.md`

- [ ] **Step 1: Add the changelog fragment**

Check the current fragment schema before creating the file:

```bash
ls -t wave/config/changelog.d/*.json | head -n 3
sed -n '1,80p' "$(ls -t wave/config/changelog.d/*.json | head -n 1)"
rg -n '"releaseId": "2026-04-25-issue-979-bootstrap-session-contract"' wave/config/changelog.d || true
```

Confirm the fragment uses the existing `releaseId` / `sections[].type` shape, that `fix` is an accepted section type, and that `2026-04-25-issue-979-bootstrap-session-contract` is not already used.

Create:

```json
{
  "releaseId": "2026-04-25-issue-979-bootstrap-session-contract",
  "date": "2026-04-25",
  "version": "Unreleased",
  "title": "J2CL bootstrap JSON no longer exposes the volatile HTML client ID seed",
  "summary": "The /bootstrap.json contract now omits the per-render SessionConstants.ID_SEED value that remains in legacy inline HTML bootstrap globals for rollback compatibility. This prevents J2CL/Lit clients from treating the value as an HTTP session identifier or as correlated with the HTML bootstrap page.",
  "sections": [
    {
      "type": "fix",
      "items": [
        "Removed the volatile session.id client ID seed and its misleading bootstrap-contract constant from the J2CL /bootstrap.json response while preserving the legacy inline HTML bootstrap seed during the rollout overlap window"
      ]
    }
  ]
}
```

- [ ] **Step 2: Add the verification journal**

Create `journal/local-verification/2026-04-25-issue-979-bootstrap-session-contract.md` with:

```markdown
# Issue 979 Bootstrap Session Contract Verification

Date: 2026-04-25
Worktree: /Users/vega/devroot/worktrees/issue-979-bootstrap-session-contract
Branch: codex/issue-979-bootstrap-session-contract

## Decision

/bootstrap.json omits SessionConstants.ID_SEED. Legacy inline HTML globals keep their existing seed until #978 removes the overlap path.

## Commands

- python3 scripts/assemble-changelog.py && python3 scripts/validate-changelog.py
- sbt -batch "testOnly org.waveprotocol.box.server.rpc.J2clBootstrapServletTest org.waveprotocol.box.server.rpc.WaveClientServletJ2clRootShellTest org.waveprotocol.box.j2cl.transport.SidecarTransportCodecTest"
- sbt -batch j2clLitTest
- sbt -batch j2clSearchTest j2clProductionBuild j2clLitTest j2clLitBuild
- rg -n "\\bidSeed\\b" j2cl/src j2cl/lit
- rg -n "session\\.id|SESSION_ID|ID_SEED|idSeed" docs/runbooks/j2cl-sidecar-testing.md wave/src/main/java/org/waveprotocol/box/common/J2clBootstrapContract.java wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/J2clBootstrapServlet.java j2cl/lit/src/input/json-shell-input.js
- git diff --check (whitespace gate)

## Results

Record exact pass/fail output summaries and exit codes before PR. Do not leave this section as a stub.
Use the form `exit=0` or `exit=<nonzero>` for each command summary.
```

Stage it with `git add -f journal/local-verification/2026-04-25-issue-979-bootstrap-session-contract.md` because `journal/` is ignored.

- [ ] **Step 3: Run final local verification**

Run:

```bash
python3 scripts/assemble-changelog.py && python3 scripts/validate-changelog.py
sbt -batch "testOnly org.waveprotocol.box.server.rpc.J2clBootstrapServletTest org.waveprotocol.box.server.rpc.WaveClientServletJ2clRootShellTest org.waveprotocol.box.j2cl.transport.SidecarTransportCodecTest"
sbt -batch j2clLitTest
sbt -batch j2clSearchTest j2clProductionBuild j2clLitTest j2clLitBuild
rg -n "\\bidSeed\\b" j2cl/src j2cl/lit
rg -n "session\\.id|SESSION_ID|ID_SEED|idSeed" docs/runbooks/j2cl-sidecar-testing.md wave/src/main/java/org/waveprotocol/box/common/J2clBootstrapContract.java wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/J2clBootstrapServlet.java j2cl/lit/src/input/json-shell-input.js
git diff --check  # whitespace gate
```

Expected: all commands pass. If SBT fails because `wave/config/changelog.json` is missing, run the changelog assemble/validate command first and rerun the SBT command.

The focused SBT command also confirms `J2clBootstrapContract.SESSION_ID` has no remaining Java references after removal; any missed reference should fail compilation.

## Task 5: Review, Issue Update, PR, And Monitor

**Files:**
- No additional file changes unless review finds an issue.

- [ ] **Step 1: Self-review**

Run:

```bash
git status --short
git diff --check
rg -n "session\\.id|SESSION_ID|ID_SEED|idSeed" docs/runbooks/j2cl-sidecar-testing.md wave/src/main/java/org/waveprotocol/box/common/J2clBootstrapContract.java wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/J2clBootstrapServlet.java j2cl/lit/src/input/json-shell-input.js j2cl/lit/test/json-shell-input.test.js wave/src/jakarta-test/java/org/waveprotocol/box/server/rpc/J2clBootstrapServletTest.java
```

Confirm:

- `/bootstrap.json` docs and tests do not advertise `session.id` as a response field.
- Legacy inline HTML behavior is not edited.
- Java bootstrap decoding remains tolerant of older `session.id` payloads.
- Java `SidecarSessionBootstrap` still has no seed/session-id accessor that could surface `/bootstrap.json.session.id`.
- Lit JSON shell input ignores legacy `session.id`.
- No direct command-line Maven invocation is used; SBT is the orchestrator and may delegate to a Maven wrapper internally (e.g. `j2clSearchTest`).

Before invoking the implementation review helper, replace `REVIEW_TEST_RESULTS` with the exact command summary copied from the verification journal.

- [ ] **Step 2: Claude Opus implementation review loop**

Run the review helper from this worktree with a focused diff:

```bash
export REVIEW_TASK="Issue #979 J2CL bootstrap session.id contract"
export REVIEW_GOAL="Ensure /bootstrap.json no longer exposes or consumes the volatile HTML client ID seed while preserving rollback-safe inline globals."
export REVIEW_ACCEPTANCE=$'- /bootstrap.json omits SessionConstants.ID_SEED\\n- Legacy inline window.__session emission is unchanged\\n- Java and Lit clients tolerate older JSON but do not depend on session.id\\n- Docs/tests make the contract unambiguous\\n- SBT-only verification is recorded'
export REVIEW_RUNTIME="Jakarta servlet + J2CL Java + Lit shell"
export REVIEW_RISKY="Bootstrap contract compatibility, GWT rollback overlap, client ID seed semantics"
export REVIEW_TEST_COMMANDS="python3 scripts/assemble-changelog.py && python3 scripts/validate-changelog.py; sbt -batch \"testOnly org.waveprotocol.box.server.rpc.J2clBootstrapServletTest org.waveprotocol.box.server.rpc.WaveClientServletJ2clRootShellTest org.waveprotocol.box.j2cl.transport.SidecarTransportCodecTest\"; sbt -batch j2clLitTest; sbt -batch j2clSearchTest j2clProductionBuild j2clLitTest j2clLitBuild; contract-doc rg scan; git diff --check"
export REVIEW_TEST_RESULTS="$(sed -n '/## Results/,$p' journal/local-verification/2026-04-25-issue-979-bootstrap-session-contract.md)"
export REVIEW_DIFF_SPEC="$(git merge-base origin/main HEAD)...HEAD"
export REVIEW_PLATFORM=claude
export REVIEW_MODEL=claude-opus-4-7
/Users/vega/.codex/skills/public/claude-review/scripts/review_task.sh
```

Address all blockers, important concerns, and required follow-ups. Repeat until the final review has no blockers, no important concerns, no required follow-ups, and no unresolved coverage gaps.

- [ ] **Step 3: Commit and issue update**

Commit with:

```bash
git add docs/superpowers/plans/2026-04-25-issue-979-bootstrap-session-contract.md
git add wave/src/main/java/org/waveprotocol/box/common/J2clBootstrapContract.java
git add wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/J2clBootstrapServlet.java
git add wave/src/jakarta-test/java/org/waveprotocol/box/server/rpc/J2clBootstrapServletTest.java
git add wave/src/jakarta-test/java/org/waveprotocol/box/server/rpc/WaveClientServletJ2clRootShellTest.java
git add j2cl/src/test/java/org/waveprotocol/box/j2cl/transport/SidecarTransportCodecTest.java
git add j2cl/lit/src/input/json-shell-input.js
git add j2cl/lit/test/json-shell-input.test.js
git add docs/runbooks/j2cl-sidecar-testing.md
git add wave/config/changelog.d/2026-04-25-issue-979-bootstrap-session-contract.json
git add -f journal/local-verification/2026-04-25-issue-979-bootstrap-session-contract.md
git commit -m "fix(j2cl): omit volatile session id from bootstrap json"
```

Post an issue comment with worktree, branch, plan path, commit SHA, verification commands/results, Claude review result, and PR URL after creation.

- [ ] **Step 4: Open PR and monitor to merge**

Create the PR body from the decision and verification evidence:

```bash
cat >/tmp/issue-979-pr-body.md <<'EOF'
Closes #979
Updates #904

## Summary
- Omit the volatile `SessionConstants.ID_SEED` value from `/bootstrap.json.session`.
- Preserve legacy inline `window.__session.id` during the #978 rollback overlap.
- Keep Java and Lit clients tolerant of older JSON payloads while preventing new JSON consumers from using `session.id`.

## Verification
- python3 scripts/assemble-changelog.py && python3 scripts/validate-changelog.py
- sbt -batch "testOnly org.waveprotocol.box.server.rpc.J2clBootstrapServletTest org.waveprotocol.box.server.rpc.WaveClientServletJ2clRootShellTest org.waveprotocol.box.j2cl.transport.SidecarTransportCodecTest"
- sbt -batch j2clLitTest
- sbt -batch j2clSearchTest j2clProductionBuild j2clLitTest j2clLitBuild
- contract-doc rg scan recorded in journal
- git diff --check

## Review
- Self-review complete.
- Claude Opus implementation review loop complete with no blockers or required follow-ups.
EOF
```

Push and open a PR:

```bash
gh repo view --json nameWithOwner
git push -u origin codex/issue-979-bootstrap-session-contract
PR=$(gh pr create --repo vega113/supawave --base main --head codex/issue-979-bootstrap-session-contract --title "Omit volatile session id from J2CL bootstrap JSON" --body-file /tmp/issue-979-pr-body.md --json number -q .number)
gh pr merge --auto --squash --repo vega113/supawave "$PR"
```

Expected: `gh repo view --json nameWithOwner` reports `vega113/supawave`. If it does not, stop and update the PR commands to the actual repository before pushing.

Monitor until merged:

```bash
gh pr view "$PR" --repo vega113/supawave --json state,mergeable,reviewDecision,statusCheckRollup,isDraft,autoMergeRequest
gh api graphql -f query='query($owner:String!,$repo:String!,$number:Int!){repository(owner:$owner,name:$repo){pullRequest(number:$number){reviewThreads(first:100){nodes{id,isResolved}}}}}' -f owner=vega113 -f repo=supawave -F number="$PR"
gh pr checks "$PR" --repo vega113/supawave
```

If `autoMergeRequest` is `null` after `gh pr merge --auto --squash`, rerun `gh pr merge --auto --squash --repo vega113/supawave "$PR"` and recheck. Continue polling until `state` is `MERGED`; do not declare the lane complete on a one-shot mergeable result.

Success criteria:

- PR merged.
- GraphQL unresolved review threads are `0`.
- Required checks pass or auto-merge completes.
- #979 closed.
- #904 updated with the PR number, merge commit, and next unblocked lane decision.

## Self-Review

- Spec coverage: The plan decides the contract (`session.id` omitted from `/bootstrap.json`), preserves current GWT/rollback inline behavior, updates docs/tests, and keeps #978 as the owner of removing legacy inline globals.
- Red-flag scan: No prohibited plan filler or journal template filler remains.
- Type consistency: Uses `SessionConstants.ID_SEED` for the Java key, keeps `J2clBootstrapContract.KEY_SESSION` unchanged, removes `J2clBootstrapContract.SESSION_ID`, and keeps Lit `idSeed` as the snapshot property while no longer mapping it from JSON `session.id`.
- Verification discipline: The plan uses SBT tasks only for Java/J2CL/Lit verification and explicitly excludes direct Maven commands.
- Rollback safety: `HtmlRenderer`, `WaveClientServlet#getSessionJson(...)`, and `SidecarSessionBootstrap#fromRootHtml(...)` are out of scope and unchanged.
