package org.waveprotocol.box.server.jakarta;

import com.typesafe.config.ConfigFactory;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.waveprotocol.box.attachment.AttachmentMetadata;
import org.waveprotocol.box.server.attachment.AttachmentService;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.rpc.AttachmentServlet;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.box.server.persistence.AttachmentUtil;
import org.mockito.ArgumentCaptor;
import org.waveprotocol.wave.media.model.AttachmentId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.wave.ParticipantId;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import static org.junit.Assert.*;

public class AttachmentServletJakartaIT {
  private Server server;
  private int port;

  private AttachmentService svc;
  private WaveletProvider wprov;
  private SessionManager sm;

  @Before
  public void start() throws Exception {
    TestSupport.assumeJettyEe10PresentOrSkip();
    try {
      svc = Mockito.mock(AttachmentService.class);
      wprov = Mockito.mock(WaveletProvider.class);
      sm = Mockito.mock(SessionManager.class);
      server = new Server();
      ServerConnector c = new ServerConnector(server);
      c.setPort(0);
      server.addConnector(c);
      ServletContextHandler ctx = new ServletContextHandler(ServletContextHandler.SESSIONS);
      ctx.setContextPath("/");
      AttachmentServlet servlet = new AttachmentServlet(svc, wprov, sm, ConfigFactory.parseString("core.thumbnail_patterns_directory=\".\""));
      ctx.addServlet(new org.eclipse.jetty.ee10.servlet.ServletHolder(servlet), AttachmentServlet.ATTACHMENT_URL + "/*");
      server.setHandler(ctx);
      server.start();
      port = c.getLocalPort();
    } catch (NoClassDefFoundError | IncompatibleClassChangeError e) {
      TestSupport.assumeJettyEe10PresentOrSkip();
    }
  }

  @After
  public void stop() throws Exception {
    if (server != null) server.stop();
  }

  @Test
  public void forbiddenWhenUnauthenticated() throws Exception {
    // No user
    Mockito.when(sm.getLoggedInUser(Mockito.any())).thenReturn(null);
    // Ensure metadata exists so servlet reaches authorization
    AttachmentId aid = AttachmentId.deserialise("att+123");
    AttachmentMetadata meta = Mockito.mock(AttachmentMetadata.class);
    Mockito.when(meta.getMimeType()).thenReturn("text/plain");
    Mockito.when(meta.getFileName()).thenReturn("hello.txt");
    Mockito.when(meta.getWaveRef()).thenReturn("local:wave/local/wavelet");
    Mockito.when(svc.getMetadata(aid)).thenReturn(meta);
    URL url = new URL("http://localhost:" + port + AttachmentServlet.ATTACHMENT_URL + "/att+123?waveRef=local:wave/local/wavelet");
    HttpURLConnection c = (HttpURLConnection) url.openConnection();
    assertEquals(403, c.getResponseCode());
  }

  @Test
  public void thumbnailPatternFallbackWhenDirInvalid() throws Exception {
    // Spin a local server with an invalid patterns directory
    Server srv = new Server();
    ServerConnector c = new ServerConnector(srv);
    c.setPort(0);
    srv.addConnector(c);
    ServletContextHandler ctx = new ServletContextHandler(ServletContextHandler.SESSIONS);
    ctx.setContextPath("/");
    AttachmentService svc2 = Mockito.mock(AttachmentService.class);
    WaveletProvider wprov2 = Mockito.mock(WaveletProvider.class);
    SessionManager sm2 = Mockito.mock(SessionManager.class);
    Mockito.when(sm2.getLoggedInUser(Mockito.any())).thenReturn(new ParticipantId("user@example.com"));
    Mockito.when(wprov2.checkAccessPermission(Mockito.any(WaveletName.class), Mockito.any())).thenReturn(true);
    AttachmentServlet servlet = new AttachmentServlet(svc2, wprov2, sm2, com.typesafe.config.ConfigFactory.parseString("core.thumbnail_patterns_directory=\"/path/does/not/exist\""));
    ctx.addServlet(new org.eclipse.jetty.ee10.servlet.ServletHolder(servlet), AttachmentServlet.THUMBNAIL_URL + "/*");
    srv.setHandler(ctx);
    srv.start();
    int p = c.getLocalPort();
    try {
      AttachmentId aid = AttachmentId.deserialise("att+123");
      AttachmentMetadata meta = Mockito.mock(AttachmentMetadata.class);
      Mockito.when(meta.getWaveRef()).thenReturn("local:wave/local/wavelet");
      Mockito.when(meta.getMimeType()).thenReturn("application/pdf"); // non-image -> pattern path
      Mockito.when(svc2.getMetadata(aid)).thenReturn(meta);

      URL url = new URL("http://localhost:" + p + AttachmentServlet.THUMBNAIL_URL + "/att+123?waveRef=local:wave/local/wavelet");
      HttpURLConnection hc = (HttpURLConnection) url.openConnection();
      assertEquals(200, hc.getResponseCode());
      assertTrue(hc.getInputStream().read() != -1); // some bytes served
    } finally {
      srv.stop();
    }
  }

  @Test
  public void servesAttachmentWhenAuthorized() throws Exception {
    ParticipantId user = new ParticipantId("user@example.com");
    Mockito.when(sm.getLoggedInUser(Mockito.any())).thenReturn(user);
    Mockito.when(wprov.checkAccessPermission(Mockito.any(WaveletName.class), Mockito.eq(user))).thenReturn(true);
    AttachmentId aid = AttachmentId.deserialise("att+123");
    AttachmentMetadata meta = Mockito.mock(AttachmentMetadata.class);
    Mockito.when(meta.getMimeType()).thenReturn("text/plain");
    Mockito.when(meta.getFileName()).thenReturn("hello.txt");
    Mockito.when(meta.getWaveRef()).thenReturn("local:wave/local/wavelet");
    Mockito.when(svc.getMetadata(aid)).thenReturn(meta);
    org.waveprotocol.box.server.persistence.AttachmentStore.AttachmentData data = new org.waveprotocol.box.server.persistence.AttachmentStore.AttachmentData() {
      @Override public InputStream getInputStream() { return new ByteArrayInputStream("OK".getBytes()); }
      @Override public long getSize() { return 2; }
    };
    Mockito.when(svc.getAttachment(aid)).thenReturn(data);

    URL url = new URL("http://localhost:" + port + AttachmentServlet.ATTACHMENT_URL + "/att+123?waveRef=local:wave/local/wavelet");
    HttpURLConnection c = (HttpURLConnection) url.openConnection();
    assertEquals(200, c.getResponseCode());
    assertEquals("text/plain", c.getHeaderField("Content-Type"));
    String disp = c.getHeaderField("Content-Disposition");
    assertNotNull(disp);
    assertTrue(disp.contains("hello.txt"));
  }

  @Test
  public void rejectsExtraPathSegments() throws Exception {
    Mockito.when(sm.getLoggedInUser(Mockito.any())).thenReturn(new ParticipantId("user@example.com"));
    // Even if metadata exists for the base id, extra segments must 404
    AttachmentId aid = AttachmentId.deserialise("att+123");
    AttachmentMetadata meta = Mockito.mock(AttachmentMetadata.class);
    Mockito.when(meta.getMimeType()).thenReturn("text/plain");
    Mockito.when(meta.getFileName()).thenReturn("hello.txt");
    Mockito.when(meta.getWaveRef()).thenReturn("local:wave/local/wavelet");
    Mockito.when(svc.getMetadata(aid)).thenReturn(meta);

    URL url = new URL("http://localhost:" + port + AttachmentServlet.ATTACHMENT_URL + "/att+123/evil?waveRef=local:wave/local/wavelet");
    HttpURLConnection c = (HttpURLConnection) url.openConnection();
    assertEquals(404, c.getResponseCode());
    Mockito.verify(wprov, Mockito.never()).checkAccessPermission(Mockito.any(), Mockito.any());
  }

  @Test
  public void rejectsBackslashInId() throws Exception {
    Mockito.when(sm.getLoggedInUser(Mockito.any())).thenReturn(new ParticipantId("user@example.com"));
    URL url = new URL("http://localhost:" + port + AttachmentServlet.ATTACHMENT_URL + "/att\\123?waveRef=local:wave/local/wavelet");
    HttpURLConnection c = (HttpURLConnection) url.openConnection();
    assertEquals(404, c.getResponseCode());
  }

  @Test
  public void rejectsDotOrDotDot() throws Exception {
    Mockito.when(sm.getLoggedInUser(Mockito.any())).thenReturn(new ParticipantId("user@example.com"));
    URL u1 = new URL("http://localhost:" + port + AttachmentServlet.ATTACHMENT_URL + "/.." );
    assertEquals(404, ((HttpURLConnection) u1.openConnection()).getResponseCode());
    URL u2 = new URL("http://localhost:" + port + AttachmentServlet.ATTACHMENT_URL + "/." );
    assertEquals(404, ((HttpURLConnection) u2.openConnection()).getResponseCode());
  }

  @Test
  public void rejectsExcessiveLength() throws Exception {
    Mockito.when(sm.getLoggedInUser(Mockito.any())).thenReturn(new ParticipantId("user@example.com"));
    String longId = "a".repeat(1024);
    URL url = new URL("http://localhost:" + port + AttachmentServlet.ATTACHMENT_URL + "/" + longId);
    HttpURLConnection c = (HttpURLConnection) url.openConnection();
    assertEquals(404, c.getResponseCode());
    Mockito.verify(svc, Mockito.never()).getMetadata(Mockito.any());
  }

  @Test
  public void acceptsDomainSlashIdSingleSegment() throws Exception {
    ParticipantId user = new ParticipantId("user@example.com");
    Mockito.when(sm.getLoggedInUser(Mockito.any())).thenReturn(user);
    Mockito.when(wprov.checkAccessPermission(Mockito.any(WaveletName.class), Mockito.eq(user))).thenReturn(true);
    AttachmentId aid = AttachmentId.deserialise("example.com/att123");
    AttachmentMetadata meta = Mockito.mock(AttachmentMetadata.class);
    Mockito.when(meta.getMimeType()).thenReturn("text/plain");
    Mockito.when(meta.getFileName()).thenReturn("hello.txt");
    Mockito.when(meta.getWaveRef()).thenReturn("local:wave/local/wavelet");
    Mockito.when(svc.getMetadata(aid)).thenReturn(meta);
    org.waveprotocol.box.server.persistence.AttachmentStore.AttachmentData data = new org.waveprotocol.box.server.persistence.AttachmentStore.AttachmentData() {
      @Override public InputStream getInputStream() { return new ByteArrayInputStream("OK".getBytes()); }
      @Override public long getSize() { return 2; }
    };
    Mockito.when(svc.getAttachment(aid)).thenReturn(data);

    URL url = new URL("http://localhost:" + port + AttachmentServlet.ATTACHMENT_URL + "/example.com/att123?waveRef=local:wave/local/wavelet");
    HttpURLConnection c = (HttpURLConnection) url.openConnection();
    assertEquals(200, c.getResponseCode());
  }

  @Test
  public void forbiddenWhenAuthorizedButNoPermission() throws Exception {
    ParticipantId user = new ParticipantId("user@example.com");
    Mockito.when(sm.getLoggedInUser(Mockito.any())).thenReturn(user);
    Mockito.when(wprov.checkAccessPermission(Mockito.any(WaveletName.class), Mockito.eq(user))).thenReturn(false);

    AttachmentId aid = AttachmentId.deserialise("att+nope");
    AttachmentMetadata meta = Mockito.mock(AttachmentMetadata.class);
    Mockito.when(meta.getMimeType()).thenReturn("text/plain");
    Mockito.when(meta.getFileName()).thenReturn("nope.txt");
    Mockito.when(meta.getWaveRef()).thenReturn("local:wave/secret/wavelet");
    Mockito.when(svc.getMetadata(aid)).thenReturn(meta);

    URL url = new URL("http://localhost:" + port + AttachmentServlet.ATTACHMENT_URL + "/att+nope?waveRef=local:wave/local/wavelet");
    HttpURLConnection c = (HttpURLConnection) url.openConnection();
    assertEquals(403, c.getResponseCode());
  }

  @Test
  public void ignoresWaveRefParamUsesMetadataForAuth() throws Exception {
    ParticipantId user = new ParticipantId("user@example.com");
    Mockito.when(sm.getLoggedInUser(Mockito.any())).thenReturn(user);
    // Return false regardless; we assert the wavelet used equals metadata's
    Mockito.when(wprov.checkAccessPermission(Mockito.any(WaveletName.class), Mockito.eq(user))).thenReturn(false);

    AttachmentId aid = AttachmentId.deserialise("att+meta");
    AttachmentMetadata meta = Mockito.mock(AttachmentMetadata.class);
    Mockito.when(meta.getMimeType()).thenReturn("text/plain");
    Mockito.when(meta.getFileName()).thenReturn("meta.txt");
    // Metadata points to a different wave than the request parameter
    String metadataWaveRef = "local:wave/other/wavelet";
    Mockito.when(meta.getWaveRef()).thenReturn(metadataWaveRef);
    Mockito.when(svc.getMetadata(aid)).thenReturn(meta);

    URL url = new URL("http://localhost:" + port + AttachmentServlet.ATTACHMENT_URL + "/att+meta?waveRef=local:wave/local/wavelet");
    HttpURLConnection c = (HttpURLConnection) url.openConnection();
    assertEquals(403, c.getResponseCode());

    ArgumentCaptor<WaveletName> cap = ArgumentCaptor.forClass(WaveletName.class);
    Mockito.verify(wprov).checkAccessPermission(cap.capture(), Mockito.eq(user));
    WaveletName used = cap.getValue();
    WaveletName expected = AttachmentUtil.waveRef2WaveletName(metadataWaveRef);
    assertEquals(expected, used);
  }

  @Test
  public void notFoundWhenMetadataMissingEvenIfWaveRefProvided() throws Exception {
    ParticipantId user = new ParticipantId("user@example.com");
    Mockito.when(sm.getLoggedInUser(Mockito.any())).thenReturn(user);
    AttachmentId aid = AttachmentId.deserialise("att+missing");
    Mockito.when(svc.getMetadata(aid)).thenReturn(null);

    URL url = new URL("http://localhost:" + port + AttachmentServlet.ATTACHMENT_URL + "/att+missing?waveRef=local:wave/local/wavelet");
    HttpURLConnection c = (HttpURLConnection) url.openConnection();
    assertEquals(404, c.getResponseCode());
    Mockito.verify(wprov, Mockito.never()).checkAccessPermission(Mockito.any(WaveletName.class), Mockito.any());
  }
}
