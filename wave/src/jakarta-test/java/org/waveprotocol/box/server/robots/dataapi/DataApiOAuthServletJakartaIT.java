/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership.  The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.waveprotocol.box.server.robots.dataapi;

import com.google.common.base.Strings;
import net.oauth.OAuth;
import net.oauth.OAuthAccessor;
import net.oauth.OAuthException;
import net.oauth.OAuthMessage;
import net.oauth.OAuthServiceProvider;
import net.oauth.OAuthValidator;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.jakarta.TestSupport;
import org.waveprotocol.box.server.authentication.WebSession;
import org.waveprotocol.box.server.authentication.WebSessions;
import org.waveprotocol.box.server.robots.dataapi.DataApiOAuthServlet;
import org.waveprotocol.box.server.robots.dataapi.DataApiTokenContainer;
import org.waveprotocol.wave.model.id.TokenGeneratorImpl;
import org.waveprotocol.wave.model.wave.ParticipantId;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

/**
 * Integration tests for the Jakarta DataApiOAuthServlet.
 */
public final class DataApiOAuthServletJakartaIT {
  private static final String REQUEST_PATH = "/OAuthGetRequestToken";
  private static final String AUTHORIZE_PATH = "/OAuthAuthorizeToken";
  private static final String ACCESS_PATH = "/OAuthGetAccessToken";
  private static final String ALL_TOKENS_PATH = "/OAuthGetAllTokens";

  private Server server;
  private int port;
  private StubSessionManager sessionManager;
  private DataApiTokenContainer tokenContainer;
  private CookieManager cookieManager;

  @Before
  public void start() throws Exception {
    TestSupport.assumeJettyEe10PresentOrSkip();
    cookieManager = new CookieManager();
    CookieHandler.setDefault(cookieManager);

    sessionManager = new StubSessionManager();
    tokenContainer = new DataApiTokenContainer(new TokenGeneratorImpl(new Random(1234L)));

    DataApiOAuthServlet servlet = new DataApiOAuthServlet(
        REQUEST_PATH,
        AUTHORIZE_PATH,
        ACCESS_PATH,
        ALL_TOKENS_PATH,
        new OAuthServiceProvider(
            DataApiOAuthServlet.DATA_API_OAUTH_PATH + REQUEST_PATH,
            DataApiOAuthServlet.DATA_API_OAUTH_PATH + AUTHORIZE_PATH,
            DataApiOAuthServlet.DATA_API_OAUTH_PATH + ACCESS_PATH),
        new PermissiveOAuthValidator(),
        tokenContainer,
        sessionManager,
        new TokenGeneratorImpl(new Random(9876L))
    );

    server = new Server();
    ServerConnector connector = new ServerConnector(server);
    connector.setPort(0);
    server.addConnector(connector);

    ServletContextHandler handler = new ServletContextHandler(ServletContextHandler.SESSIONS);
    handler.setContextPath("/");
    handler.addServlet(DataApiOAuthServletHolder.of(servlet), DataApiOAuthServlet.DATA_API_OAUTH_PATH + "/*");
    handler.addServlet(LoginServletHolder.of(new LoginServlet(sessionManager)), "/auth/signin");
    server.setHandler(handler);

    server.start();
    port = connector.getLocalPort();
  }

  @After
  public void stop() throws Exception {
    if (server != null) {
      server.stop();
    }
    CookieHandler.setDefault(null);
    cookieManager = null;
  }

  @Test
  public void fullOAuthFlowProducesAccessToken() throws Exception {
    // Step 1: Request token
    HttpURLConnection requestConn = post("/robot/dataapi/oauth" + REQUEST_PATH, "");
    assertEquals(200, requestConn.getResponseCode());
    Map<String, String> requestBody = FormUtil.decode(readBody(requestConn));
    String requestToken = requestBody.get(OAuth.OAUTH_TOKEN);
    assertFalse("request token should be present", Strings.isNullOrEmpty(requestToken));

    String callback = "http://callback.example/success";

    // Step 2: Authorization without login should redirect to signin
    HttpURLConnection authRedirect = get("/robot/dataapi/oauth" + AUTHORIZE_PATH
        + "?oauth_token=" + urlEncode(requestToken)
        + "&oauth_callback=" + urlEncode(callback));
    assertEquals(302, authRedirect.getResponseCode());
    String loginLocation = authRedirect.getHeaderField("Location");
    assertNotNull(loginLocation);
    assertTrue(loginLocation.contains("/auth/signin"));

    // Step 3: Perform login (sets session and redirects back)
    HttpURLConnection login = get(loginLocation + "&user=robot@example.com");
    assertEquals(302, login.getResponseCode());
    String postLoginLocation = login.getHeaderField("Location");
    assertTrue(postLoginLocation.contains(AUTHORIZE_PATH));

    // Step 4: Load authorization form while logged in to obtain XSRF token
    HttpURLConnection authForm = get(postLoginLocation);
    assertEquals(200, authForm.getResponseCode());
    String formHtml = readBody(authForm);
    String xsrfToken = extractHiddenToken(formHtml);
    assertNotNull("XSRF token must be present", xsrfToken);

    // Step 5: Approve token via POST
    HttpURLConnection authPost = post(postLoginLocation,
        "agree=true&token=" + urlEncode(xsrfToken));
    assertEquals(302, authPost.getResponseCode());
    String callbackLocation = authPost.getHeaderField("Location");
    assertTrue(callbackLocation.startsWith(callback));
    assertTrue(callbackLocation.contains("oauth_token=" + requestToken));

    // Step 6: Exchange request token for access token
    HttpURLConnection exchange = post("/robot/dataapi/oauth" + ACCESS_PATH,
        OAuth.OAUTH_TOKEN + "=" + urlEncode(requestToken));
    assertEquals(200, exchange.getResponseCode());
    Map<String, String> accessBody = FormUtil.decode(readBody(exchange));
    assertEquals("true", accessBody.get(OAuth.OAUTH_CALLBACK_CONFIRMED));
    String accessToken = accessBody.get(OAuth.OAUTH_TOKEN);
    String accessSecret = accessBody.get(OAuth.OAUTH_TOKEN_SECRET);
    assertFalse(Strings.isNullOrEmpty(accessToken));
    assertFalse(Strings.isNullOrEmpty(accessSecret));

    // Sanity check: token container now holds access token
    OAuthAccessor accessor = tokenContainer.getAccessTokenAccessor(accessToken);
    assertEquals(accessToken, accessor.accessToken);
    assertEquals(accessSecret, accessor.tokenSecret);
  }

  private HttpURLConnection get(String path) throws IOException {
    URL url = resolve(path);
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.setInstanceFollowRedirects(false);
    return conn;
  }

  private HttpURLConnection post(String path, String body) throws IOException {
    URL url = resolve(path);
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.setInstanceFollowRedirects(false);
    conn.setRequestMethod("POST");
    conn.setDoOutput(true);
    conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
    try (OutputStreamWriter writer = new OutputStreamWriter(conn.getOutputStream(), StandardCharsets.UTF_8)) {
      writer.write(body);
    }
    return conn;
  }

  private URL resolve(String path) throws IOException {
    if (path.startsWith("http")) {
      return URI.create(path).toURL();
    }
    if (!path.startsWith("/")) {
      path = "/" + path;
    }
    return new URL("http://localhost:" + port + path);
  }

  private static String readBody(HttpURLConnection conn) throws IOException {
    InputStream in = conn.getResponseCode() >= 400 ? conn.getErrorStream() : conn.getInputStream();
    if (in == null) {
      return "";
    }
    return new String(in.readAllBytes(), StandardCharsets.UTF_8);
  }

  private static String extractHiddenToken(String html) {
    Matcher matcher = Pattern.compile("name=\"token\" value=\"([^\"]+)\"").matcher(html);
    return matcher.find() ? matcher.group(1) : null;
  }

  private static String urlEncode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  /** Simplistic servlet holder factory to avoid Jetty dependency noise. */
  private static final class DataApiOAuthServletHolder extends org.eclipse.jetty.ee10.servlet.ServletHolder {
    private DataApiOAuthServletHolder(DataApiOAuthServlet servlet) {
      super(servlet);
    }
    static DataApiOAuthServletHolder of(DataApiOAuthServlet servlet) {
      return new DataApiOAuthServletHolder(servlet);
    }
  }

  private static final class LoginServletHolder extends org.eclipse.jetty.ee10.servlet.ServletHolder {
    private LoginServletHolder(HttpServlet servlet) {
      super(servlet);
    }
    static LoginServletHolder of(HttpServlet servlet) {
      return new LoginServletHolder(servlet);
    }
  }

  /**
   * Minimal session manager implementation that stores participants in the web session.
   */
  private static final class StubSessionManager implements SessionManager {
    @Override
    public ParticipantId getLoggedInUser(WebSession session) {
      return (session == null) ? null : (ParticipantId) session.getAttribute(USER_FIELD);
    }

    @Override
    public org.waveprotocol.box.server.account.AccountData getLoggedInAccount(WebSession session) {
      return null;
    }

    @Override
    public void setLoggedInUser(WebSession session, ParticipantId id) {
      if (session != null) {
        session.setAttribute(USER_FIELD, id);
      }
    }

    @Override
    public void logout(WebSession session) {
      if (session != null) {
        session.removeAttribute(USER_FIELD);
      }
    }

    @Override
    public String getLoginUrl(String redirect) {
      if (Strings.isNullOrEmpty(redirect)) {
        return "/auth/signin";
      }
      return "/auth/signin?redirect=" + urlEncode(redirect);
    }

    @Override
    public WebSession getSessionFromToken(String token) {
      return null;
    }
  }

  /** Servlet that mimics the Wave login endpoint for the test environment. */
  private static final class LoginServlet extends HttpServlet {
    private final StubSessionManager sessionManager;
    LoginServlet(StubSessionManager sessionManager) {
      this.sessionManager = sessionManager;
    }
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
      String userParam = req.getParameter("user");
      String redirect = req.getParameter("redirect");
      if (Strings.isNullOrEmpty(userParam)) {
        resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "user parameter required");
        return;
      }
      ParticipantId user = ParticipantId.ofUnsafe(userParam);
      sessionManager.setLoggedInUser(WebSessions.from(req, true), user);
      if (Strings.isNullOrEmpty(redirect)) {
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write("logged in");
      } else {
        resp.sendRedirect(redirect);
      }
    }
  }

  /** OAuth validator that allows all messages (for deterministic testing). */
  private static final class PermissiveOAuthValidator implements OAuthValidator {
    @Override
    public void validateMessage(OAuthMessage message, OAuthAccessor accessor)
        throws OAuthException, IOException {
      // Intentionally no-op for testing.
    }
  }

  /** Utility for parsing application/x-www-form-urlencoded responses. */
  private static final class FormUtil {
    static Map<String, String> decode(String body) {
      return body.lines()
          .flatMap(line -> java.util.Arrays.stream(line.split("&")))
          .map(pair -> pair.split("=", 2))
          .filter(parts -> parts.length == 2)
          .collect(java.util.stream.Collectors.toMap(
              parts -> decodePart(parts[0]),
              parts -> decodePart(parts[1])
          ));
    }

    private static String decodePart(String value) {
      try {
        return java.net.URLDecoder.decode(value, StandardCharsets.UTF_8.name());
      } catch (Exception e) {
        throw new UncheckedIOException(new IOException("Failed to decode parameter", e));
      }
    }
  }
}
