package org.waveprotocol.box.j2cl.search;

import com.google.j2cl.junit.apt.J2clTestInput;
import java.lang.reflect.Method;
import org.junit.Assert;
import org.junit.Test;
import org.waveprotocol.box.j2cl.transport.SidecarSessionBootstrap;
import org.waveprotocol.box.j2cl.transport.SidecarSubmitRequest;
import org.waveprotocol.box.j2cl.transport.SidecarTransportCodec;
import org.waveprotocol.box.j2cl.transport.SidecarViewportHints;

/**
 * Issue #933 regression coverage for the first outbound J2CL sidecar frames.
 * Sidecar auth is now established entirely by the WebSocket upgrade handshake
 * (the server resolves loggedInUser from the HttpSession before any frames are
 * exchanged), so the selected-wave and submit sockets must begin with
 * ProtocolOpenRequest / ProtocolSubmitRequest instead of an auth envelope.
 *
 * <p>This is a static-contract regression, not a behavioural test of the
 * gateways — it cannot catch a future regression that hand-inlines a cookie
 * read in {@link J2clSearchGateway} or the sandbox sidecar. For that defence,
 * see the local-browser verification recorded under
 * {@code journal/local-verification/2026-04-23-issue-933-sidecar-ws-auth.md},
 * which inspects the live {@code /socket} frames for the absence of a
 * {@code ProtocolAuthenticate} payload.
 */
@J2clTestInput(J2clSearchGatewayAuthFrameTest.class)
public class J2clSearchGatewayAuthFrameTest {
  @Test
  public void selectedWaveSocketStartsWithProtocolOpenRequest() {
    String frame =
        J2clSearchGateway.buildSelectedWaveOpenFrame(
            new SidecarSessionBootstrap("rose@example.com", "socket.example.test"),
            "example.com/w+abc");

    Assert.assertEquals("ProtocolOpenRequest", SidecarTransportCodec.decodeMessageType(frame));
    Assert.assertFalse(frame.contains("ProtocolAuthenticate"));
  }

  @Test
  public void selectedWaveOpenFrameCanCarryExplicitDefaultViewportLimit() {
    String frame =
        J2clSearchGateway.buildSelectedWaveOpenFrame(
            new SidecarSessionBootstrap("rose@example.com", "socket.example.test"),
            "example.com/w+abc",
            new SidecarViewportHints(null, null, Integer.valueOf(0)));

    Assert.assertEquals("ProtocolOpenRequest", SidecarTransportCodec.decodeMessageType(frame));
    Assert.assertTrue(frame.contains("\"7\":0"));
  }

  @Test
  public void selectedWaveOpenFrameCanCarryServerFirstViewportAnchor() {
    String frame =
        J2clSearchGateway.buildSelectedWaveOpenFrame(
            new SidecarSessionBootstrap("rose@example.com", "socket.example.test"),
            "example.com/w+abc",
            new SidecarViewportHints("b+root", "forward", null));

    Assert.assertEquals("ProtocolOpenRequest", SidecarTransportCodec.decodeMessageType(frame));
    Assert.assertTrue(frame.contains("\"5\":\"b+root\""));
    Assert.assertTrue(frame.contains("\"6\":\"forward\""));
    Assert.assertFalse(frame.contains("\"7\""));
  }

  @Test
  public void submitSocketStartsWithProtocolSubmitRequest() {
    String frame =
        J2clSearchGateway.buildSubmitFrame(
            new SidecarSubmitRequest(
                "example.com/w+abc/conv+root",
                "{\"ops\":[]}",
                "channel-7"));

    Assert.assertEquals("ProtocolSubmitRequest", SidecarTransportCodec.decodeMessageType(frame));
    Assert.assertFalse(frame.contains("ProtocolAuthenticate"));
  }

  @Test
  public void fragmentsFetchUrlCarriesViewportWindowAndDefaultWavelet() {
    String url =
        J2clSearchGateway.buildFragmentsUrl(
            "example.com/w+abc", "b+root", "backward", 12, 40L, 44L);

    Assert.assertTrue(url.startsWith("/fragments?"));
    Assert.assertTrue(url.contains("waveId=example.com%2Fw%2Babc"));
    Assert.assertTrue(url.contains("waveletId=example.com%2Fconv%2Broot"));
    Assert.assertTrue(url.contains("client=j2cl"));
    Assert.assertTrue(url.contains("startBlipId=b%2Broot"));
    Assert.assertTrue(url.contains("direction=backward"));
    Assert.assertTrue(url.contains("limit=12"));
    Assert.assertTrue(url.contains("startVersion=40"));
    Assert.assertTrue(url.contains("endVersion=44"));
  }

  @Test
  public void fragmentsFetchUrlOmitsEmptyAnchorAndDefaultsDirection() {
    String url =
        J2clSearchGateway.buildFragmentsUrl(
            "example.com/w+abc", "", null, 5, 40L, 44L);

    Assert.assertFalse(url.contains("startBlipId="));
    Assert.assertTrue(url.contains("direction=forward"));
    Assert.assertTrue(url.contains("client=j2cl"));
  }

  @Test
  public void fragmentsFetchUrlFallsBackToRootWaveletForDomainlessWaveId() {
    String url =
        J2clSearchGateway.buildFragmentsUrl(
            "w+abc", "b+root", "forward", 5, 40L, 44L);

    Assert.assertTrue(url.contains("waveId=w%2Babc"));
    Assert.assertTrue(url.contains("waveletId=conv%2Broot"));
  }

  @Test
  public void websocketCookieHostMustMatchCurrentPageHost() {
    Assert.assertTrue(
        SidecarSessionBootstrap.usesCompatibleCookieHost(
            "wave.example.com", "wave.example.com:7443"));
    Assert.assertTrue(
        SidecarSessionBootstrap.usesCompatibleCookieHost(
            "wave.example.com", "wave.example.com"));
    Assert.assertFalse(
        SidecarSessionBootstrap.usesCompatibleCookieHost(
            "wave.example.com", "socket.example.com:7443"));
    Assert.assertTrue(
        SidecarSessionBootstrap.usesCompatibleCookieHost(
            "[2001:db8::1]", "[2001:db8::1]:7443"));
  }

  @Test
  public void transportCodecDoesNotExposeAuthenticateEnvelopeHelper() {
    for (Method method : SidecarTransportCodec.class.getDeclaredMethods()) {
      Assert.assertFalse(
          "SidecarTransportCodec must not expose encodeAuthenticateEnvelope; "
              + "sidecar auth is handled by the WebSocket upgrade handshake (#933)",
          "encodeAuthenticateEnvelope".equals(method.getName()));
    }
  }
}
