package org.waveprotocol.box.j2cl.search;

import com.google.j2cl.junit.apt.J2clTestInput;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Assert;
import org.junit.Test;
import org.waveprotocol.box.j2cl.read.J2clReadBlip;
import org.waveprotocol.box.j2cl.transport.SidecarConversationManifest;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveDocument;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveUpdate;

/**
 * #1270: split from the monster J2clSelectedWaveProjectorTest. Covers
 * lock-state carry/drop and conversation-manifest DFS reordering/grafting.
 */
@J2clTestInput(J2clSelectedWaveProjectorLockAndManifestTest.class)
public class J2clSelectedWaveProjectorLockAndManifestTest extends J2clSelectedWaveProjectorTestSupport {

  @Test
  public void projectCarriesLockStateFromLockDocument() {
    J2clSelectedWaveModel projected =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 0),
            new SidecarSelectedWaveUpdate(
                1,
                WAVELET_NAME,
                true,
                CHANNEL_ID,
                44L,
                "HASH",
                Arrays.asList("user@example.com"),
                Arrays.asList(lockDocument("root")),
                null),
            null,
            0);

    Assert.assertEquals("root", projected.getLockState());
  }

  @Test
  public void projectPreservesPreviousLockStateWhenSameWaveUpdateOmitsLockDocument() {
    J2clSelectedWaveModel previous =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 0),
            new SidecarSelectedWaveUpdate(
                1,
                WAVELET_NAME,
                true,
                CHANNEL_ID,
                44L,
                "HASH",
                Arrays.asList("user@example.com"),
                Arrays.asList(lockDocument("all")),
                null),
            null,
            0);

    J2clSelectedWaveModel liveUpdate =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 0),
            new SidecarSelectedWaveUpdate(
                2,
                WAVELET_NAME,
                true,
                CHANNEL_ID,
                45L,
                "HASH2",
                Arrays.asList("user@example.com"),
                Collections.<SidecarSelectedWaveDocument>emptyList(),
                null),
            previous,
            0);

    Assert.assertEquals("all", liveUpdate.getLockState());
  }

  @Test
  public void projectDropsPreviousLockStateAcrossWaveSwitch() {
    J2clSelectedWaveModel previous =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 0),
            new SidecarSelectedWaveUpdate(
                1,
                WAVELET_NAME,
                true,
                CHANNEL_ID,
                44L,
                "HASH",
                Arrays.asList("user@example.com"),
                Arrays.asList(lockDocument("root")),
                null),
            null,
            0);

    J2clSelectedWaveModel switched =
        J2clSelectedWaveProjector.project(
            "example.com/w+2",
            null,
            new SidecarSelectedWaveUpdate(
                2,
                WAVELET_NAME_2,
                true,
                CHANNEL_ID,
                45L,
                "HASH2",
                Arrays.asList("user@example.com"),
                Collections.<SidecarSelectedWaveDocument>emptyList(),
                null),
            previous,
            0);

    Assert.assertEquals("unlocked", switched.getLockState());
  }

  // ─── J-UI-4 (#1082, R-3.1) — applyConversationManifest ────────────────

  @Test
  public void applyConversationManifestIsNoOpWhenManifestEmpty() {
    java.util.List<J2clReadBlip> blips =
        Arrays.asList(
            new J2clReadBlip("b+a", "alpha"), new J2clReadBlip("b+b", "beta"));
    java.util.List<J2clReadBlip> result =
        J2clSelectedWaveProjector.applyConversationManifest(
            blips, SidecarConversationManifest.empty());
    Assert.assertSame(blips, result);
  }

  @Test
  public void applyConversationManifestReordersBlipsIntoManifestDfsOrder() {
    // Manifest order: b+second, b+first  (the input list is in arrival
    // order; the projector must hand back manifest order).
    SidecarConversationManifest manifest =
        SidecarConversationManifest.of(
            Arrays.asList(
                new SidecarConversationManifest.Entry("b+second", "", "root", 0, 0),
                new SidecarConversationManifest.Entry("b+first", "", "root", 0, 1)));
    java.util.List<J2clReadBlip> blips =
        Arrays.asList(new J2clReadBlip("b+first", "alpha"), new J2clReadBlip("b+second", "beta"));

    java.util.List<J2clReadBlip> result =
        J2clSelectedWaveProjector.applyConversationManifest(blips, manifest);

    Assert.assertEquals(2, result.size());
    Assert.assertEquals("b+second", result.get(0).getBlipId());
    Assert.assertEquals("beta", result.get(0).getText());
    Assert.assertEquals("b+first", result.get(1).getBlipId());
    Assert.assertEquals("alpha", result.get(1).getText());
  }

  @Test
  public void applyConversationManifestGraftsParentAndThreadOnNestedBlip() {
    SidecarConversationManifest manifest =
        SidecarConversationManifest.of(
            Arrays.asList(
                new SidecarConversationManifest.Entry("b+parent", "", "root", 0, 0),
                new SidecarConversationManifest.Entry("b+child", "b+parent", "t+reply", 1, 0)));
    java.util.List<J2clReadBlip> blips =
        Arrays.asList(
            new J2clReadBlip("b+parent", "p"), new J2clReadBlip("b+child", "c"));

    java.util.List<J2clReadBlip> result =
        J2clSelectedWaveProjector.applyConversationManifest(blips, manifest);

    Assert.assertEquals(2, result.size());
    Assert.assertEquals("", result.get(0).getParentBlipId());
    Assert.assertEquals("root", result.get(0).getThreadId());
    Assert.assertEquals("b+parent", result.get(1).getParentBlipId());
    Assert.assertEquals("t+reply", result.get(1).getThreadId());
  }

  @Test
  public void applyConversationManifestEmitsPlaceholderForOrphanedManifestEntry() {
    SidecarConversationManifest manifest =
        SidecarConversationManifest.of(
            Arrays.asList(
                new SidecarConversationManifest.Entry("b+missing", "", "root", 0, 0)));

    java.util.List<J2clReadBlip> result =
        J2clSelectedWaveProjector.applyConversationManifest(
            Collections.<J2clReadBlip>emptyList(), manifest);

    Assert.assertEquals(1, result.size());
    Assert.assertEquals("b+missing", result.get(0).getBlipId());
    Assert.assertEquals("", result.get(0).getText());
    Assert.assertEquals("", result.get(0).getParentBlipId());
    Assert.assertEquals("root", result.get(0).getThreadId());
  }

  @Test
  public void applyConversationManifestAppendsExtraBlipsNotReferencedByManifest() {
    SidecarConversationManifest manifest =
        SidecarConversationManifest.of(
            Arrays.asList(
                new SidecarConversationManifest.Entry("b+tracked", "", "root", 0, 0)));
    java.util.List<J2clReadBlip> blips =
        Arrays.asList(
            new J2clReadBlip("b+tracked", "tracked"),
            new J2clReadBlip("b+stray", "stray"));

    java.util.List<J2clReadBlip> result =
        J2clSelectedWaveProjector.applyConversationManifest(blips, manifest);

    Assert.assertEquals(2, result.size());
    Assert.assertEquals("b+tracked", result.get(0).getBlipId());
    Assert.assertEquals("b+stray", result.get(1).getBlipId());
  }
}
