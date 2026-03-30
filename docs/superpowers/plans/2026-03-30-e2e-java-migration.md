# E2E Java Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the Python/pytest E2E suite (19 tests) with an equivalent Java/JUnit 5 suite using stdlib `java.net.http.*` and Gson.

**Architecture:** Custom SBT `e2eTest` config sources from `wave/src/e2e-test/java`; four Java files handle shared state, HTTP API, WebSocket protocol, and the ordered test class. Python files are deleted after the Java suite passes against a live server.

**Tech Stack:** Java 17, JUnit Jupiter 5.10, `net.aichler:jupiter-interface` 0.11.1 (JUnit 5 sbt bridge), `java.net.http.HttpClient`, `java.net.http.WebSocket`, Gson (already on classpath)

---

## File Map

| Action | Path | Responsibility |
|---|---|---|
| Create | `wave/src/e2e-test/java/org/waveprotocol/wave/e2e/E2eTestContext.java` | Shared static state (cookies, wave IDs, WS handles) |
| Create | `wave/src/e2e-test/java/org/waveprotocol/wave/e2e/WaveApiClient.java` | HTTP: register, login, fetch, search, healthCheck |
| Create | `wave/src/e2e-test/java/org/waveprotocol/wave/e2e/WaveWebSocketClient.java` | WebSocket: connect, send, recv, recvUntil, close |
| Create | `wave/src/e2e-test/java/org/waveprotocol/wave/e2e/WaveE2eTest.java` | 19 ordered JUnit 5 tests + @AfterAll cleanup |
| Modify | `build.sbt` lines 449–470 | Add E2eTest config, ivy registration, settings |
| Modify | `build.sbt` lines 228–235 | Add JUnit 5 deps scoped to E2eTest |
| Modify | `.github/workflows/e2e.yml` | Drop Python steps; run `sbt e2eTest:test` |
| Modify | `scripts/wave-e2e.sh` | Replace pytest with sbt e2eTest:test |
| Delete | `wave/src/e2e-test/test_e2e_sanity.py` | Python tests (replaced) |
| Delete | `wave/src/e2e-test/wave_client.py` | Python client (replaced) |
| Delete | `wave/src/e2e-test/conftest.py` | pytest fixtures (replaced) |
| Delete | `wave/src/e2e-test/requirements.txt` | Python deps (replaced) |

---

## Task 1: Create git worktree

**Files:** (none — setup only)

- [ ] **Step 1: Create worktree and branch**

```bash
git -C /Users/vega/devroot/incubator-wave worktree add \
  /Users/vega/devroot/worktrees/feat-e2e-java \
  -b feat/e2e-tests-java
cd /Users/vega/devroot/worktrees/feat-e2e-java
```

Expected: `Preparing worktree (new branch 'feat/e2e-tests-java')`

- [ ] **Step 2: Create Java source directory**

```bash
mkdir -p wave/src/e2e-test/java/org/waveprotocol/wave/e2e
```

---

## Task 2: Add E2eTest SBT config and JUnit 5 dependencies

**Files:** Modify `build.sbt`

- [ ] **Step 1: Add E2eTest config definition after ThumbTest (line ~453)**

In `build.sbt`, find this block:
```scala
lazy val ThumbTest      = config("thumbTest")      extend Test  describedAs "Isolated AttachmentServlet thumbnail tests"
```

Add immediately after it:
```scala
lazy val E2eTest        = config("e2eTest")        extend Test  describedAs "E2E sanity tests against a running Wave server"
```

- [ ] **Step 2: Register E2eTest in ivyConfigurations (line ~456)**

Find:
```scala
ivyConfigurations ++= Seq(JakartaTest, JakartaIT, StacktraceTest, ThumbTest)
```

Replace with:
```scala
ivyConfigurations ++= Seq(JakartaTest, JakartaIT, StacktraceTest, ThumbTest, E2eTest)
```

- [ ] **Step 3: Wire E2eTest with Defaults.testSettings (line ~462)**

Find:
```scala
inConfig(ThumbTest)(Defaults.testSettings)
```

Add immediately after:
```scala
inConfig(E2eTest)(Defaults.testSettings)
```

- [ ] **Step 4: Add E2eTest to the excludeLintKeys block (line ~465)**

Find this block and extend it:
```scala
Global / excludeLintKeys ++= Set(
  JakartaTest / javaSource, JakartaTest / scalaSource, JakartaTest / resourceDirectory, JakartaTest / semanticdbTargetRoot,
  JakartaIT / javaSource, JakartaIT / scalaSource, JakartaIT / resourceDirectory, JakartaIT / semanticdbTargetRoot,
  StacktraceTest / javaSource, StacktraceTest / scalaSource, StacktraceTest / semanticdbTargetRoot,
  ThumbTest / javaSource, ThumbTest / scalaSource, ThumbTest / semanticdbTargetRoot
)
```

Replace with:
```scala
Global / excludeLintKeys ++= Set(
  JakartaTest / javaSource, JakartaTest / scalaSource, JakartaTest / resourceDirectory, JakartaTest / semanticdbTargetRoot,
  JakartaIT / javaSource, JakartaIT / scalaSource, JakartaIT / resourceDirectory, JakartaIT / semanticdbTargetRoot,
  StacktraceTest / javaSource, StacktraceTest / scalaSource, StacktraceTest / semanticdbTargetRoot,
  ThumbTest / javaSource, ThumbTest / scalaSource, ThumbTest / semanticdbTargetRoot,
  E2eTest / javaSource, E2eTest / scalaSource, E2eTest / resourceDirectory, E2eTest / semanticdbTargetRoot
)
```

- [ ] **Step 5: Add E2eTest source/classpath/fork settings**

After the `ThumbTest` settings block (search for the last `ThumbTest /` settings), add a new block:

```scala
// --- E2eTest: E2E sanity suite settings ---
// Source dir: wave/src/e2e-test/java
E2eTest / unmanagedSourceDirectories := Seq(
  baseDirectory.value / "wave" / "src" / "e2e-test" / "java"
)
E2eTest / fork := true
E2eTest / javaOptions ++= Seq("-ea")
// Include main compile output and full test classpath (Gson, etc. must be available)
E2eTest / dependencyClasspath ++= (Compile / exportedProducts).value
E2eTest / dependencyClasspath ++= (Test / dependencyClasspath).value
E2eTest / dependencyClasspath ++= (Compile / fullClasspath).value
// Register JUnit 5 (jupiter-interface) as the test framework for this config
E2eTest / testFrameworks += new TestFramework("net.aichler.sbt.jupiterinterfaceplugin.JupiterTestFramework")
// WAVE_E2E_BASE_URL is read from environment by the tests; fork=true passes env vars through
```

- [ ] **Step 6: Add JUnit 5 library dependencies scoped to E2eTest**

Find (line ~228):
```scala
libraryDependencies ++= Seq(
  // --- Test ---
  "junit"                          % "junit"                      % "4.12"     % Test,
  "com.novocode"                   % "junit-interface"            % "0.11"     % Test,
```

Add three lines after the existing test deps (after `"org.testcontainers" % "mongodb"` line):
```scala
  // --- E2E test (JUnit 5 — scoped to e2eTest config only, does not affect unit tests) ---
  "org.junit.jupiter"              % "junit-jupiter-api"          % "5.10.2"   % E2eTest,
  "org.junit.jupiter"              % "junit-jupiter-engine"       % "5.10.2"   % E2eTest,
  "net.aichler"                    % "jupiter-interface"          % "0.11.1"   % E2eTest,
```

- [ ] **Step 7: Verify SBT resolves without errors**

```bash
cd /Users/vega/devroot/worktrees/feat-e2e-java
sbt --batch "show e2eTest:unmanagedSourceDirectories"
```

Expected output includes `wave/src/e2e-test/java`.

---

## Task 3: Create E2eTestContext.java

**Files:** Create `wave/src/e2e-test/java/org/waveprotocol/wave/e2e/E2eTestContext.java`

- [ ] **Step 1: Write the file**

```java
package org.waveprotocol.wave.e2e;

import com.google.gson.JsonObject;
import java.util.UUID;

/**
 * Shared static state for the ordered E2E test suite.
 * All fields are mutated in test order by WaveE2eTest.
 * RUN_ID is a short unique suffix preventing cross-run collisions.
 */
class E2eTestContext {

    /** 8-char hex suffix unique per JVM run — appended to usernames and wave local IDs. */
    static final String RUN_ID =
            UUID.randomUUID().toString().replace("-", "").substring(0, 8);

    static String aliceJsessionid;
    static String bobJsessionid;
    static String aliceJwt;
    static String bobJwt;

    /** Wave ID in "domain!wave_local_id" format (e.g. local.net!w+e2eabc12345). */
    static String waveId;
    /** Modern wave ID in "domain/wave_local_id" format (e.g. local.net/w+e2eabc12345). */
    static String modernWaveId;
    /** Wavelet name: "domain/wave_local_id/~/wavelet_local_id". */
    static String waveletName;
    /** Channel ID returned during ProtocolOpenRequest drain phase. */
    static String channelId;
    /**
     * Last hashed_version_after_application from ProtocolSubmitResponse (field "3").
     * Stored as JsonObject {"1": version, "2": historyHash} to avoid Double coercion.
     */
    static JsonObject lastVersion;

    static String blipId;
    static String replyBlipId;

    static WaveWebSocketClient aliceWs;
    static WaveWebSocketClient bobWs;
}
```

---

## Task 4: Create WaveApiClient.java

**Files:** Create `wave/src/e2e-test/java/org/waveprotocol/wave/e2e/WaveApiClient.java`

- [ ] **Step 1: Write the file**

```java
package org.waveprotocol.wave.e2e;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * HTTP client for the Wave server REST/servlet API.
 *
 * Uses Redirect.NEVER so the 302/303 from /auth/signin can be intercepted
 * to capture Set-Cookie before any redirect is followed.
 */
class WaveApiClient {

    private static final Gson GSON = new Gson();
    private final String baseUrl;
    private final HttpClient http;

    WaveApiClient(String baseUrl) {
        this.baseUrl = baseUrl.replaceAll("/$", "");
        this.http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /** GET /healthz — returns true when server responds 200. */
    boolean healthCheck() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/healthz"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<Void> resp = http.send(req, HttpResponse.BodyHandlers.discarding());
            return resp.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * POST /auth/register — returns HTTP status code.
     * 200 = success, 403 = duplicate/disabled.
     */
    int register(String username, String password) throws Exception {
        String body = "address=" + enc(username) + "&password=" + enc(password);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/auth/register"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<Void> resp = http.send(req, HttpResponse.BodyHandlers.discarding());
        return resp.statusCode();
    }

    /**
     * POST /auth/signin — returns a LoginResult with JSESSIONID and wave-session-jwt.
     * The server returns 302/303 on success; we read Set-Cookie before the redirect.
     * Address is sent as "username@local.net".
     */
    LoginResult login(String username, String password) throws Exception {
        String address = username + "@local.net";
        String body = "address=" + enc(address) + "&password=" + enc(password);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/auth/signin"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<Void> resp = http.send(req, HttpResponse.BodyHandlers.discarding());
        int status = resp.statusCode();
        if (status != 200 && status != 302 && status != 303) {
            return new LoginResult(status, null, null);
        }
        String jsessionid = null;
        String jwt = null;
        for (String header : resp.headers().allValues("set-cookie")) {
            for (String part : header.split(";")) {
                String trimmed = part.trim();
                if (trimmed.startsWith("JSESSIONID=")) {
                    jsessionid = trimmed.substring("JSESSIONID=".length());
                } else if (trimmed.startsWith("wave-session-jwt=")) {
                    jwt = trimmed.substring("wave-session-jwt=".length());
                }
            }
        }
        return new LoginResult(status, jsessionid, jwt);
    }

    /**
     * GET /fetch/<waveId> with JSESSIONID cookie.
     * Wave ID "domain!id" is converted to "domain/id" URL path per JavaWaverefEncoder.
     */
    JsonObject fetch(String jsessionid, String waveId) throws Exception {
        String path = waveId.replace("!", "/");
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/fetch/" + path))
                .timeout(Duration.ofSeconds(10))
                .header("Cookie", "JSESSIONID=" + jsessionid)
                .GET()
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        return GSON.fromJson(resp.body(), JsonObject.class);
    }

    /** GET /search/?query=...&index=0&numResults=10 with JSESSIONID cookie. */
    JsonObject search(String jsessionid, String query) throws Exception {
        String url = baseUrl + "/search/?query=" + enc(query) + "&index=0&numResults=10";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Cookie", "JSESSIONID=" + jsessionid)
                .GET()
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        return GSON.fromJson(resp.body(), JsonObject.class);
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    /** Result from login(). */
    static class LoginResult {
        final int status;
        final String jsessionid;
        final String jwt;

        LoginResult(int status, String jsessionid, String jwt) {
            this.status = status;
            this.jsessionid = jsessionid;
            this.jwt = jwt;
        }

        boolean success() {
            return status == 200 || status == 302 || status == 303;
        }
    }
}
```

---

## Task 5: Create WaveWebSocketClient.java

**Files:** Create `wave/src/e2e-test/java/org/waveprotocol/wave/e2e/WaveWebSocketClient.java`

- [ ] **Step 1: Write the file**

```java
package org.waveprotocol.wave.e2e;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * WebSocket client for the Wave server /socket endpoint.
 *
 * Wraps java.net.http.WebSocket with a LinkedBlockingDeque so tests can
 * call recv() and recvUntil() synchronously.
 *
 * Wire format — JSON envelopes:
 *   {"messageType": "...", "sequenceNumber": N, "message": {...}}
 *
 * The listener calls webSocket.request(1) after every onText invocation
 * (including partial fragments) so flow control is continuously active.
 * Fragmented frames are accumulated in fragmentBuffer before enqueuing.
 */
class WaveWebSocketClient {

    private static final Gson GSON = new Gson();
    /** Sentinel values pushed to queue on error or server-close. */
    private static final String ERROR_SENTINEL = "\u0000ERROR\u0000";
    private static final String CLOSE_SENTINEL = "\u0000CLOSE\u0000";

    private final WebSocket ws;
    private final LinkedBlockingDeque<String> queue = new LinkedBlockingDeque<>();
    private final AtomicInteger seqNum = new AtomicInteger(0);
    /** Accumulates text fragments until last==true. Not shared across threads. */
    private final StringBuilder fragmentBuffer = new StringBuilder();

    private WaveWebSocketClient(WebSocket ws) {
        this.ws = ws;
    }

    /**
     * Open a WebSocket to ws://<host>/socket authenticated via JSESSIONID cookie.
     * Blocks until the handshake completes (up to 15 s).
     */
    static WaveWebSocketClient connect(String baseUrl, String jsessionid) throws Exception {
        String wsUrl = baseUrl.replace("http://", "ws://")
                              .replace("https://", "wss://") + "/socket";

        // ref is set after the WebSocket object is created, before request(1) is called.
        // No messages arrive before request(1) so the window is safe.
        AtomicReference<WaveWebSocketClient> ref = new AtomicReference<>();

        HttpClient httpClient = HttpClient.newHttpClient();
        WebSocket ws = httpClient.newWebSocketBuilder()
                .header("Cookie", "JSESSIONID=" + jsessionid)
                .connectTimeout(Duration.ofSeconds(15))
                .buildAsync(URI.create(wsUrl), new WebSocket.Listener() {

                    @Override
                    public CompletionStage<?> onText(WebSocket ws,
                                                     CharSequence data,
                                                     boolean last) {
                        WaveWebSocketClient client = ref.get();
                        if (client != null) {
                            client.fragmentBuffer.append(data);
                            if (last) {
                                String full = client.fragmentBuffer.toString();
                                client.fragmentBuffer.setLength(0);
                                client.queue.add(full);
                            }
                        }
                        ws.request(1); // always re-arm for next frame/fragment
                        return null;
                    }

                    @Override
                    public void onError(WebSocket ws, Throwable error) {
                        WaveWebSocketClient client = ref.get();
                        if (client != null) client.queue.add(ERROR_SENTINEL);
                    }

                    @Override
                    public CompletionStage<?> onClose(WebSocket ws,
                                                      int statusCode,
                                                      String reason) {
                        WaveWebSocketClient client = ref.get();
                        if (client != null) client.queue.add(CLOSE_SENTINEL);
                        return null;
                    }
                })
                .get(15, TimeUnit.SECONDS);

        WaveWebSocketClient client = new WaveWebSocketClient(ws);
        ref.set(client);
        ws.request(1); // prime the pump — allow first message from server
        return client;
    }

    /**
     * Send a JSON envelope: {"messageType":type, "sequenceNumber":N, "message":payload}.
     */
    void send(String msgType, JsonObject payload) throws Exception {
        JsonObject envelope = new JsonObject();
        envelope.addProperty("messageType", msgType);
        envelope.addProperty("sequenceNumber", seqNum.getAndIncrement());
        envelope.add("message", payload);
        ws.sendText(GSON.toJson(envelope), true).get(10, TimeUnit.SECONDS);
    }

    /**
     * Receive the next envelope within timeoutMs.
     * Throws TimeoutException, or RuntimeException on error/close sentinel.
     */
    JsonObject recv(long timeoutMs) throws Exception {
        String raw = queue.poll(timeoutMs, TimeUnit.MILLISECONDS);
        if (raw == null)
            throw new TimeoutException("recv() timed out after " + timeoutMs + "ms");
        if (ERROR_SENTINEL.equals(raw)) throw new RuntimeException("WebSocket error");
        if (CLOSE_SENTINEL.equals(raw)) throw new RuntimeException("WebSocket closed by server");
        return GSON.fromJson(raw, JsonObject.class);
    }

    /**
     * Loop recv() until a message with the given messageType arrives.
     * Messages of other types are silently discarded.
     */
    JsonObject recvUntil(String msgType, long timeoutMs) throws Exception {
        long deadlineNs = System.nanoTime() + timeoutMs * 1_000_000L;
        while (true) {
            long remainingMs = (deadlineNs - System.nanoTime()) / 1_000_000L;
            if (remainingMs <= 0)
                throw new TimeoutException("recvUntil(" + msgType + ") timed out");
            JsonObject msg = recv(remainingMs);
            String mt = msg.has("messageType") ? msg.get("messageType").getAsString() : "";
            if (msgType.equals(mt)) return msg;
        }
    }

    /** Send WebSocket close frame and drain the queue. */
    void close() {
        try {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "test done").get(5, TimeUnit.SECONDS);
        } catch (Exception ignored) {}
        queue.clear();
    }
}
```

---

## Task 6: Create WaveE2eTest.java

**Files:** Create `wave/src/e2e-test/java/org/waveprotocol/wave/e2e/WaveE2eTest.java`

- [ ] **Step 1: Write the file (all 19 tests)**

```java
package org.waveprotocol.wave.e2e;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * E2E sanity suite — 19 ordered tests covering registration, login, WebSocket,
 * wave creation, search, cross-user messaging, and cleanup.
 *
 * Run with: WAVE_E2E_BASE_URL=http://localhost:9898 sbt "e2eTest:test"
 *
 * Tests share state through E2eTestContext. Each test depends on the prior ones
 * having run; @TestMethodOrder(OrderAnnotation.class) enforces order.
 */
@TestMethodOrder(OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WaveE2eTest {

    private static final String DOMAIN = "local.net";
    private static final String PASSWORD = "Secret123!";

    private WaveApiClient client;

    // Helpers — suffixed with RUN_ID to avoid cross-run collisions
    private String alice()     { return "alice_" + E2eTestContext.RUN_ID; }
    private String bob()       { return "bob_" + E2eTestContext.RUN_ID; }
    private String aliceAddr() { return alice() + "@" + DOMAIN; }
    private String bobAddr()   { return bob() + "@" + DOMAIN; }

    @BeforeAll
    void setUp() {
        String baseUrl = System.getenv("WAVE_E2E_BASE_URL");
        assertNotNull(baseUrl, "WAVE_E2E_BASE_URL environment variable must be set");
        client = new WaveApiClient(baseUrl);
    }

    @AfterAll
    void cleanup() {
        for (WaveWebSocketClient ws : new WaveWebSocketClient[]{
                E2eTestContext.aliceWs, E2eTestContext.bobWs}) {
            if (ws != null) {
                try { ws.close(); } catch (Exception ignored) {}
            }
        }
    }

    // =========================================================================
    // Phase 1 — Server health
    // =========================================================================

    @Test @Order(1)
    void test01_healthCheck() {
        assertTrue(client.healthCheck(), "Server /healthz must return 200");
    }

    // =========================================================================
    // Phase 2 — Registration and login
    // =========================================================================

    @Test @Order(2)
    void test02_registerAlice() throws Exception {
        int status = client.register(alice(), PASSWORD);
        assertEquals(200, status, "Alice registration should return 200");
    }

    @Test @Order(3)
    void test03_registerBob() throws Exception {
        int status = client.register(bob(), PASSWORD);
        assertEquals(200, status, "Bob registration should return 200");
    }

    @Test @Order(4)
    void test04_duplicateRegistration() throws Exception {
        int status = client.register(alice(), PASSWORD);
        assertEquals(403, status, "Duplicate Alice registration should return 403");
    }

    @Test @Order(5)
    void test05_loginAlice() throws Exception {
        WaveApiClient.LoginResult result = client.login(alice(), PASSWORD);
        assertTrue(result.success(), "Alice login failed, status=" + result.status);
        assertNotNull(result.jsessionid, "Alice JSESSIONID must not be null");
        E2eTestContext.aliceJsessionid = result.jsessionid;
        E2eTestContext.aliceJwt = result.jwt;
    }

    @Test @Order(6)
    void test06_loginBob() throws Exception {
        WaveApiClient.LoginResult result = client.login(bob(), PASSWORD);
        assertTrue(result.success(), "Bob login failed, status=" + result.status);
        assertNotNull(result.jsessionid, "Bob JSESSIONID must not be null");
        E2eTestContext.bobJsessionid = result.jsessionid;
        E2eTestContext.bobJwt = result.jwt;
    }

    // =========================================================================
    // Phase 3 — WebSocket scenarios
    // =========================================================================

    @Test @Order(7)
    void test07_aliceWsConnect() throws Exception {
        String baseUrl = System.getenv("WAVE_E2E_BASE_URL");
        WaveWebSocketClient ws = WaveWebSocketClient.connect(baseUrl, E2eTestContext.aliceJsessionid);

        // Send ProtocolAuthenticate — belt-and-suspenders on Jakarta cookie-first auth
        JsonObject auth = new JsonObject();
        auth.addProperty("1", E2eTestContext.aliceJsessionid);
        ws.send("ProtocolAuthenticate", auth);

        JsonObject resp = ws.recv(10_000);
        assertEquals("ProtocolAuthenticationResult",
                resp.get("messageType").getAsString(),
                "Expected ProtocolAuthenticationResult, got: " + resp);
        E2eTestContext.aliceWs = ws;
    }

    @Test @Order(8)
    void test08_aliceCreatesWave() throws Exception {
        String waveLocalId    = "w+e2e" + E2eTestContext.RUN_ID;
        String waveletLocalId = "conv+root";
        String waveId         = DOMAIN + "!" + waveLocalId;
        String modernWaveId   = DOMAIN + "/" + waveLocalId;
        String waveletName    = DOMAIN + "/" + waveLocalId + "/~/" + waveletLocalId;
        WaveWebSocketClient ws = E2eTestContext.aliceWs;

        // 1. Open wave (subscribe) — required before submitting the first delta
        ws.send("ProtocolOpenRequest", makeOpenRequest(aliceAddr(), modernWaveId));

        // Drain ProtocolWaveletUpdate messages until the marker (inner field "6" == true)
        // Collect channelId from inner field "7" when it first appears
        String channelId = null;
        long drainDeadline = System.nanoTime() + 15_000_000_000L;
        while (System.nanoTime() < drainDeadline) {
            long remaining = (drainDeadline - System.nanoTime()) / 1_000_000L;
            if (remaining <= 0) break;
            JsonObject msg = ws.recv(remaining);
            if (!"ProtocolWaveletUpdate".equals(
                    msg.has("messageType") ? msg.get("messageType").getAsString() : "")) {
                continue;
            }
            JsonObject inner = msg.has("message") ? msg.get("message").getAsJsonObject()
                                                   : new JsonObject();
            if (inner.has("7") && channelId == null) {
                channelId = inner.get("7").getAsString();
            }
            if (inner.has("6") && inner.get("6").getAsBoolean()) break;
        }

        assertNotNull(channelId, "channel_id not received during ProtocolOpenRequest drain");

        // 2. Submit add_participant delta at version 0
        String v0Hash = versionZeroHash(DOMAIN, waveLocalId, waveletLocalId);
        JsonObject delta    = makeAddParticipantDelta(aliceAddr(), aliceAddr(), 0, v0Hash);
        JsonObject submitReq = makeSubmitRequest(waveletName, delta, channelId);
        ws.send("ProtocolSubmitRequest", submitReq);

        JsonObject resp = ws.recvUntil("ProtocolSubmitResponse", 30_000);
        JsonObject msg  = resp.get("message").getAsJsonObject();
        int ops = msg.has("1") ? msg.get("1").getAsInt() : 0;
        assertTrue(ops > 0, "Expected operations_applied > 0, got: " + resp);
        assertTrue(msg.has("3"),
                "ProtocolSubmitResponse missing hashed_version_after_application (field '3')");

        E2eTestContext.waveId       = waveId;
        E2eTestContext.modernWaveId = modernWaveId;
        E2eTestContext.waveletName  = waveletName;
        E2eTestContext.channelId    = channelId;
        E2eTestContext.lastVersion  = msg.get("3").getAsJsonObject();
    }

    @Test @Order(9)
    void test09_aliceOpensWave() throws Exception {
        JsonObject result = client.fetch(E2eTestContext.aliceJsessionid, E2eTestContext.waveId);
        String raw = result.toString();
        assertTrue(raw.contains(aliceAddr()),
                "Alice (" + aliceAddr() + ") not found in fetch response: "
                + raw.substring(0, Math.min(500, raw.length())));
    }

    @Test @Order(10)
    void test10_aliceAddsBob() throws Exception {
        JsonObject lv      = E2eTestContext.lastVersion;
        int version        = lv.get("1").getAsInt();
        String historyHash = lv.get("2").getAsString();

        JsonObject delta    = makeAddParticipantDelta(aliceAddr(), bobAddr(), version, historyHash);
        JsonObject submitReq = makeSubmitRequest(E2eTestContext.waveletName, delta, E2eTestContext.channelId);
        E2eTestContext.aliceWs.send("ProtocolSubmitRequest", submitReq);

        JsonObject resp = E2eTestContext.aliceWs.recvUntil("ProtocolSubmitResponse", 30_000);
        JsonObject msg  = resp.get("message").getAsJsonObject();
        assertTrue(msg.has("1") && msg.get("1").getAsInt() > 0,
                "Expected operations_applied > 0 adding Bob, got: " + resp);
        assertTrue(msg.has("3"), "Submit response missing hashed_version_after_application");
        E2eTestContext.lastVersion = msg.get("3").getAsJsonObject();
    }

    @Test @Order(11)
    void test11_aliceWritesBlip() throws Exception {
        JsonObject lv      = E2eTestContext.lastVersion;
        int version        = lv.get("1").getAsInt();
        String historyHash = lv.get("2").getAsString();

        String blipId    = "b+e2e" + E2eTestContext.RUN_ID;
        JsonObject delta = makeBlipDelta(aliceAddr(), blipId, "Hello from E2E test!", version, historyHash);
        JsonObject submitReq = makeSubmitRequest(E2eTestContext.waveletName, delta, E2eTestContext.channelId);
        E2eTestContext.aliceWs.send("ProtocolSubmitRequest", submitReq);

        JsonObject resp = E2eTestContext.aliceWs.recvUntil("ProtocolSubmitResponse", 30_000);
        JsonObject msg  = resp.get("message").getAsJsonObject();
        assertTrue(msg.has("1") && msg.get("1").getAsInt() > 0,
                "Expected operations_applied > 0 writing blip, got: " + resp);
        assertTrue(msg.has("3"), "Submit response missing hashed_version_after_application");
        E2eTestContext.lastVersion = msg.get("3").getAsJsonObject();
        E2eTestContext.blipId = blipId;
    }

    // =========================================================================
    // Phase 4 — Cross-user communication
    // =========================================================================

    @Test @Order(12)
    void test12_bobWsConnect() throws Exception {
        String baseUrl = System.getenv("WAVE_E2E_BASE_URL");
        WaveWebSocketClient ws = WaveWebSocketClient.connect(baseUrl, E2eTestContext.bobJsessionid);

        JsonObject auth = new JsonObject();
        auth.addProperty("1", E2eTestContext.bobJsessionid);
        ws.send("ProtocolAuthenticate", auth);

        JsonObject resp = ws.recv(10_000);
        assertEquals("ProtocolAuthenticationResult",
                resp.get("messageType").getAsString(),
                "Expected ProtocolAuthenticationResult for Bob, got: " + resp);
        E2eTestContext.bobWs = ws;
    }

    @Test @Order(13)
    void test13_bobSearch() throws Exception {
        boolean found = pollSearchForWave(
                E2eTestContext.bobJsessionid, E2eTestContext.modernWaveId,
                20_000, 500, 0);
        assertTrue(found,
                "Wave " + E2eTestContext.modernWaveId
                + " not found in Bob's in:inbox search within 20s");
    }

    @Test @Order(14)
    void test14_bobOpensWave() throws Exception {
        JsonObject result = client.fetch(E2eTestContext.bobJsessionid, E2eTestContext.waveId);
        String raw = result.toString();
        assertTrue(raw.contains("Hello from E2E test!"),
                "Alice's blip text not found in Bob's fetch: "
                + raw.substring(0, Math.min(500, raw.length())));
    }

    @Test @Order(15)
    void test15_bobUnreadCount() throws Exception {
        // Poll until Bob's search digest shows blip_count >= 1
        boolean found = pollSearchForWave(
                E2eTestContext.bobJsessionid, E2eTestContext.modernWaveId,
                20_000, 500, 1);
        assertTrue(found,
                "Wave " + E2eTestContext.modernWaveId
                + " with blip_count>=1 not found in Bob's search within 20s");
    }

    @Test @Order(16)
    void test16_bobReplies() throws Exception {
        JsonObject lv      = E2eTestContext.lastVersion;
        int version        = lv.get("1").getAsInt();
        String historyHash = lv.get("2").getAsString();

        String replyBlipId = "b+reply" + E2eTestContext.RUN_ID;
        JsonObject delta   = makeBlipDelta(bobAddr(), replyBlipId, "Hello from Bob!", version, historyHash);
        // Bob replies without channelId (he hasn't subscribed to the wave via WS)
        JsonObject submitReq = makeSubmitRequest(E2eTestContext.waveletName, delta, null);
        E2eTestContext.bobWs.send("ProtocolSubmitRequest", submitReq);

        JsonObject resp = E2eTestContext.bobWs.recvUntil("ProtocolSubmitResponse", 30_000);
        JsonObject msg  = resp.get("message").getAsJsonObject();
        assertTrue(msg.has("1") && msg.get("1").getAsInt() > 0,
                "Expected operations_applied > 0 for Bob's reply, got: " + resp);
        if (msg.has("3")) {
            E2eTestContext.lastVersion = msg.get("3").getAsJsonObject();
        }
        E2eTestContext.replyBlipId = replyBlipId;
    }

    @Test @Order(17)
    void test17_aliceReceivesReply() throws Exception {
        JsonObject result = client.fetch(E2eTestContext.aliceJsessionid, E2eTestContext.waveId);
        String raw = result.toString();
        assertTrue(raw.contains("Hello from Bob!"),
                "Bob's reply not found in Alice's fetch: "
                + raw.substring(0, Math.min(500, raw.length())));
    }

    @Test @Order(18)
    void test18_aliceFetchSeesReply() throws Exception {
        JsonObject result = client.fetch(E2eTestContext.aliceJsessionid, E2eTestContext.waveId);
        String raw = result.toString();
        assertTrue(raw.contains("Hello from E2E test!"),
                "Alice's original blip not found in fetch: "
                + raw.substring(0, Math.min(500, raw.length())));
        assertTrue(raw.contains("Hello from Bob!"),
                "Bob's reply not found in fetch: "
                + raw.substring(0, Math.min(500, raw.length())));
    }

    // =========================================================================
    // Protocol message builders
    // =========================================================================

    /**
     * Version-zero history hash: UTF-8 bytes of "wave://<domain>/<waveLocal>/<waveletLocal>"
     * encoded as uppercase hex. Matches Python compute_version_zero_hash().
     */
    private static String versionZeroHash(String domain, String waveLocal, String waveletLocal) {
        String uri = "wave://" + domain + "/" + waveLocal + "/" + waveletLocal;
        byte[] bytes = uri.getBytes(StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02X", b));
        return sb.toString();
    }

    private static JsonObject makeHashedVersion(int version, String historyHash) {
        JsonObject hv = new JsonObject();
        hv.addProperty("1", version);
        hv.addProperty("2", historyHash);
        return hv;
    }

    private static JsonObject makeOpenRequest(String participant, String waveId) {
        JsonObject req = new JsonObject();
        req.addProperty("1", participant);
        req.addProperty("2", waveId);
        req.add("3", new JsonArray());
        req.add("4", new JsonArray());
        return req;
    }

    /**
     * Build a ProtocolWaveletDelta with a single add_participant operation.
     * op structure: {"1": newParticipant}  (field 1 of WaveletOperation = add_participant)
     */
    private static JsonObject makeAddParticipantDelta(String author, String newParticipant,
                                                       int version, String historyHash) {
        JsonObject addPartOp = new JsonObject();
        addPartOp.addProperty("1", newParticipant);

        JsonObject opWrapper = new JsonObject();
        opWrapper.add("1", addPartOp);  // field 1 of WaveletOperation

        JsonArray ops = new JsonArray();
        ops.add(opWrapper);

        JsonObject delta = new JsonObject();
        delta.add("1", makeHashedVersion(version, historyHash));
        delta.addProperty("2", author);
        delta.add("3", ops);
        delta.add("4", new JsonArray()); // address_path
        return delta;
    }

    /**
     * Build a ProtocolWaveletDelta with a single mutate_document operation.
     * Document content: <body><line/>text</body>
     * op structure: field 3 of WaveletOperation = mutate_document
     */
    private static JsonObject makeBlipDelta(String author, String blipId, String text,
                                             int version, String historyHash) {
        // Components of the document operation
        JsonObject bodyStart = new JsonObject();
        JsonObject bodyElem = new JsonObject();
        bodyElem.addProperty("1", "body");
        bodyElem.add("2", new JsonArray());
        bodyStart.add("3", bodyElem);          // element_start <body>

        JsonObject lineStart = new JsonObject();
        JsonObject lineElem = new JsonObject();
        lineElem.addProperty("1", "line");
        lineElem.add("2", new JsonArray());
        lineStart.add("3", lineElem);          // element_start <line>

        JsonObject lineEnd = new JsonObject();
        lineEnd.addProperty("4", true);        // element_end </line>

        JsonObject chars = new JsonObject();
        chars.addProperty("2", text);          // characters

        JsonObject bodyEnd = new JsonObject();
        bodyEnd.addProperty("4", true);        // element_end </body>

        JsonArray components = new JsonArray();
        components.add(bodyStart);
        components.add(lineStart);
        components.add(lineEnd);
        components.add(chars);
        components.add(bodyEnd);

        JsonObject docOp = new JsonObject();
        docOp.add("1", components);            // field 1 of DocumentOperation = components[]

        JsonObject mutateDoc = new JsonObject();
        mutateDoc.addProperty("1", blipId);    // document_id
        mutateDoc.add("2", docOp);             // document_operation

        JsonObject opWrapper = new JsonObject();
        opWrapper.add("3", mutateDoc);         // field 3 of WaveletOperation = mutate_document

        JsonArray ops = new JsonArray();
        ops.add(opWrapper);

        JsonObject delta = new JsonObject();
        delta.add("1", makeHashedVersion(version, historyHash));
        delta.addProperty("2", author);
        delta.add("3", ops);
        delta.add("4", new JsonArray());
        return delta;
    }

    private static JsonObject makeSubmitRequest(String waveletName, JsonObject delta,
                                                 String channelId) {
        JsonObject req = new JsonObject();
        req.addProperty("1", waveletName);
        req.add("2", delta);
        if (channelId != null) req.addProperty("3", channelId);
        return req;
    }

    /**
     * Poll GET /search/?query=in:inbox until target wave appears with blipCount >= minBlipCount.
     * Search digest: field "3" = wave id string, field "6" = blip count.
     */
    private boolean pollSearchForWave(String jsessionid, String modernWaveId,
                                       long timeoutMs, long pollMs, int minBlipCount)
            throws Exception {
        long deadlineNs = System.nanoTime() + timeoutMs * 1_000_000L;
        while (System.nanoTime() < deadlineNs) {
            JsonObject result = client.search(jsessionid, "in:inbox");
            if (result.has("3") && result.get("3").isJsonArray()) {
                for (var elem : result.get("3").getAsJsonArray()) {
                    JsonObject digest = elem.getAsJsonObject();
                    String digestWaveId = digest.has("3") ? digest.get("3").getAsString() : "";
                    if (digestWaveId.contains(modernWaveId)) {
                        int blipCount = digest.has("6") ? digest.get("6").getAsInt() : 0;
                        if (blipCount >= minBlipCount) return true;
                    }
                }
            }
            Thread.sleep(pollMs);
        }
        return false;
    }
}
```

---

## Task 7: Verify compilation

**Files:** (no source changes — compilation check only)

- [ ] **Step 1: Compile the E2e test sources**

```bash
cd /Users/vega/devroot/worktrees/feat-e2e-java
sbt --batch "e2eTest:compile"
```

Expected: `[success] Total time: ...` with no errors.

If `net.aichler:jupiter-interface` fails to resolve, run:
```bash
sbt --batch "show e2eTest:libraryDependencies" 2>&1 | grep -i jupiter
```
to confirm the dependency is wired. If `JupiterTestFramework` class is not found at resolution, use the fallback framework class name `net.aichler.sbt.jupiterinterfaceplugin.JupiterTestFramework` (check jar contents: `jar tf ~/.ivy2/cache/net.aichler/jupiter-interface/jars/*.jar | grep Framework`).

- [ ] **Step 2: Commit scaffold**

```bash
git add build.sbt wave/src/e2e-test/java/
git commit -m "feat: add Java E2E test scaffold (SBT config + 4 source files)"
```

---

## Task 8: Update CI workflow

**Files:** Modify `.github/workflows/e2e.yml`

- [ ] **Step 1: Remove Python setup steps and replace E2E run step**

Replace the entire `e2e.yml` content with:

```yaml
name: E2E Tests

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]
  workflow_dispatch:

jobs:
  e2e:
    name: E2E WebSocket Tests
    runs-on: ubuntu-latest
    timeout-minutes: 20

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'
          cache: 'sbt'

      - name: Set up sbt
        uses: sbt/setup-sbt@v1

      - name: Compile PST and Wave
        run: sbt --batch pst/compile wave/compile

      - name: Stage distribution
        run: sbt --batch Universal/stage

      - name: Run E2E tests
        env:
          WAVE_E2E_BASE_URL: http://localhost:9898
        run: bash scripts/wave-e2e.sh

      - name: Upload E2E results
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: e2e-results
          path: |
            wave/target/e2e-results/
            target/universal/stage/wave_server.out
```

---

## Task 9: Update wave-e2e.sh

**Files:** Modify `scripts/wave-e2e.sh`

- [ ] **Step 1: Replace the pytest invocation block with sbt**

Find this block in `scripts/wave-e2e.sh` (lines ~114–130):
```bash
echo "[e2e] Running pytest against $BASE_URL ..."

PYTHON="${PYTHON:-python3}"

set +e
WAVE_E2E_BASE_URL="$BASE_URL" \
  "$PYTHON" -m pytest "$E2E_DIR" \
    -v \
    --tb=short \
    --junitxml="$RESULTS_DIR/e2e-junit.xml" \
    -o "console_output_style=classic" \
  2>&1 | tee "$RESULTS_DIR/e2e-output.txt"
exit_code=${PIPESTATUS[0]}
set -e
```

Replace with:
```bash
echo "[e2e] Running Java E2E suite (sbt e2eTest:test) against $BASE_URL ..."

set +e
WAVE_E2E_BASE_URL="$BASE_URL" \
  sbt --batch "e2eTest:test" \
  2>&1 | tee "$RESULTS_DIR/e2e-output.txt"
exit_code=${PIPESTATUS[0]}
set -e
```

- [ ] **Step 2: Remove the PYTHON variable and unused E2E_DIR variable**

Remove from the top of the script:
```bash
E2E_DIR="$REPO_ROOT/wave/src/e2e-test"
```
(The `RESULTS_DIR` and `INSTALL_DIR` variables are still needed.)

---

## Task 10: Delete Python files

**Files:** Delete 4 Python files

- [ ] **Step 1: Delete Python test files**

```bash
cd /Users/vega/devroot/worktrees/feat-e2e-java
git rm wave/src/e2e-test/test_e2e_sanity.py \
       wave/src/e2e-test/wave_client.py \
       wave/src/e2e-test/conftest.py \
       wave/src/e2e-test/requirements.txt
```

Expected: 4 files staged for deletion.

---

## Task 11: Copilot diff review

**Files:** (no code changes)

- [ ] **Step 1: Capture diff and run Copilot review**

```bash
cd /Users/vega/devroot/worktrees/feat-e2e-java
DIFF=$(git diff origin/main...HEAD)
PROMPT="Review this Java E2E migration diff for incubator-wave.

Check:
1. All 19 Python tests have exact Java equivalents with equivalent assertions
2. WebSocket + proto flow correct (request(1), fragment handling, sentinel)
3. Version-zero hash algorithm matches Python (wave:// URI uppercase hex)
4. SBT config follows existing JakartaTest pattern exactly
5. CI workflow valid YAML, Python steps removed, sbt invocation correct
6. wave-e2e.sh correctly updated
7. Python files all deleted
8. No regressions — existing unit test setup (com.novocode:junit-interface) untouched

Diff:
$DIFF"

copilot -p "$PROMPT" --model gpt-5.4 --effort xhigh --silent 2>&1
```

- [ ] **Step 2: Fix any findings and recompile**

```bash
sbt --batch "e2eTest:compile"
```

---

## Task 12: Start server and run full E2E suite

**Files:** (no code changes)

- [ ] **Step 1: Kill any existing server on the port**

```bash
PORT=9899
fuser -k ${PORT}/tcp 2>/dev/null || true
```

- [ ] **Step 2: Build distribution**

```bash
cd /Users/vega/devroot/worktrees/feat-e2e-java
sbt --batch Universal/stage
```

Expected: `[success]` and `target/universal/stage/bin/wave` exists.

- [ ] **Step 3: Start server in background**

```bash
cd target/universal/stage
nohup ./bin/wave > /tmp/wave-e2e-server.log 2>&1 &
SERVER_PID=$!
echo "Server PID=$SERVER_PID"
cd /Users/vega/devroot/worktrees/feat-e2e-java
```

- [ ] **Step 4: Wait for server to be healthy**

```bash
for i in $(seq 1 60); do
  if curl -sf -o /dev/null http://localhost:9899/healthz 2>/dev/null; then
    echo "Server healthy after ${i}s"; break
  fi
  sleep 1
done
curl -sf http://localhost:9899/healthz || (tail -30 /tmp/wave-e2e-server.log && exit 1)
```

- [ ] **Step 5: Run E2E suite**

```bash
WAVE_E2E_BASE_URL=http://localhost:9899 sbt --batch "e2eTest:test"
TEST_EXIT=$?
```

Expected: All 19 tests pass. If failures:
- Check `/tmp/wave-e2e-server.log` for server errors
- Check `wave/target/e2e-results/` for JUnit XML report
- Diagnose per failing test; fix source and re-run

- [ ] **Step 6: Stop server**

```bash
kill $SERVER_PID 2>/dev/null || true
[ $TEST_EXIT -eq 0 ] || (echo "E2E FAILED — see output above" && exit 1)
```

---

## Task 13: Commit, push, create PR, spawn monitor

**Files:** (no code changes)

- [ ] **Step 1: Stage everything and commit**

```bash
cd /Users/vega/devroot/worktrees/feat-e2e-java
git add -A
git commit -m "$(cat <<'EOF'
feat: migrate E2E sanity tests from Python/pytest to Java/JUnit 5

- Replaces all 19 Python tests with equivalent JUnit 5 tests in order
- Uses java.net.http.HttpClient + WebSocket (stdlib, no extra deps)
- Handles ProtocolAuthenticate WebSocket flow and JSON-proto messages
- Removes wave_client.py, test_e2e_sanity.py, conftest.py, requirements.txt
- Adds E2eTest SBT config following existing JakartaTest pattern
- Updates CI (e2e.yml) and scripts/wave-e2e.sh to run sbt e2eTest:test
- Per-run RUN_ID suffix prevents cross-run account/wave collisions
EOF
)"
```

- [ ] **Step 2: Push branch**

```bash
git push origin feat/e2e-tests-java
```

- [ ] **Step 3: Create PR**

```bash
PR_URL=$(gh pr create \
  --repo vega113/incubator-wave \
  --title "feat: migrate E2E sanity tests from Python to Java/JUnit 5" \
  --body "$(cat <<'EOF'
## Summary
- Replaces all 19 Python/pytest E2E tests with Java/JUnit 5 equivalents
- Uses stdlib `java.net.http` (no OkHttp); Gson for JSON (already on classpath)
- Adds `e2eTest` SBT config following existing `JakartaTest`/`JakartaIT` pattern
- Updates CI and `scripts/wave-e2e.sh` to run `sbt e2eTest:test`
- Deletes `wave_client.py`, `test_e2e_sanity.py`, `conftest.py`, `requirements.txt`

## Test plan
- [ ] All 19 tests pass locally against a live Wave server
- [ ] `sbt e2eTest:compile` succeeds cleanly
- [ ] CI `e2e` job passes (no Python setup steps remain)
- [ ] Existing unit test configs (`jakartaTest`, `jakartaIT`) unaffected

🤖 Generated with [Claude Code](https://claude.ai/claude-code)
EOF
)")
echo "PR: $PR_URL"
PR_NUMBER=$(echo "$PR_URL" | grep -o '[0-9]*$')
```

- [ ] **Step 4: Spawn PR monitor pane**

```bash
cat > /tmp/mon-${PR_NUMBER}.txt << PROMPT
You are a PR monitor for PR #${PR_NUMBER} (feat: migrate E2E tests to Java/JUnit 5) in repo vega113/incubator-wave.
Every 2 minutes: check CI, review threads, fix or rebase if needed.
Each cycle: 1) gh pr checks ${PR_NUMBER} --repo vega113/incubator-wave 2) check review threads 3) fix root cause of failures.
When merged: git worktree remove /Users/vega/devroot/worktrees/feat-e2e-java --force && git push origin --delete feat/e2e-tests-java && tmux kill-pane -t $TMUX_PANE
Start: gh pr view ${PR_NUMBER} --repo vega113/incubator-wave
PROMPT

# Reuse first pane if wave-pr-monitor is empty, otherwise split
PANE_COUNT=$(tmux list-panes -t vibe-code:wave-pr-monitor 2>/dev/null | wc -l)
if [ "$PANE_COUNT" -le 1 ]; then
  TARGET="vibe-code:wave-pr-monitor.1"
else
  tmux split-window -t vibe-code:wave-pr-monitor -v
  tmux select-layout -t vibe-code:wave-pr-monitor tiled
  TARGET="vibe-code:wave-pr-monitor.$(tmux list-panes -t vibe-code:wave-pr-monitor -F '#{pane_index}' | tail -1)"
fi
tmux select-pane -t "$TARGET" -T "PR #${PR_NUMBER} E2E Java migration"
tmux send-keys -t "$TARGET" \
  "claude --model claude-sonnet-4-6 --dangerously-skip-permissions < /tmp/mon-${PR_NUMBER}.txt" Enter
```
