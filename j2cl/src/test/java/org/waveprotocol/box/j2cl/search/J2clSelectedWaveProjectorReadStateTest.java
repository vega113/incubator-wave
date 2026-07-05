package org.waveprotocol.box.j2cl.search;

import com.google.j2cl.junit.apt.J2clTestInput;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Assert;
import org.junit.Test;
import org.waveprotocol.box.j2cl.read.J2clReadBlip;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveDocument;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveFragment;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveFragmentRange;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveFragments;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveReadState;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveUpdate;

/**
 * #1270: split from the monster J2clSelectedWaveProjectorTest. Covers
 * server read-state projection, unread-blip application and stale-count annotation.
 */
@J2clTestInput(J2clSelectedWaveProjectorReadStateTest.class)
public class J2clSelectedWaveProjectorReadStateTest extends J2clSelectedWaveProjectorTestSupport {

  // -- Read-state projection (issue #931) -------------------------------------

  @Test
  public void projectUsesServerReadStateWhenPresent() {
    J2clSelectedWaveModel projected =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 2),
            sampleUpdate(),
            null,
            0,
            new SidecarSelectedWaveReadState(WAVE_ID, 5, false),
            false);

    Assert.assertTrue(projected.isReadStateKnown());
    Assert.assertEquals(5, projected.getUnreadCount());
    Assert.assertEquals("5 unread.", projected.getUnreadText());
  }

  @Test
  public void projectFallsBackToDigestWhenServerReadStateAbsent() {
    J2clSelectedWaveModel projected =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 3),
            sampleUpdate(),
            null,
            0);

    Assert.assertFalse(projected.isReadStateKnown());
    Assert.assertEquals("3 unread in the selected digest.", projected.getUnreadText());
  }

  @Test
  public void projectCarriesForwardPreviousServerReadStateAcrossUpdates() {
    J2clSelectedWaveModel first =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 0),
            sampleUpdate(),
            null,
            0,
            new SidecarSelectedWaveReadState(WAVE_ID, 2, false),
            false);

    J2clSelectedWaveModel second =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 0),
            sampleUpdate(),
            first,
            0);

    Assert.assertTrue(second.isReadStateKnown());
    Assert.assertEquals(2, second.getUnreadCount());
    Assert.assertEquals("2 unread.", second.getUnreadText());
  }

  @Test
  public void projectRendersReadWhenServerReportsZero() {
    J2clSelectedWaveModel projected =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 7),
            sampleUpdate(),
            null,
            0,
            new SidecarSelectedWaveReadState(WAVE_ID, 0, true),
            false);

    Assert.assertTrue(projected.isReadStateKnown());
    Assert.assertTrue(projected.isRead());
    Assert.assertEquals("Read.", projected.getUnreadText());
  }

  @Test
  public void projectAppliesServerUnreadBlipIdsToReadModels() {
    J2clSelectedWaveModel projected =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 0),
            fragmentUpdate("b+root", "Root text", "b+reply", "Reply text"),
            null,
            0,
            new SidecarSelectedWaveReadState(
                WAVE_ID, 1, false, Arrays.asList("b+reply")),
            false);

    Assert.assertEquals(2, projected.getReadBlips().size());
    Assert.assertFalse(projected.getReadBlips().get(0).isUnread());
    Assert.assertEquals("b+reply", projected.getReadBlips().get(1).getBlipId());
    Assert.assertTrue(projected.getReadBlips().get(1).isUnread());
  }

  @Test
  public void reprojectReadStateAppliesServerUnreadBlipIdsToExistingReadModels() {
    J2clSelectedWaveModel projected =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 0),
            fragmentUpdate("b+root", "Root text", "b+reply", "Reply text"),
            null,
            0);

    J2clSelectedWaveModel reprojected =
        J2clSelectedWaveProjector.reprojectReadState(
            projected,
            digest("Wave A", "snippet", 1),
            new SidecarSelectedWaveReadState(
                WAVE_ID, 1, false, Arrays.asList("b+reply")),
            false);

    Assert.assertEquals(2, reprojected.getReadBlips().size());
    Assert.assertFalse(reprojected.getReadBlips().get(0).isUnread());
    Assert.assertTrue(reprojected.getReadBlips().get(1).isUnread());
  }

  @Test
  public void applyReadStatePreservesExistingBlipMarkersWhenUnreadIdsAreAbsent() {
    java.util.List<J2clReadBlip> blips =
        Arrays.asList(
            new J2clReadBlip("b+root", "Root").withUnread(false),
            new J2clReadBlip("b+reply", "Reply").withUnread(true));

    java.util.List<J2clReadBlip> projected =
        J2clSelectedWaveProjector.applyReadStateToReadBlips(
            blips, new SidecarSelectedWaveReadState(WAVE_ID, 1, false));

    Assert.assertSame(blips, projected);
    Assert.assertFalse(projected.get(0).isUnread());
    Assert.assertTrue(projected.get(1).isUnread());
  }

  @Test
  public void staleFlagPreservesPriorCountAndAnnotatesStatus() {
    J2clSelectedWaveModel fresh =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 0),
            sampleUpdate(),
            null,
            0,
            new SidecarSelectedWaveReadState(WAVE_ID, 4, false),
            false);

    J2clSelectedWaveModel stale =
        J2clSelectedWaveProjector.reprojectReadState(fresh, null, null, true);

    Assert.assertTrue(stale.isReadStateStale());
    Assert.assertEquals(4, stale.getUnreadCount());
    Assert.assertEquals("4 unread.", stale.getUnreadText());
  }

  @Test
  public void projectDoesNotAnnotateStaleStatusWhenReadStateIsUnknown() {
    J2clSelectedWaveModel projected =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 3),
            sampleUpdate(),
            null,
            0,
            null,
            true);

    Assert.assertFalse(projected.isReadStateKnown());
    Assert.assertFalse(projected.isReadStateStale());
    Assert.assertEquals("Live updates connected.", projected.getStatusText());
  }

  @Test
  public void projectKeepsStableReadBlipIdsFromFragmentSegments() {
    J2clSelectedWaveModel projected =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 0),
            new SidecarSelectedWaveUpdate(
                1,
                WAVELET_NAME,
                true,
                CHANNEL_ID,
                9L,
                "HASH",
                Arrays.asList("user@example.com"),
                Collections.<SidecarSelectedWaveDocument>emptyList(),
                new SidecarSelectedWaveFragments(
                    9L,
                    0L,
                    9L,
                    Arrays.asList(
                        new SidecarSelectedWaveFragmentRange("blip:b+root", 0L, 9L),
                        new SidecarSelectedWaveFragmentRange("blip:b+reply", 0L, 9L)),
                    Arrays.asList(
                        new SidecarSelectedWaveFragment("blip:b+root", "Root text", 0, 0),
                        new SidecarSelectedWaveFragment("blip:b+reply", "Reply text", 0, 0)))),
            null,
            0);

    Assert.assertEquals(2, projected.getReadBlips().size());
    Assert.assertEquals("b+root", projected.getReadBlips().get(0).getBlipId());
    Assert.assertEquals("Root text", projected.getReadBlips().get(0).getText());
    Assert.assertEquals("b+reply", projected.getReadBlips().get(1).getBlipId());
    Assert.assertEquals("Reply text", projected.getReadBlips().get(1).getText());
  }
}
