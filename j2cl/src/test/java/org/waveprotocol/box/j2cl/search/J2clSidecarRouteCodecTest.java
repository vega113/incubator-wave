package org.waveprotocol.box.j2cl.search;

import com.google.j2cl.junit.apt.J2clTestInput;
import org.junit.Assert;
import org.junit.Test;

@J2clTestInput(J2clSidecarRouteCodecTest.class)
public class J2clSidecarRouteCodecTest {
  @Test
  public void blankOrMissingQueryFallsBackToDefault() {
    Assert.assertEquals(
        J2clSearchResultProjector.DEFAULT_QUERY,
        J2clSidecarRouteCodec.parse("").getQuery());
    Assert.assertEquals(
        J2clSearchResultProjector.DEFAULT_QUERY,
        J2clSidecarRouteCodec.parse("?q=").getQuery());
    Assert.assertEquals(
        J2clSearchResultProjector.DEFAULT_QUERY,
        J2clSidecarRouteCodec.parse("?q=%").getQuery());
  }

  @Test
  public void queryOnlyRouteRoundTripsToCanonicalUrl() {
    J2clSidecarRouteState state = J2clSidecarRouteCodec.parse("?q=with%3A%40");

    Assert.assertEquals("with:@", state.getQuery());
    Assert.assertNull(state.getSelectedWaveId());
    Assert.assertEquals(
        "?q=with%3A%40",
        J2clSidecarRouteCodec.toUrl(state));
  }

  @Test
  public void queryAndSelectedWaveRouteRoundTripsToCanonicalUrl() {
    J2clSidecarRouteState state =
        J2clSidecarRouteCodec.parse("?wave=example.com%2Fw%2Babc123&q=with%3A%40");

    Assert.assertEquals("with:@", state.getQuery());
    Assert.assertEquals("example.com/w+abc123", state.getSelectedWaveId());
    Assert.assertEquals(
        "?q=with%3A%40&wave=example.com%2Fw%2Babc123",
        J2clSidecarRouteCodec.toUrl(state));
  }

  @Test
  public void malformedOrIncompleteWaveParameterIsDroppedSafely() {
    Assert.assertNull(J2clSidecarRouteCodec.parse("?wave=").getSelectedWaveId());
    Assert.assertNull(J2clSidecarRouteCodec.parse("?wave=not-a-wave-id").getSelectedWaveId());
    Assert.assertNull(J2clSidecarRouteCodec.parse("?wave=bad%20value").getSelectedWaveId());
  }

  @Test
  public void waveParameterPreservesLiteralPlusInWaveId() {
    Assert.assertEquals(
        "example.com/w+abc123",
        J2clSidecarRouteCodec.parse("?wave=example.com/w+abc123").getSelectedWaveId());
  }

  @Test
  public void fullWaveRefParameterKeepsOnlyWaveIdPrefix() {
    Assert.assertEquals(
        "example.com/w+abc123",
        J2clSidecarRouteCodec.parse(
                "?wave=example.com/w+abc123/~/conv+root/b+1234")
            .getSelectedWaveId());
  }

  @Test
  public void queryDecodingTreatsPlusAsSpace() {
    Assert.assertEquals(
        "mentions:me unread:true",
        J2clSidecarRouteCodec.parse("?q=mentions%3Ame+unread%3Atrue").getQuery());
  }

  @Test
  public void legacyHashPlainWaveIdIsAccepted() {
    Assert.assertEquals(
        "example.com/w+abc123",
        J2clSidecarRouteCodec.parse("", "#example.com/w+abc123").getSelectedWaveId());
  }

  @Test
  public void legacyHashFullWaveRefTokenIsStrippedToWaveId() {
    // Full WaveRef tokens from /waveref/* include conversation and blip segments;
    // only the "domain/w+id" prefix should be returned.
    Assert.assertEquals(
        "example.com/w+abc123",
        J2clSidecarRouteCodec.parse("", "#example.com/w+abc123/~/conv+root/b+1234")
            .getSelectedWaveId());
  }

  @Test
  public void legacyHashAmpersandMetadataIsStripped() {
    // Legacy Wave history tokens can append focus/depth state with '&'; only the wave-id portion
    // should be returned.
    Assert.assertEquals(
        "example.com/w+abc123",
        J2clSidecarRouteCodec.parse("", "#example.com/w+abc123&focus=blip1")
            .getSelectedWaveId());
  }

  @Test
  public void legacyHashMissingOrInvalidIsNull() {
    Assert.assertNull(J2clSidecarRouteCodec.parse("", null).getSelectedWaveId());
    Assert.assertNull(J2clSidecarRouteCodec.parse("", "").getSelectedWaveId());
    Assert.assertNull(J2clSidecarRouteCodec.parse("", "#not-a-wave").getSelectedWaveId());
  }

  // --- F-2 slice 5 (#1055, R-3.7 G.4): depth URL parameter -----------

  @Test
  public void depthBlipIdParsesFromUrl() {
    J2clSidecarRouteState state =
        J2clSidecarRouteCodec.parse(
            "?q=with%3A%40&wave=example.com%2Fw%2B1&depth=b%2Bdrillin");
    Assert.assertEquals("with:@", state.getQuery());
    Assert.assertEquals("example.com/w+1", state.getSelectedWaveId());
    Assert.assertEquals("b+drillin", state.getDepthBlipId());
  }

  @Test
  public void depthBlipIdRoundTripsThroughToUrl() {
    J2clSidecarRouteState state =
        new J2clSidecarRouteState("with:@", "example.com/w+abc123", "b+leaf-blip");
    String url = J2clSidecarRouteCodec.toUrl(state);
    Assert.assertEquals(
        "?q=with%3A%40&wave=example.com%2Fw%2Babc123&depth=b%2Bleaf-blip", url);
  }

  @Test
  public void depthBlipIdEmptyOrMissingStaysNull() {
    Assert.assertNull(
        J2clSidecarRouteCodec.parse("?wave=example.com%2Fw%2B1").getDepthBlipId());
    Assert.assertNull(
        J2clSidecarRouteCodec.parse("?wave=example.com%2Fw%2B1&depth=").getDepthBlipId());
    Assert.assertNull(
        J2clSidecarRouteCodec.parse("?wave=example.com%2Fw%2B1&depth=%20")
            .getDepthBlipId());
  }

  @Test
  public void depthIsOmittedFromUrlWhenAbsent() {
    // Absence of depth means a clean ?q=&wave=... URL, no trailing &depth=.
    J2clSidecarRouteState state =
        new J2clSidecarRouteState("with:@", "example.com/w+abc123");
    String url = J2clSidecarRouteCodec.toUrl(state);
    Assert.assertEquals(
        "?q=with%3A%40&wave=example.com%2Fw%2Babc123", url);
  }

  @Test
  public void withDepthBlipIdProducesMatchingState() {
    J2clSidecarRouteState base =
        new J2clSidecarRouteState("with:@", "example.com/w+abc");
    J2clSidecarRouteState withDepth = base.withDepthBlipId("b+anchor");
    Assert.assertEquals("b+anchor", withDepth.getDepthBlipId());
    Assert.assertEquals(base.getQuery(), withDepth.getQuery());
    Assert.assertEquals(base.getSelectedWaveId(), withDepth.getSelectedWaveId());
    // Clearing the depth produces an equal-to-base state.
    Assert.assertEquals(base, withDepth.withDepthBlipId(null));
    Assert.assertEquals(base, withDepth.withDepthBlipId(""));
  }
}
