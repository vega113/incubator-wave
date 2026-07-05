package org.waveprotocol.box.j2cl.search;

import com.google.j2cl.junit.apt.J2clTestInput;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Assert;
import org.junit.Test;
import org.waveprotocol.box.j2cl.overlay.J2clInteractionBlipModel;
import org.waveprotocol.box.j2cl.transport.SidecarAnnotationRange;
import org.waveprotocol.box.j2cl.transport.SidecarConversationManifest;
import org.waveprotocol.box.j2cl.transport.SidecarReactionEntry;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveDocument;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveFragment;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveFragmentRange;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveFragments;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveUpdate;

/**
 * #1270: split from the monster J2clSelectedWaveProjectorTest. Covers
 * write-session derivation: coupled version/hash, manifest insert offsets and retargeting.
 */
@J2clTestInput(J2clSelectedWaveProjectorWriteSessionTest.class)
public class J2clSelectedWaveProjectorWriteSessionTest extends J2clSelectedWaveProjectorTestSupport {

  // -- Write-session coupling (pre-existing) ----------------------------------

  @Test
  public void advancesWriteSessionWhenUpdateCarriesCoupledVersionAndHash() {
    J2clSelectedWaveModel previous = modelWithWriteSession(44L, "ABCD");

    J2clSelectedWaveModel result =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            null,
            updateWithVersionAndHash(50L, "EFGH"),
            previous,
            0);

    J2clSidecarWriteSession writeSession = result.getWriteSession();
    Assert.assertNotNull(writeSession);
    Assert.assertEquals(50L, writeSession.getBaseVersion());
    Assert.assertEquals("EFGH", writeSession.getHistoryHash());
  }

  @Test
  public void preservesPreviousPairWhenUpdateOmitsHistoryHash() {
    J2clSelectedWaveModel previous = modelWithWriteSession(44L, "ABCD");

    J2clSidecarWriteSession nullHash =
        J2clSelectedWaveProjector.project(
                WAVE_ID, null, updateWithVersionAndHash(50L, null), previous, 0)
            .getWriteSession();
    Assert.assertNotNull(nullHash);
    Assert.assertEquals(44L, nullHash.getBaseVersion());
    Assert.assertEquals("ABCD", nullHash.getHistoryHash());

    J2clSidecarWriteSession emptyHash =
        J2clSelectedWaveProjector.project(
                WAVE_ID, null, updateWithVersionAndHash(50L, ""), previous, 0)
            .getWriteSession();
    Assert.assertNotNull(emptyHash);
    Assert.assertEquals(44L, emptyHash.getBaseVersion());
    Assert.assertEquals("ABCD", emptyHash.getHistoryHash());
  }

  @Test
  public void preservesPreviousPairWhenUpdateHasNoResultingVersion() {
    J2clSelectedWaveModel previous = modelWithWriteSession(44L, "ABCD");

    SidecarSelectedWaveUpdate update =
        new SidecarSelectedWaveUpdate(
            2,
            WAVELET_NAME,
            true,
            CHANNEL_ID,
            -1L,
            null,
            Arrays.asList("user@example.com"),
            Arrays.asList(
                new SidecarSelectedWaveDocument(
                    "b+root", "user@example.com", 60L, 61L, "Later content")),
            new SidecarSelectedWaveFragments(
                70L,
                50L,
                70L,
                Arrays.asList(
                    new SidecarSelectedWaveFragmentRange("blip:b+root", 50L, 70L)),
                Arrays.asList(
                    new SidecarSelectedWaveFragment("blip:b+root", "Later content", 0, 0))));

    J2clSidecarWriteSession writeSession =
        J2clSelectedWaveProjector.project(WAVE_ID, null, update, previous, 0).getWriteSession();

    Assert.assertNotNull(writeSession);
    Assert.assertEquals(44L, writeSession.getBaseVersion());
    Assert.assertEquals("ABCD", writeSession.getHistoryHash());
  }

  @Test
  public void returnsNullWriteSessionWhenNoPreviousAndUpdateLacksCoupledPair() {
    SidecarSelectedWaveUpdate noVersion =
        new SidecarSelectedWaveUpdate(
            1,
            WAVELET_NAME,
            true,
            CHANNEL_ID,
            -1L,
            "ABCD",
            Arrays.asList("user@example.com"),
            Arrays.asList(
                new SidecarSelectedWaveDocument(
                    "b+root", "user@example.com", 1L, 2L, "Bootstrap")),
            null);
    Assert.assertNull(
        J2clSelectedWaveProjector.project(WAVE_ID, null, noVersion, null, 0).getWriteSession());

    SidecarSelectedWaveUpdate noHash = updateWithVersionAndHash(5L, null);
    Assert.assertNull(
        J2clSelectedWaveProjector.project(WAVE_ID, null, noHash, null, 0).getWriteSession());
  }

  @Test
  public void projectRefreshesParticipantContextWhenCarryingForwardPreviousInteractionBlips() {
    J2clSelectedWaveModel first =
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
                Arrays.asList("alice@example.com"),
                Arrays.asList(
                    new SidecarSelectedWaveDocument(
                        "b+root",
                        "author@example.com",
                        7L,
                        8L,
                        "Root text",
                        Arrays.asList(
                            new SidecarAnnotationRange("task/id", "task-123", 0, 4)),
                        Collections.<SidecarReactionEntry>emptyList())),
                null),
            null,
            0);

    J2clSelectedWaveModel second =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 0),
            new SidecarSelectedWaveUpdate(
                2,
                WAVELET_NAME,
                true,
                CHANNEL_ID,
                10L,
                "HASH-2",
                Arrays.asList("alice@example.com", "bob@example.com"),
                Arrays.asList(
                    new SidecarSelectedWaveDocument(
                        "react+b+root",
                        "author@example.com",
                        7L,
                        8L,
                        "",
                        Collections.<SidecarAnnotationRange>emptyList(),
                        Arrays.asList(
                            new SidecarReactionEntry(
                                "tada", Arrays.asList("alice@example.com"))))),
                null),
            first,
            0);

    Assert.assertEquals(1, second.getInteractionBlips().size());
    J2clInteractionBlipModel blip = second.getInteractionBlips().get(0);
    Assert.assertEquals(
        Arrays.asList("alice@example.com", "bob@example.com"),
        blip.getParticipantContext());
    Assert.assertTrue(blip.isEditable());
    Assert.assertEquals(1, blip.getTaskItems().size());
    Assert.assertTrue(blip.getTaskItems().get(0).isEditable());
    Assert.assertEquals(1, blip.getReactionEntries().size());
    Assert.assertEquals("tada", blip.getReactionEntries().get(0).getEmoji());
  }

  @Test
  public void buildsWriteSessionOnFirstCoupledUpdate() {
    SidecarSelectedWaveUpdate update = updateWithVersionAndHash(0L, "ZERO");

    J2clSidecarWriteSession writeSession =
        J2clSelectedWaveProjector.project(WAVE_ID, null, update, null, 0).getWriteSession();

    Assert.assertNotNull(writeSession);
    Assert.assertEquals(0L, writeSession.getBaseVersion());
    Assert.assertEquals("ZERO", writeSession.getHistoryHash());
    Assert.assertEquals(CHANNEL_ID, writeSession.getChannelId());
    Assert.assertEquals("b+root", writeSession.getReplyTargetBlipId());
  }

  @Test
  public void writeSessionCarriesManifestInsertPositionAndItemCount() {
    SidecarConversationManifest manifest =
        SidecarConversationManifest.of(
            Arrays.asList(
                new SidecarConversationManifest.Entry("b+root", "", "root", 0, 0, 6)),
            8);
    SidecarSelectedWaveUpdate update =
        new SidecarSelectedWaveUpdate(
            1,
            WAVELET_NAME,
            true,
            CHANNEL_ID,
            44L,
            "ABCD",
            Arrays.asList("user@example.com"),
            Arrays.asList(
                new SidecarSelectedWaveDocument(
                    "b+root", "user@example.com", 33L, 44L, "content")),
            null,
            manifest);

    J2clSidecarWriteSession writeSession =
        J2clSelectedWaveProjector.project(WAVE_ID, null, update, null, 0).getWriteSession();

    Assert.assertNotNull(writeSession);
    Assert.assertEquals(6, writeSession.getReplyManifestInsertPosition());
    Assert.assertEquals(8, writeSession.getReplyManifestItemCount());
  }

  @Test
  public void writeSessionCanRetargetManifestInsertPositionToChildBlip() {
    SidecarConversationManifest manifest =
        SidecarConversationManifest.of(
            Arrays.asList(
                new SidecarConversationManifest.Entry("b+root", "", "root", 0, 0, 9),
                new SidecarConversationManifest.Entry("b+child", "b+root", "t+child", 1, 0, 6)),
            12);
    SidecarSelectedWaveUpdate update =
        new SidecarSelectedWaveUpdate(
            1,
            WAVELET_NAME,
            true,
            CHANNEL_ID,
            44L,
            "ABCD",
            Arrays.asList("user@example.com"),
            Arrays.asList(
                new SidecarSelectedWaveDocument(
                    "b+root", "user@example.com", 33L, 44L, "root content"),
                new SidecarSelectedWaveDocument(
                    "b+child", "user@example.com", 33L, 44L, "child content")),
            null,
            manifest);

    J2clSidecarWriteSession writeSession =
        J2clSelectedWaveProjector.project(WAVE_ID, null, update, null, 0).getWriteSession();
    J2clSidecarWriteSession childSession = writeSession.forReplyTarget("b+child");

    Assert.assertNotNull(writeSession);
    Assert.assertEquals("b+root", writeSession.getReplyTargetBlipId());
    Assert.assertEquals(9, writeSession.getReplyManifestInsertPosition());
    Assert.assertEquals("b+child", childSession.getReplyTargetBlipId());
    Assert.assertEquals(6, childSession.getReplyManifestInsertPosition());
    Assert.assertEquals(12, childSession.getReplyManifestItemCount());
  }

  @Test
  public void writeSessionCanRetargetSiblingReplyPositionAndDepthToChildBlip() {
    SidecarConversationManifest manifest =
        SidecarConversationManifest.of(
            Arrays.asList(
                new SidecarConversationManifest.Entry("b+root", "", "root", 0, 0, 9, 10),
                new SidecarConversationManifest.Entry("b+child", "b+root", "t+child", 5, 0, -1, 7)),
            12);
    SidecarSelectedWaveUpdate update =
        new SidecarSelectedWaveUpdate(
            1,
            WAVELET_NAME,
            true,
            CHANNEL_ID,
            44L,
            "ABCD",
            Arrays.asList("user@example.com"),
            Arrays.asList(
                new SidecarSelectedWaveDocument(
                    "b+root", "user@example.com", 33L, 44L, "root content"),
                new SidecarSelectedWaveDocument(
                    "b+child", "user@example.com", 33L, 44L, "child content")),
            null,
            manifest);

    J2clSidecarWriteSession writeSession =
        J2clSelectedWaveProjector.project(WAVE_ID, null, update, null, 0).getWriteSession();
    J2clSidecarWriteSession childSession = writeSession.forReplyTarget("b+child");

    Assert.assertNotNull(writeSession);
    Assert.assertEquals(10, writeSession.getReplyManifestSiblingInsertPosition());
    Assert.assertEquals(0, writeSession.getReplyTargetDepth());
    Assert.assertEquals(-1, childSession.getReplyManifestInsertPosition());
    Assert.assertEquals(12, childSession.getReplyManifestItemCount());
    Assert.assertEquals(7, childSession.getReplyManifestSiblingInsertPosition());
    Assert.assertEquals(5, childSession.getReplyTargetDepth());
  }

  @Test
  public void writeSessionDoesNotReusePreviousManifestOffsetsWithFreshBasis() {
    SidecarConversationManifest previousManifest =
        SidecarConversationManifest.of(
            Arrays.asList(
                new SidecarConversationManifest.Entry("b+root", "", "root", 0, 0, 6)),
            8);
    J2clSidecarWriteSession previousWriteSession =
        new J2clSidecarWriteSession(
            WAVE_ID,
            CHANNEL_ID,
            44L,
            "ABCD",
            "b+root",
            Arrays.asList("user@example.com"),
            6,
            8);
    J2clSelectedWaveModel previous =
        new J2clSelectedWaveModel(
                true,
                false,
                false,
                WAVE_ID,
                "title",
                "snippet",
                "",
                "",
                "",
                0,
                Arrays.asList("user@example.com"),
                Arrays.asList("old content"),
                previousWriteSession,
                J2clSelectedWaveModel.UNKNOWN_UNREAD_COUNT,
                false,
                false,
                false)
            .withConversationManifest(previousManifest);
    SidecarSelectedWaveUpdate liveBlipOnlyUpdate = updateWithVersionAndHash(45L, "EFGH");

    J2clSelectedWaveModel projected =
        J2clSelectedWaveProjector.project(WAVE_ID, null, liveBlipOnlyUpdate, previous, 0);

    Assert.assertSame(previousManifest, projected.getConversationManifest());
    J2clSidecarWriteSession writeSession = projected.getWriteSession();
    Assert.assertNotNull(writeSession);
    Assert.assertEquals(45L, writeSession.getBaseVersion());
    Assert.assertEquals("EFGH", writeSession.getHistoryHash());
    Assert.assertEquals(-1, writeSession.getReplyManifestInsertPosition());
    Assert.assertEquals(-1, writeSession.getReplyManifestItemCount());
  }

  @Test
  public void writeSessionPreservesPreviousManifestOffsetsWhenBasisIsPrevious() {
    J2clSidecarWriteSession previousWriteSession =
        new J2clSidecarWriteSession(
            WAVE_ID,
            CHANNEL_ID,
            44L,
            "ABCD",
            "b+root",
            Arrays.asList("user@example.com"),
            6,
            8);
    J2clSelectedWaveModel previous =
        new J2clSelectedWaveModel(
            true,
            false,
            false,
            WAVE_ID,
            "title",
            "snippet",
            "",
            "",
            "",
            0,
            Arrays.asList("user@example.com"),
            Arrays.asList("old content"),
            previousWriteSession,
            J2clSelectedWaveModel.UNKNOWN_UNREAD_COUNT,
            false,
            false,
            false);
    SidecarSelectedWaveUpdate noCoupledBasis =
        new SidecarSelectedWaveUpdate(
            2,
            WAVELET_NAME,
            true,
            CHANNEL_ID,
            -1L,
            null,
            Arrays.asList("user@example.com"),
            Arrays.asList(
                new SidecarSelectedWaveDocument(
                    "b+root", "user@example.com", 45L, 45L, "live content")),
            new SidecarSelectedWaveFragments(
                -1L,
                44L,
                45L,
                Arrays.asList(new SidecarSelectedWaveFragmentRange("blip:b+root", 44L, 45L)),
                Arrays.asList(
                    new SidecarSelectedWaveFragment("blip:b+root", "live content", 0, 0))));

    J2clSidecarWriteSession writeSession =
        J2clSelectedWaveProjector.project(WAVE_ID, null, noCoupledBasis, previous, 0)
            .getWriteSession();

    Assert.assertNotNull(writeSession);
    Assert.assertEquals(44L, writeSession.getBaseVersion());
    Assert.assertEquals("ABCD", writeSession.getHistoryHash());
    Assert.assertEquals("b+root", writeSession.getReplyTargetBlipId());
    Assert.assertEquals(6, writeSession.getReplyManifestInsertPosition());
    Assert.assertEquals(8, writeSession.getReplyManifestItemCount());
  }

  @Test
  public void writeSessionDropsPreviousManifestOffsetsWhenReplyTargetChanges() {
    J2clSidecarWriteSession previousWriteSession =
        new J2clSidecarWriteSession(
            WAVE_ID,
            CHANNEL_ID,
            44L,
            "ABCD",
            "b+root",
            Arrays.asList("user@example.com"),
            6,
            8);
    J2clSelectedWaveModel previous =
        new J2clSelectedWaveModel(
            true,
            false,
            false,
            WAVE_ID,
            "title",
            "snippet",
            "",
            "",
            "",
            0,
            Arrays.asList("user@example.com"),
            Arrays.asList("old content"),
            previousWriteSession,
            J2clSelectedWaveModel.UNKNOWN_UNREAD_COUNT,
            false,
            false,
            false);
    SidecarSelectedWaveUpdate retargetedWithoutBasis =
        new SidecarSelectedWaveUpdate(
            2,
            WAVELET_NAME,
            true,
            CHANNEL_ID,
            -1L,
            null,
            Arrays.asList("user@example.com"),
            Arrays.asList(
                new SidecarSelectedWaveDocument(
                    "b+other", "user@example.com", 45L, 45L, "other content")),
            null);

    J2clSidecarWriteSession writeSession =
        J2clSelectedWaveProjector.project(WAVE_ID, null, retargetedWithoutBasis, previous, 0)
            .getWriteSession();

    Assert.assertNotNull(writeSession);
    Assert.assertEquals(44L, writeSession.getBaseVersion());
    Assert.assertEquals("ABCD", writeSession.getHistoryHash());
    Assert.assertEquals("b+other", writeSession.getReplyTargetBlipId());
    Assert.assertEquals(-1, writeSession.getReplyManifestInsertPosition());
    Assert.assertEquals(-1, writeSession.getReplyManifestItemCount());
  }
}
