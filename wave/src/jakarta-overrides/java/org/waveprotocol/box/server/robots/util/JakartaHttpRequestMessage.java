/**
 * Minimal Jakarta servlet compatible replacement for net.oauth.server.HttpRequestMessage.
 */
package org.waveprotocol.box.server.robots.util;

import jakarta.servlet.http.HttpServletRequest;
import net.oauth.OAuth;
import net.oauth.OAuthMessage;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

/**
 * Builds an {@link OAuthMessage} from a Jakarta {@link HttpServletRequest}.
 */
public final class JakartaHttpRequestMessage extends OAuthMessage {
  private final HttpServletRequest request;

  public JakartaHttpRequestMessage(HttpServletRequest request, String requestURL) {
    super(request.getMethod(), requestURL, collectParameters(request));
    this.request = request;
  }

  @Override
  public InputStream getBodyAsStream() throws IOException {
    return request.getInputStream();
  }

  @Override
  public String getBodyEncoding() {
    return request.getCharacterEncoding();
  }

  private static List<OAuth.Parameter> collectParameters(HttpServletRequest request) {
    List<OAuth.Parameter> params = new ArrayList<>();
    for (Map.Entry<String, String[]> entry : request.getParameterMap().entrySet()) {
      String key = entry.getKey();
      if (key == null) {
        continue;
      }
      String[] values = entry.getValue();
      if (values == null || values.length == 0) {
        params.add(new OAuth.Parameter(key, null));
      } else {
        for (String value : values) {
          params.add(new OAuth.Parameter(key, value));
        }
      }
    }

    Enumeration<String> headers = request.getHeaders("Authorization");
    while (headers != null && headers.hasMoreElements()) {
      String header = headers.nextElement();
      if (header == null) {
        continue;
      }
      if (header.regionMatches(true, 0, "OAuth", 0, 5)) {
        for (OAuth.Parameter p : OAuthMessage.decodeAuthorization(header)) {
          params.add(p);
        }
      }
    }
    return params;
  }
}
