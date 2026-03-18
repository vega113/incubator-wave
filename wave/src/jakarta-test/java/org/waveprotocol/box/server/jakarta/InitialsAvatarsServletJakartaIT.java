package org.waveprotocol.box.server.jakarta;

import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.apache.wave.box.server.rpc.InitialsAvatarsServlet;

import java.net.HttpURLConnection;
import java.net.URL;

import static org.junit.Assert.*;

public class InitialsAvatarsServletJakartaIT {
  private Server server;
  private int port;

  @Before
  public void start() throws Exception {
    TestSupport.assumeJettyEe10PresentOrSkip();
    server = new Server();
    ServerConnector connector = new ServerConnector(server);
    connector.setPort(0);
    server.addConnector(connector);

    ServletContextHandler ctx = new ServletContextHandler(ServletContextHandler.SESSIONS);
    ctx.setContextPath("/");
    ctx.addServlet(new ServletHolder(new InitialsAvatarsServlet()), "/iniavatars/*");

    server.setHandler(ctx);
    server.start();
    port = connector.getLocalPort();
  }

  @After
  public void stop() {
    TestSupport.stopServerQuietly(server);
  }

  @Test
  public void servesDefaultAvatar() throws Exception {
    URL url = new URL("http://localhost:" + port + "/iniavatars/default");
    HttpURLConnection c = TestSupport.openConnection(url);
    assertEquals(200, c.getResponseCode());
    String contentType = c.getHeaderField("Content-Type");
    assertNotNull(contentType);
    assertTrue(contentType.toLowerCase().contains("image"));
    byte[] body = c.getInputStream().readAllBytes();
    assertTrue("expected non-empty avatar payload", body.length > 0);
  }
}
