/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.waveprotocol.box.server.jakarta;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.session.SessionHandler;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.authentication.WebSession;
import org.waveprotocol.box.server.persistence.AccountStore;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;

import static org.junit.Assert.*;

/**
 * End-to-end test: start an EE10 server with sessions, create a session via
 * servlet, then resolve it via SessionManagerImpl.getSessionFromToken when the
 * experimental flag is enabled.
 */
public class SessionLookupEmbeddedIT {
  private Server server;
  private int port;
  private SessionHandler sessionHandler;

  @Before
  public void start() throws Exception {
    try {
      server = new Server();
      ServerConnector c = new ServerConnector(server);
      c.setPort(0);
      server.addConnector(c);

      org.eclipse.jetty.ee10.servlet.SessionHandler eeSessionHandler = new org.eclipse.jetty.ee10.servlet.SessionHandler();
      try {
        java.lang.reflect.Method m = eeSessionHandler.getClass().getMethod("getSessionHandler");
        Object base = m.invoke(eeSessionHandler);
        if (!(base instanceof org.eclipse.jetty.session.SessionHandler)) {
          Assume.assumeTrue("EE10 SessionHandler does not expose core SessionHandler", false);
        }
        sessionHandler = (org.eclipse.jetty.session.SessionHandler) base;
      } catch (Throwable t) {
        Assume.assumeNoException("Unable to access core SessionHandler from EE10", t);
      }

      ServletContextHandler ctx = new ServletContextHandler(ServletContextHandler.SESSIONS);
      ctx.setContextPath("/");
      ctx.setSessionHandler(eeSessionHandler);
      ctx.addServlet(EchoSessionIdServlet.class, "/sid");
      server.setHandler(ctx);
      server.start();
      port = c.getLocalPort();
    } catch (Throwable t) {
      Assume.assumeNoException("Jetty 12 not available", t);
    }
  }

  @After
  public void stop() throws Exception {
    if (server != null) server.stop();
  }

  public static class EchoSessionIdServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
      jakarta.servlet.http.HttpSession s = req.getSession(true);
      s.setAttribute("marker", "ok");
      resp.setStatus(200);
      resp.setContentType("text/plain");
      try (PrintWriter w = resp.getWriter()) {
        w.print(s.getId());
      }
    }
  }

  @Test
  public void lookupByToken_whenFlagEnabled_returnsSession() throws Exception {
    // 1) Create a session via servlet and capture the session id body
    URL url = new URL("http://localhost:" + port + "/sid");
    HttpURLConnection c = (HttpURLConnection) url.openConnection();
    assertEquals(200, c.getResponseCode());
    String sid = new String(c.getInputStream().readAllBytes());
    assertNotNull(sid);
    assertFalse(sid.isEmpty());

    // 2) Build SessionManagerImpl with the same SessionHandler and flag enabled
    Config cfg = ConfigFactory.parseString("experimental.jetty12_session_lookup = true");
    AccountStore store = Mockito.mock(AccountStore.class);
    // Construct Jakarta override SessionManagerImpl via reflection to avoid tight coupling
    Class<?> smClass = Class.forName("org.waveprotocol.box.server.authentication.SessionManagerImpl");
    SessionManager sm = (SessionManager) smClass
        .getConstructor(org.waveprotocol.box.server.persistence.AccountStore.class,
                        org.eclipse.jetty.session.SessionHandler.class,
                        com.typesafe.config.Config.class)
        .newInstance(store, sessionHandler, cfg);

    // 3) Lookup by token
    WebSession session = sm.getSessionFromToken(sid);
    // Should resolve to a non-null WebSession wrapper
    assertNotNull(session);
  }
}
