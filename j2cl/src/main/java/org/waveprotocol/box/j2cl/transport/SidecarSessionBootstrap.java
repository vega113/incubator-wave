package org.waveprotocol.box.j2cl.transport;

import java.util.Map;

public final class SidecarSessionBootstrap {
  public static final String BOOTSTRAP_PATH = "/bootstrap.json";

  private final String address;
  private final String websocketAddress;

  public SidecarSessionBootstrap(String address, String websocketAddress) {
    this.address = address;
    this.websocketAddress = websocketAddress;
  }

  public String getAddress() {
    return address;
  }

  public String getWebSocketAddress() {
    return websocketAddress;
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
    return new SidecarSessionBootstrap(address, socketAddress);
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
