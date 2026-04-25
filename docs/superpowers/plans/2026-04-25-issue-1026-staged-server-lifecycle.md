# Issue 1026 Staged Server Lifecycle Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the staged worktree server started by `scripts/wave-smoke.sh start` remain alive after readiness so `scripts/wave-smoke.sh check` and browser verification can run against the same stable process until `scripts/wave-smoke.sh stop` is called.

**Architecture:** Keep the lifecycle fix in the active Jakarta runtime seam, not in a wrapper-only workaround. `ServerMain.run(...)` currently returns immediately after `ServerRpcProvider.startWebSocketServer(...)`, while `ServerRpcProvider` starts Jetty and exposes a stop method but no blocking join. Add a narrow blocking method on the provider, call it from `ServerMain` after successful startup, and keep `wave-smoke.sh` as the owner of detached start/stop behavior for worktree and CI smoke flows.

**Tech Stack:** Java 17, Jetty 12 EE10, Jakarta servlet override source set, SBT (`Universal/stage`, `smokeInstalled`), Bash smoke helpers, Python script tests.

---

## Issue Context

- GitHub issue: `vega113/supawave#1026`
- Observed failure: `scripts/wave-smoke.sh start` can print `PROBE_HTTP=200` and `READY`, but the Java process exits before a later `scripts/wave-smoke.sh check` or browser pass connects.
- Acceptance criteria:
  - `scripts/wave-smoke.sh start` leaves a stable process listening on the selected `PORT` until `scripts/wave-smoke.sh stop`.
  - `scripts/wave-smoke.sh check` passes after `start` without a race-style immediate probe.
  - Browser verification runbooks can rely on the started worktree server for J2CL root checks.
  - CI and non-interactive behavior stays bounded and does not hang indefinitely.
  - No orphaned Java processes are left behind.

## Current Code Evidence

- `scripts/wave-smoke.sh` chooses the staged SBT distribution when `target/universal/stage` exists, sets `PID_FILE="$INSTALL_DIR/wave_server.pid"`, and validates `PORT` at lines 17-27.
- `scripts/wave-smoke.sh start` runs `nohup ./bin/wave ... &`, waits for root readiness, then rewrites the PID file to the listener PID discovered from the selected port at lines 125-145.
- `scripts/wave-smoke.sh check` probes `/`, `/healthz`, `/?view=landing`, `/?view=j2cl-root`, `/j2cl/index.html`, `/j2cl-search/sidecar/j2cl-sidecar.js`, and `/webclient/webclient.nocache.js` at lines 180-260.
- `scripts/wave-smoke.sh stop` collects both port listener PIDs and the PID file, sends SIGTERM, escalates to SIGKILL within `STOP_TIMEOUT`, and removes the PID file at lines 262-340.
- `scripts/worktree-boot.sh` refuses the primary checkout, stages with `sbt -batch Universal/stage`, writes the selected port into staged `config/application.conf`, and prints exact `start`, `check`, `diagnostics`, and `stop` commands at lines 95-207.
- Active runtime is `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/ServerMain.java`; `ServerMain.run(...)` calls `server.startWebSocketServer(injector)` at lines 150-151 and then returns.
- `ServerRpcProvider.startWebSocketServer(...)` creates the Jetty server and calls `httpServer.start()` at lines 402-405 and 743-744, but the provider currently only exposes `stopServer()` at lines 976-981.

## Non-Goals

- Do not change J2CL root routing, GWT default root behavior, smoke endpoint expectations, or staged asset packaging.
- Do not replace SBT with Maven or add a new browser framework.
- Do not make `wave-smoke.sh stop` kill broad Java process groups or unrelated worktree lanes.
- Do not alter production deploy scripts unless implementation evidence later shows they call a different lifecycle path that bypasses `ServerMain`.

## File Ownership

- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/ServerRpcProvider.java`
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/ServerMain.java`
- Create: `scripts/tests/test_wave_smoke_lifecycle.py`
- Modify only if needed for documented command output: `scripts/wave-smoke.sh`
- Modify only if the implementation changes the worktree command shape: `docs/SMOKE_TESTS.md`
- Modify only if the implementation changes browser verification instructions: `docs/runbooks/browser-verification.md`
- Do not modify: legacy `wave/src/main/java/org/waveprotocol/box/server/ServerMain.java` unless direct SBT evidence shows a runtime path still uses it. Current repo rule says the Jakarta override is runtime-active.

## Task 1: Add A Focused Script Regression For Detached Lifecycle

**Files:**
- Create: `scripts/tests/test_wave_smoke_lifecycle.py`

- [ ] **Step 1: Write a Python test that guards `wave-smoke.sh` detached lifecycle ownership**

Create `scripts/tests/test_wave_smoke_lifecycle.py` with this test harness:

```python
import os
import signal
import socket
import subprocess
import sys
import textwrap
import time
from pathlib import Path
from urllib.parse import urlparse


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts" / "wave-smoke.sh"


def free_port() -> int:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        sock.bind(("127.0.0.1", 0))
        return sock.getsockname()[1]


def write_fake_install(tmp_path: Path, server_source: str) -> Path:
    install = tmp_path / "stage"
    bin_dir = install / "bin"
    bin_dir.mkdir(parents=True)
    server = install / "fake_wave_server.py"
    server.write_text(server_source)
    launcher = bin_dir / "wave"
    launcher.write_text(
        "#!/usr/bin/env bash\n"
        "set -euo pipefail\n"
        f"exec {sys.executable} {server}\n"
    )
    launcher.chmod(0o755)
    return install


def run_smoke(tmp_path: Path, install: Path, port: int, command: str) -> subprocess.CompletedProcess[str]:
    env = os.environ.copy()
    env.update(
        {
            "INSTALL_DIR": str(install),
            "PORT": str(port),
            "STOP_TIMEOUT": "5",
        }
    )
    return subprocess.run(
        ["bash", str(SCRIPT), command],
        cwd=ROOT,
        env=env,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        timeout=20,
        check=False,
    )


def test_start_check_stop_keeps_fake_staged_server_alive(tmp_path: Path) -> None:
    port = free_port()
    install = write_fake_install(
        tmp_path,
        textwrap.dedent(
            """
            import http.server
            import os
            import signal
            import socketserver
            from urllib.parse import urlparse

            port = int(os.environ["PORT"])

            class Handler(http.server.BaseHTTPRequestHandler):
                def do_GET(self):
                    body = b""
                    status = 200
                    parsed = urlparse(self.path)
                    route = parsed.path
                    query = parsed.query
                    if route == "/" and query == "":
                        body = b"<script src='webclient/webclient.nocache.js'></script>"
                    elif route == "/healthz":
                        body = b"ok"
                    elif route == "/" and query == "view=landing":
                        body = b"landing"
                    elif route == "/" and query == "view=j2cl-root":
                        body = b"<div data-j2cl-root-shell></div>"
                    elif route in (
                        "/j2cl/index.html",
                        "/j2cl-search/sidecar/j2cl-sidecar.js",
                        "/webclient/webclient.nocache.js",
                    ):
                        body = b"asset"
                    else:
                        status = 404
                    self.send_response(status)
                    self.end_headers()
                    self.wfile.write(body)

                def log_message(self, format, *args):
                    return

            class ReusableTCPServer(socketserver.TCPServer):
                allow_reuse_address = True

            with ReusableTCPServer(("127.0.0.1", port), Handler) as httpd:
                signal.signal(signal.SIGTERM, lambda *_: httpd.shutdown())
                httpd.serve_forever()
            """
        ),
    )

    start = run_smoke(tmp_path, install, port, "start")
    try:
        assert start.returncode == 0, start.stdout + start.stderr
        assert "READY" in start.stdout
        pid_file = install / "wave_server.pid"
        assert pid_file.exists()
        recorded_pid = int(pid_file.read_text().strip())
        os.kill(recorded_pid, 0)

        time.sleep(1.0)
        check = run_smoke(tmp_path, install, port, "check")
        assert check.returncode == 0, check.stdout + check.stderr
        assert "ROOT_STATUS=200" in check.stdout
        assert "J2CL_ROOT_STATUS=200" in check.stdout
        assert "WEBCLIENT_STATUS=200" in check.stdout
    finally:
        stop = run_smoke(tmp_path, install, port, "stop")
        assert stop.returncode == 0, stop.stdout + stop.stderr
```

- [ ] **Step 2: Verify `PORT` propagation and run the new script regression**

Before running the test, confirm `PORT` remains exported to the staged launcher:

```bash
rg -n 'PORT=|export PORT|./bin/wave' scripts/wave-smoke.sh
```

Expected: `wave-smoke.sh` receives `PORT` from the command environment (`PORT=<port> bash scripts/wave-smoke.sh start`) and does not unset it before launching `./bin/wave`. If implementation evidence shows the fake server cannot see `PORT`, update the fake launcher to pass the port as an explicit argument instead of relying on inherited environment.

Run:

```bash
python3 -m pytest scripts/tests/test_wave_smoke_lifecycle.py
```

Expected before implementation: this should pass if `wave-smoke.sh` already keeps and stops an ordinary long-lived staged process correctly. This test is a forward guard for script-owned PID, port, and stop behavior; it is not the reproducer for the Java `ServerMain.run(...)` early-return bug. The real Java lifecycle fix is exercised by `timeout 600 sbt -batch smokeInstalled` and the delayed worktree `start` plus `check` sequence in Task 4. The fake request matchers use `urlparse(...)` so they match the exact routes emitted by `wave-smoke.sh check` without depending on raw query-string formatting. Name the test file and test function as script lifecycle guards; do not describe them as reproducing `#1026`.

- [ ] **Step 3: Record what this test does and does not prove**

Add a short comment at the top of `scripts/tests/test_wave_smoke_lifecycle.py`:

```python
# This script-level regression proves wave-smoke.sh preserves and stops a
# normal long-lived staged process. It does not reproduce ServerMain.run()
# returning early; the Java join behavior is covered by the SBT staged smoke
# verification because it requires the real native-packager launcher.
```

## Task 2: Add A Blocking Join To The Jakarta Provider

**Files:**
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/ServerRpcProvider.java`

- [ ] **Step 1: Inspect existing shutdown wiring, then add a provider method that blocks until Jetty stops**

Before editing, inspect whether the provider or Jetty path already installs JVM shutdown handling:

```bash
rg -n 'Shutdown|shutdown|addShutdownHook|stopAtShutdown|setStopAtShutdown|join\\(' wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/ServerRpcProvider.java wave/src/jakarta-overrides/java/org/waveprotocol/box/server/ServerMain.java
```

Expected: record whether SIGTERM is expected to call `stopServer()` or Jetty stop automatically. If no JVM shutdown hook exists, keep Task 4 Step 6 as the acceptance gate for whether a narrow shutdown-hook addition is needed before PR.

Insert this method near `stopServer()`:

```java
    /**
     * Blocks the launcher thread until the Jakarta HTTP server is stopped.
     *
     * <p>The staged native-packager launcher expects the main Java process to
     * stay alive after Jetty readiness. Without a join, ServerMain can return
     * immediately after startup and the worktree smoke process disappears before
     * browser verification can reuse it. This method is called from the launcher
     * thread immediately after a successful startWebSocketServer(...) call; it is
     * not intended as a general concurrent lifecycle API.</p>
     */
    public void joinServer() throws InterruptedException {
        if (httpServer == null) {
            throw new IllegalStateException("Jakarta server not started; cannot join");
        }
        httpServer.join();
    }
```

- [ ] **Step 2: Confirm the provider field and Jetty method signature while compiling**

Run:

```bash
sbt -batch wave/compile
```

Expected: compilation passes. During the edit, confirm the field is still named `httpServer` and that Jetty 12 EE10 `Server.join()` has the checked-exception signature used above. If compilation shows a different signature, use the actual Jetty signature from the compiler error, update the `ServerMain` catch block to match the actual checked exception set, and keep the method narrow.

- [ ] **Step 3: Do not add a brittle provider unit test unless the constructor is already cheap to instantiate**

The fast unit seam would be a test that starts `ServerRpcProvider` on an ephemeral port, runs `joinServer()` in a worker thread, calls `stopServer()`, and asserts the join returns. Do this only if `ServerRpcProvider` can be constructed with existing lightweight test helpers. If construction requires the full Guice server graph, keep the Java lifecycle proof in `timeout 600 sbt -batch smokeInstalled` plus the delayed worktree smoke sequence; that path exercises the real native-packager launcher and the real `ServerMain.run(...)` behavior that failed in `#1026`.

## Task 3: Make ServerMain Block After Startup

**Files:**
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/ServerMain.java`

- [ ] **Step 1: Call the provider join immediately after successful startup**

Change the end of `run(...)` from:

```java
    LOG.info("Starting server");
    server.startWebSocketServer(injector);
```

to:

```java
    LOG.info("Starting server");
    server.startWebSocketServer(injector);
    try {
      server.joinServer();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LOG.warning("Server main thread interrupted; stopping Jakarta server", e);
      try {
        server.stopServer();
      } catch (java.io.IOException stopError) {
        LOG.warning("Error stopping Jakarta server after interrupted main thread", stopError);
      }
    }
```

- [ ] **Step 2: Confirm the logger overload and preserve fatal startup behavior**

Existing code in this file already uses `LOG.warning("message", e)`, so keep that logging style unless compilation proves the local `Log` API has changed. Do not catch `RuntimeException` from `server.startWebSocketServer(injector)`. Startup failures must still escape to `main(...)` and log as fatal, matching current behavior.

- [ ] **Step 3: Compile the active runtime entrypoint**

Run:

```bash
sbt -batch wave/compile
```

Expected: compilation passes.

- [ ] **Step 4: Confirm the staged distribution is using the Jakarta override entrypoint**

Run after staging:

```bash
sbt -batch Universal/stage
jar tf target/universal/stage/lib/*.jar | grep 'org/waveprotocol/box/server/ServerMain.class' | head
```

Expected: the staged distribution contains one `org/waveprotocol/box/server/ServerMain.class` entry. If an executor suspects source-selection drift, run `sbt -batch 'show Compile / unmanagedSources' | grep 'org/waveprotocol/box/server/ServerMain.java'` and confirm the selected source is under `wave/src/jakarta-overrides/java/`, not the legacy main tree.

## Task 4: Verify SBT And Worktree Lifecycle Behavior

**Files:**
- No production file changes expected in this task.
- Update `journal/local-verification/2026-04-25-issue-1026-staged-server-lifecycle.md` only if the implementation lane needs a local evidence record before PR.

- [ ] **Step 1: Run script tests**

Run:

```bash
python3 -m pytest scripts/tests/test_wave_smoke_lifecycle.py
```

Expected: the new script lifecycle regression passes.

- [ ] **Step 2: Run SBT compile**

Run:

```bash
sbt -batch wave/compile
```

Expected: Java compilation passes using SBT, not Maven.

- [ ] **Step 3: Run the SBT staged smoke task with an explicit wall-clock bound**

Run:

```bash
timeout 600 sbt -batch smokeInstalled
```

Expected:
- `Universal/stage` completes.
- `wave-smoke.sh start` prints `READY`.
- `wave-smoke.sh check` prints `ROOT_STATUS=200`, `HEALTH_STATUS=200`, `J2CL_ROOT_STATUS=200`, `SIDECAR_STATUS=200`, and `WEBCLIENT_STATUS=200`.
- The task's `finally` block runs `wave-smoke.sh stop`, and the selected smoke port no longer has a listener afterward.
- The command exits before the `timeout 600` guard fires.

- [ ] **Step 4: Run the worktree-lane browser-verification startup sequence on an issue-specific port**

Run from the issue worktree:

```bash
bash scripts/worktree-boot.sh --port 9926
WORKTREE_ROOT=$(pwd)
PORT=9926 JAVA_OPTS="-Djava.util.logging.config.file=$WORKTREE_ROOT/wave/config/wiab-logging.conf -Djava.security.auth.login.config=$WORKTREE_ROOT/wave/config/jaas.config" bash scripts/wave-smoke.sh start
sleep 3
PORT=9926 bash scripts/wave-smoke.sh check
curl -sS -o /dev/null -w '%{http_code}\n' http://localhost:9926/?view=j2cl-root
PORT=9926 bash scripts/wave-smoke.sh stop
```

Expected:
- `start` returns after printing `READY`, but the Java listener remains alive.
- `check` succeeds after the deliberate `sleep 3`; the delay is intentionally longer than an immediate readiness probe so the pass is not just the same race window that masked `#1026`.
- The J2CL root curl prints `200`.
- `stop` exits `0` and removes the staged `wave_server.pid`.
- The `WORKTREE_ROOT=$(pwd)` path is issue-worktree evidence only. Do not copy an absolute worktree path into committed runbooks.

- [ ] **Step 5: Verify duplicate start does not leave two listeners or silently spawn another server**

Run after a successful `start` and before the final `stop`:

```bash
WORKTREE_ROOT=$(pwd)
PORT=9926 JAVA_OPTS="-Djava.util.logging.config.file=$WORKTREE_ROOT/wave/config/wiab-logging.conf -Djava.security.auth.login.config=$WORKTREE_ROOT/wave/config/jaas.config" bash scripts/wave-smoke.sh start
PORT=9926 bash scripts/wave-smoke.sh check
PORT=9926 bash scripts/wave-smoke.sh stop
```

Expected: the second `start` treats the existing same-install Wave listener as stale, stops it through the existing guarded stop path, starts one replacement listener, and `check` still passes. If this behavior is judged too disruptive for interactive browser verification, stop implementation and revise `ensure_port_free` so same-install repeated `start` reports the existing listener instead of replacing it. In either case, there must never be two listeners or a leftover Java process on the same port.

- [ ] **Step 6: Verify SIGTERM to the recorded Java PID releases the joined process**

Run after a successful `start`:

```bash
SERVER_PID=$(cat target/universal/stage/wave_server.pid)
echo "Recorded staged server PID: $SERVER_PID"
kill "$SERVER_PID"
for i in 1 2 3 4 5; do
  if ! kill -0 "$SERVER_PID" 2>/dev/null; then
    break
  fi
  sleep 1
done
PORT=9926 bash scripts/wave-smoke.sh stop
```

Expected: the recorded Java PID exits after SIGTERM, and the follow-up `stop` is idempotent. If the PID remains alive until `stop` escalates, inspect whether the Java launcher, Jetty join, or shutdown hook path needs a narrower signal-handling fix before PR.

- [ ] **Step 7: Verify no orphan listener remains**

Run:

```bash
LSOF_OUTPUT=$(lsof -nP -iTCP:9926 -sTCP:LISTEN || true)
printf '%s\n' "$LSOF_OUTPUT"
test -z "$LSOF_OUTPUT"
```

Expected: no listener output, and the `test -z "$LSOF_OUTPUT"` command exits `0`. Capture this empty-listener evidence, the `wave_server.pid` value used for SIGTERM, and the actual port in the issue comment. If port `9926` is busy before the test, choose a free port and record the actual port in the issue evidence.

## Task 5: Documentation Touches Only If Command Shape Changes

**Files:**
- Modify only if needed: `docs/SMOKE_TESTS.md`
- Modify only if needed: `docs/runbooks/browser-verification.md`

- [ ] **Step 1: Check whether docs need changes**

Run:

```bash
rg -n "wave-smoke.sh start|wave-smoke.sh check|wave-smoke.sh stop|worktree-boot.sh" docs/SMOKE_TESTS.md docs/runbooks/browser-verification.md
```

Expected: if the implementation keeps the same command shape, no doc changes are required. If a command or required environment variable changes, update only the exact affected lines.

- [ ] **Step 2: Do not add a changelog fragment for this harness-only lifecycle fix unless maintainers classify it as user-facing**

This issue changes local verification harness behavior. Do not add `wave/config/changelog.d/` unless the implementation also changes product-visible behavior.

## Task 6: Commit And Issue Evidence For The Implementation Lane

**Files:**
- Git metadata only.

- [ ] **Step 1: Review the final diff**

Run:

```bash
git diff --check
git diff -- scripts/tests/test_wave_smoke_lifecycle.py wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/ServerRpcProvider.java wave/src/jakarta-overrides/java/org/waveprotocol/box/server/ServerMain.java docs/SMOKE_TESTS.md docs/runbooks/browser-verification.md
```

Expected: no whitespace errors and no unrelated files.

- [ ] **Step 2: Commit the implementation**

Run:

```bash
git add scripts/tests/test_wave_smoke_lifecycle.py wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/ServerRpcProvider.java wave/src/jakarta-overrides/java/org/waveprotocol/box/server/ServerMain.java
git add docs/SMOKE_TESTS.md docs/runbooks/browser-verification.md 2>/dev/null || true
git commit -m "fix(#1026): keep staged server alive after readiness"
```

Expected: one focused implementation commit. If docs were not changed, the second `git add` is harmless. If repo convention prefers trailers, use a commit body with `Issue: #1026`.

- [ ] **Step 3: Update issue `#1026` with implementation evidence**

Post a GitHub issue comment containing:
- Worktree path.
- Branch.
- Plan path.
- Commit SHA and one-line summary.
- Exact verification commands and pass/fail result.
- Claude implementation-review result when that later implementation review is complete.
- PR URL when the later implementation lane opens one.

## Risks And Guardrails

- **CI hang risk:** The Java process now blocks by design. CI remains bounded because `sbt smokeInstalled` invokes `wave-smoke.sh start`, then `check`, then `stop` in a `finally` block with a stop timeout. Do not remove that `finally` cleanup.
- **Interrupted shutdown risk:** Preserve interrupt status and call `server.stopServer()` if the joined main thread is interrupted.
- **Orphan process risk:** Keep PID-file plus port-listener stop behavior. Do not replace it with broad `pkill -f org.waveprotocol.box.server.ServerMain`, because other worktrees may be using their own ports.
- **Runtime source risk:** Edit the Jakarta override only. The repo rules say the Jakarta override is runtime-active; changing the main-tree `ServerMain` alone will not affect this staged path.
- **Port conflict risk:** Use a non-default worktree port. If the chosen port is occupied, choose another port instead of skipping verification.

## Final Acceptance Checklist

- [ ] `python3 -m pytest scripts/tests/test_wave_smoke_lifecycle.py` passes.
- [ ] `sbt -batch wave/compile` passes.
- [ ] `timeout 600 sbt -batch smokeInstalled` passes, exits before timeout, and runs `stop` cleanup.
- [ ] `bash scripts/worktree-boot.sh --port <port>` followed by printed `start`, delayed `check`, J2CL root curl, and `stop` succeeds.
- [ ] Repeated `start` on the same port does not leave duplicate listeners or an orphaned Java process.
- [ ] Direct SIGTERM to the recorded staged Java PID releases the joined process or is explained and fixed before PR.
- [ ] No listener remains on the verification port after `stop`, and the empty `lsof` evidence is captured in issue `#1026`.
- [ ] Issue `#1026` records worktree, branch, plan, commit, verification, review, and PR traceability.

## Self-Review

- Spec coverage: The plan maps each acceptance criterion to a task: stable post-readiness process in Tasks 2-4, delayed `check` in Task 4, browser/J2CL root reliability in Task 4, CI/non-interactive bounded behavior in Tasks 4 and Risks, and orphan prevention in Tasks 1, 4, and Risks.
- Placeholder scan: No `TBD`, `TODO`, "implement later", or unspecified test placeholders remain. Commands, files, code snippets, and expected results are explicit.
- Narrowness: The plan touches only the active Jakarta lifecycle seam and smoke regression coverage. It avoids J2CL UI behavior, Maven, broad process killing, unrelated docs, and product changelog work unless later implementation evidence requires a command-shape doc update.
- Stale-assumption check: The plan is based on current `origin/main` at worktree creation and the observed files in this issue worktree. If `ServerRpcProvider` or `ServerMain` changes before implementation starts, rerun the evidence inspection and update this plan before coding.
- Claude plan review follow-up: The first review returned `pass-with-minor-revisions`; this revision clarifies that the Python script test is a lifecycle guard rather than the Java bug reproducer, fixes the fake TCP server `allow_reuse_address` setup, adds explicit provider/signature checks, bounds `smokeInstalled` with `timeout 600`, adds repeated-start and direct-SIGTERM verification, and documents why a Java block/unblock unit test is conditional rather than mandatory. The second review returned only minor revisions; this version also parses fake-server request paths with `urlparse`, verifies the PID file points to a live process, states the `joinServer()` thread-safety expectation, templates worktree paths with `$(pwd)`, adds staged-source evidence, and requires capturing empty `lsof` evidence in issue `#1026`. The third review concluded `pass`; its implementation-time follow-ups are now part of Task 1 (`PORT` propagation), Task 2 (shutdown-hook inspection), and Task 6 (`#1026` commit traceability).
