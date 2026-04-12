# Issue #836 Robot RPC Streaming Reply Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stop `/robot/rpc` from returning a successful `newBlipId` when the underlying reply-creation delta failed to commit, so streamed follow-on writes do not receive phantom reply ids.

**Architecture:** Keep the existing `blip.createChild` and `document.modify` server behavior, but change the Jakarta robot RPC servlet to capture delta-submission failures before serializing responses. If a write operation in the batch fails during commit, convert its JSON-RPC response into an error instead of returning the pre-submit success payload.

**Tech Stack:** Jakarta robot RPC servlet, WaveletProvider submit callbacks, JUnit 4, Mockito, sbt.

---

### Task 1: Add A Failing Servlet Regression Test

**Files:**
- Modify: `wave/src/test/java/org/waveprotocol/box/server/robots/dataapi/BaseApiServletScopeEnforcementTest.java`

- [ ] **Step 1: Add a regression test for failed delta submission**

Add a test that:
- submits one mutating operation through the Jakarta `BaseApiServlet`
- uses a custom `OperationService` that records a success response and a real delta in the context
- forces `waveletProvider.submitRequest(...)` to invoke `listener.onFailure("delta failed")`
- asserts the serialized response list contains an error response for that operation instead of the pre-submit success payload

- [ ] **Step 2: Run the focused test to verify it fails**

Run:

```bash
sbt "testOnly org.waveprotocol.box.server.robots.dataapi.BaseApiServletScopeEnforcementTest"
```

Expected: FAIL because the servlet currently returns success responses even when `submitRequest` reports failure.

### Task 2: Convert Submission Failures Into JSON-RPC Errors

**Files:**
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/dataapi/BaseApiServlet.java`

- [ ] **Step 1: Capture submit failures during response handling**

Update `handleResults(...)` so it uses a listener that records submission failures instead of only logging them.

- [ ] **Step 2: Rewrite mutating operation responses on submit failure**

When at least one delta submission fails:
- convert each non-error mutating operation response in the current request to `JsonRpcResponse.error(...)`
- keep pre-existing execution errors intact
- keep read-only operation responses unchanged

- [ ] **Step 3: Rerun the focused servlet test**

Run:

```bash
sbt "testOnly org.waveprotocol.box.server.robots.dataapi.BaseApiServletScopeEnforcementTest"
```

Expected: PASS.

### Task 3: Verify The Targeted Robot Streaming Surface

**Files:**
- Modify: `journal/local-verification/2026-04-12-issue-836-robot-rpc-streaming.md`

- [ ] **Step 1: Run the focused verification set**

Run:

```bash
sbt "testOnly org.waveprotocol.box.server.robots.dataapi.BaseApiServletScopeEnforcementTest"
sbt "testOnly org.waveprotocol.examples.robots.gptbot.SupaWaveApiClientTest"
```

Expected: PASS.

- [ ] **Step 2: Record the commands and outcomes**

Write the exact verification commands and results to:

```text
journal/local-verification/2026-04-12-issue-836-robot-rpc-streaming.md
```

- [ ] **Step 3: Commit**

```bash
git add \
  docs/superpowers/plans/2026-04-12-issue-836-robot-rpc-streaming-fix.md \
  wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/dataapi/BaseApiServlet.java \
  wave/src/test/java/org/waveprotocol/box/server/robots/dataapi/BaseApiServletScopeEnforcementTest.java \
  journal/local-verification/2026-04-12-issue-836-robot-rpc-streaming.md
git commit -m "fix(robot-rpc): fail create-child responses when commit fails"
```
