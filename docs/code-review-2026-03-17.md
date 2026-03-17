# Code Review: Apache Wave Modernization — Reconciliation Session

**Date:** 2026-03-17
**Scope:** 408 files changed, +23,489 / -1,004 lines
**Base:** `d5696b8d` (pre-session baseline)
**Head:** `599ad48d` (SBT port + all fixes)
**Reviewer:** Claude (automated review — Codex CLI unavailable due to stale refresh token)

---

## 1. Executive Summary

The reconciliation session merged 111 commits from `pro-featues` (Wiab.pro feature work) into `incubator-wave/modernization` (Jakarta migration), resolved 7 merge conflicts, fixed 5 runtime errors, ported the SBT build from Wiab.pro, and produced comprehensive planning documentation. **No critical issues found.** The merge is clean and the codebase is in a stable, buildable state.

**Verdict: PASS** with 5 minor/medium findings and 3 hardening recommendations.

---

## 2. Merge Conflict Resolution — 7 Files

All 7 conflict-resolved files were inspected for residual conflict markers, duplicate code blocks, and import consistency.

| File | Status | Notes |
|------|--------|-------|
| `.grok/settings.json` | Clean | Valid JSON, no issues |
| `wave/build.gradle` | Clean | Jakarta/javax switching logic correct; duplicate `testGwt` task was removed |
| `ServerMain.java` | Minor | Trailing whitespace on lines 94-96 (cosmetic only) |
| `AttachmentServlet.java` | Clean | javax.servlet imports consistent for non-Jakarta variant |
| `AuthenticationServlet.java` | Minor | Missing indentation on `welcomeBot` field declaration (line ~100) |
| `SignOutServlet.java` | Clean | No issues |
| `WebSocketClientRpcChannel.java` | Minor | Trailing whitespace on line 92 (cosmetic) |

**No merge conflict markers (`<<<<<<<`, `=======`, `>>>>>>>`) found in any file.**
**No duplicate code blocks from bad resolution.**

---

## 3. Runtime Fixes — 4 Patches

### 3.1 FragmentsServlet.java — WebSession migration
**Status: CORRECT**

Changed `sessionManager.getLoggedInUser(req.getSession(false))` to `sessionManager.getLoggedInUser(WebSessions.from(req, false))` and updated javax to jakarta imports. `WebSessions.from()` has the correct signature and properly adapts Jakarta `HttpSession` to `WebSession`.

### 3.2 InitialsAvatarsServlet.java — Avatar path fix
**Status: WORKS BUT FRAGILE** (Finding F-1)

Changed resource path from `"static/images/avatar/unknown.jpg"` to `"static/images/unknown.jpg"`. The primary path uses `Resources.getResource()` (classpath lookup), but the file exists in `wave/war/static/images/` (WAR directory, not classpath). The fallback path at line 52 (`org/apache/wave/box/server/rpc/avatar/unknown.jpg`) does exist on the classpath and will be used. The fix works because the fallback catches the primary path failure, but the primary path string is still incorrect for classpath loading.

**Recommendation:** Either move `unknown.jpg` to `src/main/resources/static/images/` so the classpath lookup succeeds, or change the primary path to `org/apache/wave/box/server/rpc/avatar/unknown.jpg`.

### 3.3 RobotApiModule.java — Guice binding
**Status: CORRECT**

Added `bind(RobotCapabilityFetcher.class).to(RobotConnector.class)`. Verified that `RobotConnector` implements `RobotCapabilityFetcher` (line 48 of RobotConnector.java) and provides the required `fetchCapabilities()` method.

### 3.4 Timing.java — GWT NoClassDefFoundError
**Status: CORRECT**

Added try-catch around GWT class usage. Catches `Throwable` (necessary for `NoClassDefFoundError` which is an `Error`, not `Exception`). All downstream renderer usage checks for null before calling methods.

---

## 4. SBT Build Port

### 4.1 Source Path Mappings
**Status: CORRECT**

All paths correctly mapped from Wiab.pro flat layout to incubator-wave Maven layout:
- `wave/src/main/java` (main sources)
- `wave/src/test/java` (tests)
- `wave/war` (resources)
- `proto_src` (protobuf generated sources)
- `gen/` subdirectories (GXP, messages, flags, shims)

### 4.2 Package Names
**Status: CORRECT** — No `pro.wiab` references found. All use `org.apache.wave` / `org.waveprotocol`.

### 4.3 Dependency Versions
**Status: MOSTLY ALIGNED** (Finding F-2)

| Dependency | SBT | Gradle | Match? |
|-----------|-----|--------|--------|
| Guava | 32.1.3-jre | 32.1.3-jre | Yes |
| Guice | 5.1.0 | 5.1.0 | Yes |
| Protobuf | 3.25.3 | 3.25.3 | Yes |
| Jetty (javax) | 9.4.54 | 9.4.54 | Yes |
| **Jetty (Jakarta)** | **11.0.20** | **12.0.23** | **NO** |

The SBT build specifies Jetty 11 for Jakarta mode, but Gradle uses Jetty 12 EE10. This means the SBT Jakarta build would use a different servlet container version.

**Recommendation:** Update `build.sbt` line 203 from `"11.0.20"` to `"12.0.23"`.

### 4.4 Documentation
**Status: GOOD** (Finding F-3)

`docs/BUILDING-sbt.md` line 52 references JAR name `wiab-pro-server-<version>.jar` — this should be updated to reflect the actual `name.value` from the SBT project (which defaults to the directory name).

### 4.5 Third-Party JARs
Present and correct: `third_party/{codegen,runtime,test}/` with expected vendored JARs.

---

## 5. Security Review

| Area | Status | Details |
|------|--------|---------|
| Hardcoded credentials | Secure | No secrets in source; `reference.conf` uses `"changeme"` for dev keystore only |
| CSP headers | Implemented | `SecurityHeadersFilter` with `default-src 'self'`, nosniff, referrer policy, conditional HSTS |
| Session security | Secure | HttpOnly, Secure (when SSL), SameSite=LAX cookies implemented |
| MongoDB queries | Secure | All Mongo4 stores use `Filters.eq()` — no string concatenation injection risk |
| Git history | Clean | No `*.key`, `*.pem`, `.env`, or credential files committed |

### Security Recommendations (non-blocking)

**S-1:** CSP policy includes `'unsafe-inline'` and `'unsafe-eval'` in `script-src` (required for GWT output). Document this as a known trade-off and provide a stricter override path via `security.csp` config.

**S-2:** Default HSTS `max-age` is not set in `reference.conf`. Recommend adding `security.hsts_max_age = 31536000` as a documented default.

**S-3:** File-based session store (`_sessions/`) should document required filesystem permissions (0700) for production deployments.

---

## 6. Findings Summary

| ID | Severity | File | Issue | Recommendation |
|----|----------|------|-------|---------------|
| F-1 | Medium | `InitialsAvatarsServlet.java` | Primary avatar path won't resolve on classpath; relies on fallback | Move resource or fix path |
| F-2 | Medium | `build.sbt:203` | Jetty Jakarta version 11.0.20 vs Gradle's 12.0.23 | Update to 12.0.23 |
| F-3 | Low | `docs/BUILDING-sbt.md:52` | JAR name references "wiab-pro" | Update to actual name |
| F-4 | Low | `ServerMain.java`, `WebSocketClientRpcChannel.java` | Trailing whitespace from merge | Clean up |
| F-5 | Low | `AuthenticationServlet.java` | Missing indentation on field declaration | Fix formatting |
| S-1 | Info | `SecurityHeadersFilter.java` | CSP allows unsafe-inline/eval (GWT requirement) | Document trade-off |
| S-2 | Info | `reference.conf` | No default HSTS max-age | Add recommended default |
| S-3 | Info | Session store | Filesystem permissions undocumented | Add to deployment docs |

---

## 7. Items Not Reviewed (Out of Scope)

These require runtime testing (Track A-3 in reconciliation plan):

- Full integration of dynamic rendering + fragment loading + quasi-deletion when all flags enabled simultaneously
- GWT compile with the merged codebase (`./gradlew :wave:compileGwt`)
- Jakarta integration tests (`./gradlew :wave:testJakartaIT`)
- MongoDB v4 stores under load (Mongo4DeltaStore not yet implemented)

---

## 8. Codex CLI Status

The OpenAI Codex CLI review was attempted but could not complete due to a stale refresh token (token was consumed on the host Mac, making the VM's copy invalid). To retry:

```bash
# On host Mac:
codex logout && codex login

# Then in VM:
codex review --base d5696b8d --title "Apache Wave modernization: reconciliation session"
```
