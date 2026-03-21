package org.waveprotocol.box.server.authentication.jwt;

public final class BrowserSessionJwtCookie {
  private static final String SAME_SITE = "Lax";

  private BrowserSessionJwtCookie() {
  }

  public static boolean shouldUseSecureCookie(boolean requestSecure,
                                              String forwardedProtoHeader,
                                              boolean secureCookiesByDefault) {
    return requestSecure || secureCookiesByDefault || isForwardedHttps(forwardedProtoHeader);
  }

  public static String headerValue(String token, long maxAgeSeconds, boolean secure) {
    StringBuilder header = new StringBuilder();
    header.append(BrowserSessionJwt.COOKIE_NAME)
        .append("=")
        .append(token)
        .append("; Path=/; Max-Age=")
        .append(maxAgeSeconds)
        .append("; HttpOnly; SameSite=")
        .append(SAME_SITE);
    if (secure) {
      header.append("; Secure");
    }
    return header.toString();
  }

  private static boolean isForwardedHttps(String headerValue) {
    if (headerValue == null) {
      return false;
    }
    String[] values = headerValue.split(",");
    for (String value : values) {
      if ("https".equalsIgnoreCase(value.trim())) {
        return true;
      }
    }
    return false;
  }
}
