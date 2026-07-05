package org.waveprotocol.box.j2cl.transport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class SidecarSessionBootstrap {
  public static final String BOOTSTRAP_PATH = "/bootstrap.json";

  private final String address;
  private final String websocketAddress;
  // J-UI-3 (#1081, R-5.1): the per-user enabled feature-flag list passed
  // through from the bootstrap JSON's session.features array. Empty list
  // when the field is absent or signed-out.
  private final List<String> enabledFeatures;
  private final boolean admin;
  // #1277: the user's locale (e.g. "en", "de", "zh_TW") from the bootstrap
  // JSON's session.locale. Already on the wire since J-UI-8 (post-#963) and
  // consumed by the Lit i18n catalog; this surfaces it to the Java side so the
  // i18n bridge can resolve dynamic status/error strings. Empty when absent.
  private final String locale;

  public SidecarSessionBootstrap(String address, String websocketAddress) {
    this(address, websocketAddress, Collections.<String>emptyList(), false);
  }

  public SidecarSessionBootstrap(
      String address, String websocketAddress, List<String> enabledFeatures) {
    this(address, websocketAddress, enabledFeatures, false);
  }

  public SidecarSessionBootstrap(
      String address, String websocketAddress, List<String> enabledFeatures, boolean admin) {
    this(address, websocketAddress, enabledFeatures, admin, "");
  }

  public SidecarSessionBootstrap(
      String address,
      String websocketAddress,
      List<String> enabledFeatures,
      boolean admin,
      String locale) {
    this.address = address;
    this.websocketAddress = websocketAddress;
    this.enabledFeatures =
        enabledFeatures == null
            ? Collections.<String>emptyList()
            : Collections.unmodifiableList(new ArrayList<String>(enabledFeatures));
    this.admin = admin;
    this.locale = locale == null ? "" : locale.trim();
  }

  public String getAddress() {
    return address;
  }

  public String getWebSocketAddress() {
    return websocketAddress;
  }

  /**
   * J-UI-3 (#1081, R-5.1): returns the list of enabled feature-flag names
   * for the signed-in user, mirroring the bootstrap JSON's
   * {@code session.features} array. Used by experimental sub-features
   * gated under more granular flags than {@code j2cl-root-bootstrap}.
   */
  public List<String> getEnabledFeatures() {
    return enabledFeatures;
  }

  /** Convenience: returns true when {@code flag} is in {@link #getEnabledFeatures}. */
  public boolean isFeatureEnabled(String flag) {
    return enabledFeatures.contains(flag);
  }

  /** Returns true when the signed-in user has server-admin or owner privileges. */
  public boolean isAdmin() {
    return admin;
  }

  /**
   * #1277: returns the user's locale from the bootstrap JSON's
   * {@code session.locale} (e.g. {@code "en"}, {@code "de"}, {@code "zh_TW"}),
   * or an empty string when the session did not carry one. The i18n bridge uses
   * this only as an initial hint — the Lit catalog remains the source of truth
   * and resolves the active locale from {@code window.__bootstrap} itself.
   */
  public String getLocale() {
    return locale;
  }

  public static boolean usesCompatibleCookieHost(String pageHostname, String websocketAddress) {
    String expectedHost = normalizeHostName(pageHostname);
    String websocketHost = websocketHostName(websocketAddress);
    return !expectedHost.isEmpty() && expectedHost.equalsIgnoreCase(websocketHost);
  }

  public static String websocketHostName(String websocketAddress) {
    if (websocketAddress == null) {
      return "";
    }
    String trimmed = websocketAddress.trim();
    if (trimmed.isEmpty()) {
      return "";
    }
    if (trimmed.startsWith("[")) {
      int closingBracket = trimmed.indexOf(']');
      return closingBracket > 1 ? trimmed.substring(1, closingBracket) : "";
    }
    int firstColon = trimmed.indexOf(':');
    int lastColon = trimmed.lastIndexOf(':');
    if (firstColon >= 0 && firstColon == lastColon) {
      return normalizeHostName(trimmed.substring(0, firstColon));
    }
    return normalizeHostName(trimmed);
  }

  /**
   * Decode the server-owned bootstrap JSON introduced by issue #963. The
   * expected shape mirrors the server-owned bootstrap contract introduced by
   * issue #963. Unknown keys under any nested object are ignored so
   * forward-compatible extensions do not require a client change.
   *
   * <pre>
   * {
   *   "session": { "address": "...", ... },
   *   "socket":  { "address": "host:port", ... },
   *   "shell":   { ... }
   * }
   * </pre>
   *
   * <p>This method throws {@link IllegalStateException} when the payload is
   * well-formed but represents a signed-out session, and
   * {@link IllegalArgumentException} when the payload itself is malformed.
   */
  public static SidecarSessionBootstrap fromBootstrapJson(String json) {
    if (json == null) {
      throw new IllegalArgumentException("Bootstrap JSON must not be null");
    }
    Map<String, Object> root;
    try {
      root = SidecarTransportCodec.parseJsonObject(json);
    } catch (RuntimeException e) {
      throw new IllegalArgumentException("Bootstrap JSON was not a valid object", e);
    }
    Object sessionValue = root.get("session");
    if (!(sessionValue instanceof Map)) {
      throw new IllegalArgumentException("Bootstrap JSON did not include a session object");
    }
    @SuppressWarnings("unchecked")
    Map<String, Object> session = (Map<String, Object>) sessionValue;
    Object addressValue = session.get("address");
    if (!(addressValue instanceof String)) {
      throw new IllegalStateException(
          "Bootstrap JSON did not include a signed-in session address; please sign in.");
    }
    String address = ((String) addressValue).trim();
    if (address.isEmpty() || "null".equals(address)) {
      throw new IllegalStateException(
          "Bootstrap JSON did not include a signed-in session address; please sign in.");
    }
    Object socketValue = root.get("socket");
    if (!(socketValue instanceof Map)) {
      throw new IllegalArgumentException("Bootstrap JSON did not include a socket object");
    }
    @SuppressWarnings("unchecked")
    Map<String, Object> socket = (Map<String, Object>) socketValue;
    Object socketAddressValue = socket.get("address");
    if (!(socketAddressValue instanceof String)) {
      throw new IllegalArgumentException("Bootstrap JSON did not include a socket address");
    }
    String socketAddress = ((String) socketAddressValue).trim();
    if (socketAddress.isEmpty() || "null".equals(socketAddress)) {
      throw new IllegalArgumentException("Bootstrap JSON did not include a socket address");
    }
    boolean isAdmin = extractIsAdmin(session);
    return new SidecarSessionBootstrap(
        address, socketAddress, extractFeatures(session), isAdmin, extractLocale(session));
  }

  /**
   * #1277: pulls the user's locale out of {@code session.locale}. Tolerant:
   * missing or non-string values yield an empty locale rather than throwing,
   * since a missing locale must never block bootstrap (the Lit catalog falls
   * back to {@code <html lang>} then English).
   */
  private static String extractLocale(Map<String, Object> session) {
    Object localeValue = session.get("locale");
    if (!(localeValue instanceof String)) {
      return "";
    }
    String locale = ((String) localeValue).trim();
    return "null".equals(locale) ? "" : locale;
  }

  private static boolean extractIsAdmin(Map<String, Object> session) {
    Object roleValue = session.get("role");
    if (!(roleValue instanceof String)) {
      return false;
    }
    String role = (String) roleValue;
    return "admin".equals(role) || "owner".equals(role);
  }

  /**
   * J-UI-3 (#1081): pulls a list of enabled feature-flag names out of
   * {@code session.features}. Tolerant: missing or malformed fields yield
   * an empty list rather than throwing, since clients should still bootstrap
   * even if the server has not enabled any user-specific flags.
   */
  private static List<String> extractFeatures(Map<String, Object> session) {
    Object featuresValue = session.get("features");
    if (!(featuresValue instanceof java.util.List)) {
      return Collections.emptyList();
    }
    java.util.List<?> raw = (java.util.List<?>) featuresValue;
    java.util.ArrayList<String> features = new java.util.ArrayList<String>(raw.size());
    for (Object entry : raw) {
      if (entry instanceof String && !((String) entry).isEmpty()) {
        features.add((String) entry);
      }
    }
    return Collections.unmodifiableList(features);
  }

  /**
   * @deprecated Use {@link #fromBootstrapJson(String)} which reads the explicit
   *     server-owned {@code /bootstrap.json} endpoint introduced by issue #963.
   *     This legacy method is retained for one release so rolling deployments
   *     can keep serving older J2CL bundles that still scraped the root HTML
   *     page; it will be removed once the new contract has soaked. Follow-up
   *     cleanup is tracked in issue #978.
   */
  @Deprecated
  public static SidecarSessionBootstrap fromRootHtml(String html) {
    if (html == null) {
      throw new IllegalArgumentException("Root HTML must not be null");
    }
    int sessionMarker = html.indexOf("__session");
    if (sessionMarker < 0) {
      throw new IllegalArgumentException("Root page did not expose window.__session");
    }
    int assignIdx = sessionMarker + "__session".length();
    while (assignIdx < html.length() && Character.isWhitespace(html.charAt(assignIdx))) {
      assignIdx++;
    }
    if (assignIdx >= html.length() || html.charAt(assignIdx) != '=') {
      throw new IllegalArgumentException("Unable to locate __session assignment operator");
    }
    assignIdx++;
    while (assignIdx < html.length() && Character.isWhitespace(html.charAt(assignIdx))) {
      assignIdx++;
    }
    if (assignIdx >= html.length() || html.charAt(assignIdx) != '{') {
      throw new IllegalArgumentException("Unable to locate __session JSON object");
    }
    int objectStart = assignIdx;
    int objectEnd = findMatchingBrace(html, objectStart);
    Map<String, Object> session =
        SidecarTransportCodec.parseJsonObject(html.substring(objectStart, objectEnd + 1));
    Object addressValue = session.get("address");
    if (!(addressValue instanceof String)) {
      throw new IllegalArgumentException("Session bootstrap did not include an address");
    }
    String address = ((String) addressValue).trim();
    if ("null".equals(address) || address.isEmpty()) {
      throw new IllegalArgumentException("Session bootstrap did not include an address");
    }
    String websocketAddress = parseWebSocketAddress(html);
    // #1277: locale is intentionally NOT scraped from the legacy root HTML.
    // Per the #963 contract, bootstrap metadata must come from the explicit
    // server-owned bootstrap JSON, not from parsing arbitrary root HTML — and
    // this path is deprecated (removal tracked in #978). Locale is surfaced
    // only through fromBootstrapJson.
    return new SidecarSessionBootstrap(address, websocketAddress);
  }

  private static String parseWebSocketAddress(String html) {
    int marker = html.indexOf("__websocket_address");
    if (marker < 0) {
      throw new IllegalArgumentException("Root page did not expose window.__websocket_address");
    }
    int assignIdx = marker + "__websocket_address".length();
    while (assignIdx < html.length() && Character.isWhitespace(html.charAt(assignIdx))) {
      assignIdx++;
    }
    if (assignIdx >= html.length() || html.charAt(assignIdx) != '=') {
      throw new IllegalArgumentException("Unable to locate __websocket_address assignment operator");
    }
    assignIdx++;
    while (assignIdx < html.length() && Character.isWhitespace(html.charAt(assignIdx))) {
      assignIdx++;
    }
    if (assignIdx >= html.length() || html.charAt(assignIdx) != '"') {
      throw new IllegalArgumentException("Root page did not expose a websocket address string");
    }
    int stringEnd = findMatchingStringQuote(html, assignIdx);
    String websocketAddress =
        SidecarTransportCodec.parseJsonString(html.substring(assignIdx, stringEnd + 1)).trim();
    if ("null".equals(websocketAddress) || websocketAddress.isEmpty()) {
      throw new IllegalArgumentException("Root page did not expose a websocket address");
    }
    return websocketAddress;
  }

  private static int findMatchingBrace(String html, int objectStart) {
    boolean inString = false;
    boolean escaped = false;
    int depth = 0;
    for (int i = objectStart; i < html.length(); i++) {
      char c = html.charAt(i);
      if (escaped) {
        escaped = false;
        continue;
      }
      if (c == '\\') {
        escaped = true;
        continue;
      }
      if (c == '"') {
        inString = !inString;
        continue;
      }
      if (inString) {
        continue;
      }
      if (c == '{') {
        depth++;
      } else if (c == '}') {
        depth--;
        if (depth == 0) {
          return i;
        }
      }
    }
    throw new IllegalArgumentException("Unterminated __session JSON object");
  }

  private static int findMatchingStringQuote(String html, int stringStart) {
    boolean escaped = false;
    for (int i = stringStart + 1; i < html.length(); i++) {
      char c = html.charAt(i);
      if (escaped) {
        escaped = false;
        continue;
      }
      if (c == '\\') {
        escaped = true;
        continue;
      }
      if (c == '"') {
        return i;
      }
    }
    throw new IllegalArgumentException("Unterminated __websocket_address string");
  }

  private static String normalizeHostName(String hostName) {
    if (hostName == null) {
      return "";
    }
    String trimmed = hostName.trim();
    if (trimmed.startsWith("[") && trimmed.endsWith("]") && trimmed.length() > 2) {
      return trimmed.substring(1, trimmed.length() - 1);
    }
    return trimmed;
  }
}
