package org.waveprotocol.box.server.jakarta;

import com.google.gson.JsonParser;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.rpc.ProtoSerializer;
import org.waveprotocol.box.server.rpc.SearchServlet;
import org.waveprotocol.box.server.robots.OperationServiceRegistry;
import org.waveprotocol.box.server.robots.util.ConversationUtil;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import com.google.wave.api.data.converter.EventDataConverterManager;
import com.google.wave.api.SearchResult;
import com.google.wave.api.SearchResult.Digest;
import com.google.gson.JsonElement;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import java.net.HttpURLConnection;
import java.net.URL;

import static org.junit.Assert.*;

public class SearchServletJakartaIT {
  private Server server;
  private int port;
  private SessionManager sm;
  private EventDataConverterManager conv;
  private OperationServiceRegistry reg;
  private WaveletProvider wprov;
  private ConversationUtil convUtil;
  private ProtoSerializer serializer;

  @Before
  public void start() throws Exception {
    TestSupport.assumeJettyEe10PresentOrSkip();
    sm = Mockito.mock(SessionManager.class);
    conv = Mockito.mock(EventDataConverterManager.class, Mockito.RETURNS_DEEP_STUBS);
    reg = Mockito.mock(OperationServiceRegistry.class);
    wprov = Mockito.mock(WaveletProvider.class);
    convUtil = Mockito.mock(ConversationUtil.class);
    serializer = Mockito.mock(ProtoSerializer.class);
    Mockito.when(serializer.toJson(Mockito.any())).thenReturn(JsonParser.parseString("{\"ok\":true}"));

    server = new Server();
    ServerConnector c = new ServerConnector(server);
    c.setPort(0);
    server.addConnector(c);
    ServletContextHandler ctx = new ServletContextHandler(ServletContextHandler.SESSIONS);
    ctx.setContextPath("/");
    ctx.addServlet(new org.eclipse.jetty.ee10.servlet.ServletHolder(
        new SearchServlet(sm, conv, reg, wprov, convUtil, serializer)), "/search/*");
    server.setHandler(ctx);
    server.start();
    port = c.getLocalPort();
  }

  @After
  public void stop() throws Exception {
    if (server != null) server.stop();
  }

  @Test
  public void searchRequiresLogin() throws Exception {
    Mockito.when(sm.getLoggedInUser(Mockito.any())).thenReturn(null);
    URL url = new URL("http://localhost:" + port + "/search/?query=in:inbox&index=0&numResults=1");
    HttpURLConnection c = (HttpURLConnection) url.openConnection();
    assertEquals(403, c.getResponseCode());
  }

  @Test
  public void searchReturnsJsonOnSuccess() throws Exception {
    Mockito.when(sm.getLoggedInUser(Mockito.any())).thenReturn(new org.waveprotocol.wave.model.wave.ParticipantId("user@example.com"));
    // Stub the Operation pipeline result by short-circuiting performSearch via registry mocks:
    // We rely on serializer mock to return a simple JSON string, so just ensure 200/JSON here.
    URL url = new URL("http://localhost:" + port + "/search/?query=in:inbox&index=0&numResults=1");
    HttpURLConnection c = (HttpURLConnection) url.openConnection();
    assertEquals(200, c.getResponseCode());
    assertTrue(c.getHeaderField("Content-Type").contains("application/json"));
  }

  @Test
  public void serializerFailureReturns500() throws Exception {
    Mockito.when(sm.getLoggedInUser(Mockito.any())).thenReturn(new org.waveprotocol.wave.model.wave.ParticipantId("user@example.com"));
    Mockito.when(serializer.toJson(Mockito.any())).thenThrow(new org.waveprotocol.box.server.rpc.ProtoSerializer.SerializationException("boom"));
    URL url = new URL("http://localhost:" + port + "/search/?query=in:inbox&index=0&numResults=1");
    HttpURLConnection c = (HttpURLConnection) url.openConnection();
    assertEquals(500, c.getResponseCode());
  }

  @Test
  public void nonNumericParamsReturn400() throws Exception {
    Mockito.when(sm.getLoggedInUser(Mockito.any())).thenReturn(new org.waveprotocol.wave.model.wave.ParticipantId("user@example.com"));
    URL url = new URL("http://localhost:" + port + "/search/?query=in:all&index=abc&numResults=xyz");
    HttpURLConnection c = (HttpURLConnection) url.openConnection();
    assertEquals(400, c.getResponseCode());
  }

  @Test
  public void outOfRangeParamsAreClampedAnd200() throws Exception {
    Mockito.when(sm.getLoggedInUser(Mockito.any())).thenReturn(new org.waveprotocol.wave.model.wave.ParticipantId("user@example.com"));
    URL url = new URL("http://localhost:" + port + "/search/?query=in:all&index=-5&numResults=100000");
    HttpURLConnection c = (HttpURLConnection) url.openConnection();
    assertEquals(200, c.getResponseCode());
  }

  // ---- Unit-style validation of parser via reflection on private static method ----

  @Test
  public void parseSearchRequest_nonNumericThrows() throws Exception {
    HttpServletRequest req = mockedParams(new HashMap<String, String>() {{
      put("query", "in:all");
      put("index", "abc");
      put("numResults", "xyz");
    }});
    try {
      invokeParse(req);
      fail("expected IllegalArgumentException");
    } catch (java.lang.reflect.InvocationTargetException ite) {
      assertTrue(ite.getCause() instanceof IllegalArgumentException);
    }
  }

  @Test
  public void parseSearchRequest_outOfRangeClamped() throws Exception {
    HttpServletRequest req = mockedParams(new HashMap<String, String>() {{
      put("query", "in:all");
      put("index", "-5");
      put("numResults", "100000");
    }});
    org.waveprotocol.box.search.SearchProto.SearchRequest r = invokeParse(req);
    assertEquals(0, r.getIndex());
    assertEquals(100, r.getNumResults());
  }

  @Test
  public void parseSearchRequest_longOverflowThrows() throws Exception {
    HttpServletRequest req = mockedParams(new HashMap<String, String>() {{
      put("query", "in:all");
      put("index", "9223372036854775807");
      put("numResults", "-999999999999");
    }});
    try {
      invokeParse(req);
      fail("expected IllegalArgumentException");
    } catch (java.lang.reflect.InvocationTargetException ite) {
      assertTrue(ite.getCause() instanceof IllegalArgumentException);
    }
  }

  @Test
  public void serializeEscapesQueryInjection() throws Exception {
    String inj = "in:inbox\" , \"extra\":\"x\" </script>";
    SearchResult sr = new SearchResult(inj);
    org.waveprotocol.box.server.rpc.SearchServlet.serializeSearchResult(sr, 0);
    // Serialize and verify the query round-trips as a JSON string value
    org.waveprotocol.box.search.SearchProto.SearchResponse resp = org.waveprotocol.box.server.rpc.SearchServlet.serializeSearchResult(sr, 0);
    org.waveprotocol.box.server.rpc.ProtoSerializer real = new org.waveprotocol.box.server.rpc.ProtoSerializer();
    JsonElement json = real.toJson(resp);
    assertEquals(inj, json.getAsJsonObject().get("query").getAsString());
  }

  private static org.waveprotocol.box.search.SearchProto.SearchRequest invokeParse(HttpServletRequest req) throws Exception {
    Method m = org.waveprotocol.box.server.rpc.SearchServlet.class.getDeclaredMethod("parseSearchRequest", HttpServletRequest.class);
    m.setAccessible(true);
    return (org.waveprotocol.box.search.SearchProto.SearchRequest) m.invoke(null, req);
  }

  private static HttpServletRequest mockedParams(Map<String,String> params) {
    HttpServletRequest req = Mockito.mock(HttpServletRequest.class);
    Mockito.when(req.getParameter(Mockito.anyString())).thenAnswer((Answer<String>) inv -> {
      String key = (String) inv.getArguments()[0];
      return params.get(key);
    });
    return req;
  }
}
