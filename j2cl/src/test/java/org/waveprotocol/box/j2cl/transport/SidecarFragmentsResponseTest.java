package org.waveprotocol.box.j2cl.transport;

import com.google.j2cl.junit.apt.J2clTestInput;
import org.junit.Assert;
import org.junit.Test;

@J2clTestInput(SidecarFragmentsResponseTest.class)
public class SidecarFragmentsResponseTest {
  @Test
  public void decodeFragmentsServletResponsePreservesRangesAndRawSnapshots() {
    SidecarFragmentsResponse response =
        SidecarFragmentsResponse.fromJson(
            "{\"status\":\"ok\",\"waveRef\":\"example.com/w+abc/~/conv+root\","
                + "\"version\":{\"snapshot\":44,\"start\":40,\"end\":44},"
                + "\"ranges\":[{\"segment\":\"manifest\",\"from\":40,\"to\":44},"
                + "{\"segment\":\"blip:b+root\",\"from\":41,\"to\":44}],"
                + "\"fragments\":[{\"segment\":\"manifest\",\"rawSnapshot\":\"metadata\","
                + "\"adjust\":[],\"diff\":[]},"
                + "{\"segment\":\"blip:b+root\",\"rawSnapshot\":\"Root text\","
                + "\"adjust\":[{}],\"diff\":[{},{}]}]}");

    Assert.assertEquals("ok", response.getStatus());
    Assert.assertEquals("example.com/w+abc/~/conv+root", response.getWaveRefPath());
    Assert.assertEquals(44L, response.getFragments().getSnapshotVersion());
    Assert.assertEquals(40L, response.getFragments().getStartVersion());
    Assert.assertEquals(44L, response.getFragments().getEndVersion());
    Assert.assertEquals(2, response.getFragments().getRanges().size());
    Assert.assertEquals(2, response.getFragments().getEntries().size());
    Assert.assertEquals(
        "blip:b+root", response.getFragments().getEntries().get(1).getSegment());
    Assert.assertEquals("Root text", response.getFragments().getEntries().get(1).getRawSnapshot());
    Assert.assertEquals(1, response.getFragments().getEntries().get(1).getAdjustOperationCount());
    Assert.assertEquals(2, response.getFragments().getEntries().get(1).getDiffOperationCount());
  }

  @Test
  public void decodeFragmentsServletResponseRejectsErrorStatus() {
    try {
      SidecarFragmentsResponse.fromJson("{\"status\":\"error\"}");
      Assert.fail("Expected error status to be rejected");
    } catch (IllegalArgumentException expected) {
      Assert.assertTrue(expected.getMessage().contains("status"));
    }
  }

  @Test
  public void decodeFragmentsServletResponseAllowsMissingRangesAndFragments() {
    SidecarFragmentsResponse response =
        SidecarFragmentsResponse.fromJson(
            "{\"status\":\"ok\",\"waveRef\":\"example.com/w+abc/~/conv+root\","
                + "\"version\":{\"snapshot\":44,\"start\":40,\"end\":44}}");

    Assert.assertTrue(response.getFragments().getRanges().isEmpty());
    Assert.assertTrue(response.getFragments().getEntries().isEmpty());
  }

  @Test
  public void decodeFragmentsServletResponsePreservesMissingRawSnapshotAsNull() {
    SidecarFragmentsResponse response =
        SidecarFragmentsResponse.fromJson(
            "{\"status\":\"ok\",\"waveRef\":\"example.com/w+abc/~/conv+root\","
                + "\"version\":{\"snapshot\":44,\"start\":40,\"end\":44},"
                + "\"fragments\":[{\"segment\":\"blip:b+pending\"}]}");

    Assert.assertNull(response.getFragments().getEntries().get(0).getRawSnapshot());
  }

  @Test
  public void decodeFragmentsServletResponseRequiresVersionObject() {
    try {
      SidecarFragmentsResponse.fromJson(
          "{\"status\":\"ok\",\"waveRef\":\"example.com/w+abc/~/conv+root\"}");
      Assert.fail("Expected missing version to be rejected");
    } catch (IllegalArgumentException expected) {
      Assert.assertTrue(expected.getMessage().contains("Expected object"));
    }
  }

  @Test
  public void decodeFragmentsServletResponseRejectsMalformedRangeEntry() {
    try {
      SidecarFragmentsResponse.fromJson(
          "{\"status\":\"ok\",\"waveRef\":\"example.com/w+abc/~/conv+root\","
              + "\"version\":{\"snapshot\":44,\"start\":40,\"end\":44},"
              + "\"ranges\":[\"not-an-object\"]}");
      Assert.fail("Expected malformed range entry to be rejected");
    } catch (IllegalArgumentException expected) {
      Assert.assertTrue(expected.getMessage().contains("Expected object"));
    }
  }
}
