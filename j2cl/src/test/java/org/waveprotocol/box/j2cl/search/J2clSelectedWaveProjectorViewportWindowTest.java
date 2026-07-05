package org.waveprotocol.box.j2cl.search;

import com.google.j2cl.junit.apt.J2clTestInput;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Assert;
import org.junit.Test;
import org.waveprotocol.box.j2cl.read.J2clReadBlip;
import org.waveprotocol.box.j2cl.transport.SidecarConversationManifest;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveDocument;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveFragment;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveFragmentRange;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveFragments;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveUpdate;

/**
 * #1270: split from the monster J2clSelectedWaveProjectorTest. Covers
 * viewport window ranges, placeholders and fragment/snapshot/document merge ordering.
 */
@J2clTestInput(J2clSelectedWaveProjectorViewportWindowTest.class)
public class J2clSelectedWaveProjectorViewportWindowTest extends J2clSelectedWaveProjectorTestSupport {

  @Test
  public void projectPrefersFragmentReadBlipsOverDocumentFallbacks() {
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
                Arrays.asList(
                    new SidecarSelectedWaveDocument(
                        "b+root", "user@example.com", 7L, 8L, "Document text")),
                new SidecarSelectedWaveFragments(
                    9L,
                    0L,
                    9L,
                    Arrays.asList(
                        new SidecarSelectedWaveFragmentRange("blip:b+root", 0L, 9L)),
                    Arrays.asList(
                        new SidecarSelectedWaveFragment("blip:b+root", "Fragment text", 0, 0)))),
            null,
            0);

    Assert.assertEquals(1, projected.getReadBlips().size());
    J2clReadBlip blip = projected.getReadBlips().get(0);
    Assert.assertEquals("b+root", blip.getBlipId());
    Assert.assertEquals("Fragment text", blip.getText());
  }

  @Test
  public void projectPreservesFragmentWindowRangesAndPlaceholders() {
    J2clSelectedWaveModel projected =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 0),
            new SidecarSelectedWaveUpdate(
                1,
                WAVELET_NAME,
                true,
                CHANNEL_ID,
                40L,
                "HASH",
                Arrays.asList("user@example.com"),
                Collections.<SidecarSelectedWaveDocument>emptyList(),
                new SidecarSelectedWaveFragments(
                    40L,
                    30L,
                    40L,
                    Arrays.asList(
                        new SidecarSelectedWaveFragmentRange(MANIFEST_SEGMENT, 30L, 40L),
                        new SidecarSelectedWaveFragmentRange("blip:b+root", 30L, 36L),
                        new SidecarSelectedWaveFragmentRange("blip:b+missing", 36L, 38L),
                        new SidecarSelectedWaveFragmentRange("blip:b+empty", 38L, 40L)),
                    Arrays.asList(
                        new SidecarSelectedWaveFragment(MANIFEST_SEGMENT, "metadata", 0, 0),
                        new SidecarSelectedWaveFragment("blip:b+root", "Root text", 2, 3),
                        new SidecarSelectedWaveFragment("blip:b+empty", null, 0, 0)))),
            null,
            0);

    J2clSelectedWaveViewportState viewport = projected.getViewportState();

    Assert.assertEquals(40L, viewport.getSnapshotVersion());
    Assert.assertEquals(30L, viewport.getStartVersion());
    Assert.assertEquals(40L, viewport.getEndVersion());
    Assert.assertEquals(4, viewport.getEntries().size());

    J2clSelectedWaveViewportState.Entry manifest = viewport.getEntries().get(0);
    Assert.assertEquals(MANIFEST_SEGMENT, manifest.getSegment());
    Assert.assertEquals(30L, manifest.getFromVersion());
    Assert.assertEquals(40L, manifest.getToVersion());
    Assert.assertFalse(manifest.isBlip());
    Assert.assertTrue(manifest.isLoaded());
    Assert.assertEquals("metadata", manifest.getRawSnapshot());

    J2clSelectedWaveViewportState.Entry root = viewport.getEntries().get(1);
    Assert.assertEquals("blip:b+root", root.getSegment());
    Assert.assertEquals(30L, root.getFromVersion());
    Assert.assertEquals(36L, root.getToVersion());
    Assert.assertTrue(root.isBlip());
    Assert.assertEquals("b+root", root.getBlipId());
    Assert.assertTrue(root.isLoaded());
    Assert.assertEquals("Root text", root.getRawSnapshot());
    Assert.assertEquals(2, root.getAdjustOperationCount());
    Assert.assertEquals(3, root.getDiffOperationCount());

    J2clSelectedWaveViewportState.Entry placeholder = viewport.getEntries().get(2);
    Assert.assertEquals("blip:b+missing", placeholder.getSegment());
    Assert.assertEquals(36L, placeholder.getFromVersion());
    Assert.assertEquals(38L, placeholder.getToVersion());
    Assert.assertTrue(placeholder.isBlip());
    Assert.assertEquals("b+missing", placeholder.getBlipId());
    Assert.assertFalse(placeholder.isLoaded());

    J2clSelectedWaveViewportState.Entry empty = viewport.getEntries().get(3);
    Assert.assertEquals("blip:b+empty", empty.getSegment());
    Assert.assertEquals(38L, empty.getFromVersion());
    Assert.assertEquals(40L, empty.getToVersion());
    Assert.assertEquals("b+empty", empty.getBlipId());
    Assert.assertFalse(empty.isLoaded());
  }

  @Test
  public void projectUsesRawViewportManifestWhenDocumentManifestIsAbsent() {
    J2clSelectedWaveModel projected =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 0),
            new SidecarSelectedWaveUpdate(
                1,
                WAVELET_NAME,
                true,
                CHANNEL_ID,
                71L,
                "HASH",
                Arrays.asList("user@example.com"),
                Collections.<SidecarSelectedWaveDocument>emptyList(),
                new SidecarSelectedWaveFragments(
                    71L,
                    30L,
                    71L,
                    Arrays.asList(
                        new SidecarSelectedWaveFragmentRange(MANIFEST_SEGMENT, 30L, 71L),
                        new SidecarSelectedWaveFragmentRange("blip:b+root", 30L, 40L),
                        new SidecarSelectedWaveFragmentRange("blip:b+second", 40L, 50L),
                        new SidecarSelectedWaveFragmentRange("blip:b+third", 50L, 60L),
                        new SidecarSelectedWaveFragmentRange("blip:b+nested", 60L, 71L)),
                    Arrays.asList(
                        new SidecarSelectedWaveFragment(
                            MANIFEST_SEGMENT,
                            "<conversation><blip id=\"b+root\">"
                                + "<thread id=\"t+first\"><blip id=\"b+second\">"
                                + "<thread id=\"t+nested\"><blip id=\"b+nested\"/>"
                                + "</thread></blip></thread><thread id=\"t+third\">"
                                + "<blip id=\"b+third\"/></thread></blip></conversation>",
                            0,
                            0),
                        new SidecarSelectedWaveFragment("blip:b+root", "Root", 0, 0),
                        new SidecarSelectedWaveFragment("blip:b+second", "Second", 0, 0),
                        new SidecarSelectedWaveFragment("blip:b+third", "Third", 0, 0),
                        new SidecarSelectedWaveFragment("blip:b+nested", "Nested", 0, 0)))),
            null,
            0);

    SidecarConversationManifest manifest = projected.getConversationManifest();
    Assert.assertFalse(manifest.isEmpty());
    Assert.assertEquals("b+root", manifest.getOrderedEntries().get(0).getBlipId());
    Assert.assertEquals("b+second", manifest.getOrderedEntries().get(1).getBlipId());
    Assert.assertEquals("b+nested", manifest.getOrderedEntries().get(2).getBlipId());
    Assert.assertEquals("b+third", manifest.getOrderedEntries().get(3).getBlipId());
    Assert.assertEquals("b+second", manifest.findByBlipId("b+nested").getParentBlipId());
    Assert.assertEquals("b+root", manifest.findByBlipId("b+third").getParentBlipId());
  }

  @Test
  public void projectCarriesPreviousViewportWindowWhenUpdateOmitsFragments() {
    J2clSelectedWaveModel first =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 0),
            new SidecarSelectedWaveUpdate(
                1,
                WAVELET_NAME,
                true,
                CHANNEL_ID,
                40L,
                "HASH",
                Arrays.asList("user@example.com"),
                Collections.<SidecarSelectedWaveDocument>emptyList(),
                new SidecarSelectedWaveFragments(
                    40L,
                    30L,
                    40L,
                    Arrays.asList(
                        new SidecarSelectedWaveFragmentRange("blip:b+root", 30L, 40L)),
                    Arrays.asList(
                        new SidecarSelectedWaveFragment("blip:b+root", "Root text", 0, 0)))),
            null,
            0);

    J2clSelectedWaveModel second =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 0),
            sampleUpdate(),
            first,
            0);

    Assert.assertEquals(40L, second.getViewportState().getSnapshotVersion());
    Assert.assertEquals(1, second.getViewportState().getEntries().size());
    Assert.assertEquals("b+root", second.getViewportState().getEntries().get(0).getBlipId());
  }

  @Test
  public void projectCarriesPreviousViewportWindowWhenFragmentsOnlyContainMetadata() {
    J2clSelectedWaveModel first =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 0),
            rootFragmentUpdate(1, 40L, "HASH", "Root text"),
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
                44L,
                "HASH2",
                Arrays.asList("user@example.com"),
                Collections.<SidecarSelectedWaveDocument>emptyList(),
                metadataOnlyFragments(44L, 40L, 44L)),
            first,
            0);

    Assert.assertEquals(40L, second.getViewportState().getSnapshotVersion());
    Assert.assertEquals(1, second.getViewportState().getEntries().size());
    Assert.assertEquals("b+root", second.getViewportState().getEntries().get(0).getBlipId());
    Assert.assertEquals("Root text", second.getViewportState().getEntries().get(0).getRawSnapshot());
  }

  @Test
  public void projectFallsThroughToDocumentsWhenMetadataOnlyFragmentsHaveNoPreviousViewport() {
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
                Arrays.asList(
                    new SidecarSelectedWaveDocument(
                        "b+root", "user@example.com", 44L, 45L, "Document text")),
                metadataOnlyFragments(44L, 40L, 44L)),
            null,
            0);

    Assert.assertEquals(1, projected.getViewportState().getEntries().size());
    Assert.assertEquals("b+root", projected.getViewportState().getEntries().get(0).getBlipId());
    Assert.assertEquals("Document text", projected.getViewportState().getEntries().get(0).getRawSnapshot());
  }

  @Test
  public void projectDoesNotCarryMetadataOnlyFragmentsAcrossWaveSwitch() {
    J2clSelectedWaveModel previous =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 0),
            rootFragmentUpdate(1, 40L, "HASH", "Root text"),
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
                44L,
                "HASH2",
                Arrays.asList("user@example.com"),
                Collections.<SidecarSelectedWaveDocument>emptyList(),
                metadataOnlyFragments(44L, 40L, 44L)),
            previous,
            0);

    Assert.assertTrue(switched.getViewportState().isEmpty());
    Assert.assertTrue(switched.getReadBlips().isEmpty());
  }

  @Test
  public void projectMixedMetadataAndBlipFragmentsReplacePreviousViewportWindow() {
    J2clSelectedWaveModel first =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 0),
            new SidecarSelectedWaveUpdate(
                1,
                WAVELET_NAME,
                true,
                CHANNEL_ID,
                40L,
                "HASH",
                Arrays.asList("user@example.com"),
                Collections.<SidecarSelectedWaveDocument>emptyList(),
                new SidecarSelectedWaveFragments(
                    40L,
                    30L,
                    40L,
                    Arrays.asList(
                        new SidecarSelectedWaveFragmentRange("blip:b+root", 30L, 40L),
                        new SidecarSelectedWaveFragmentRange("blip:b+stale", 30L, 40L)),
                    Arrays.asList(
                        new SidecarSelectedWaveFragment("blip:b+root", "Old root text", 0, 0),
                        new SidecarSelectedWaveFragment("blip:b+stale", "Stale text", 0, 0)))),
            null,
            0);

    J2clSelectedWaveModel mixedFragments =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 0),
            new SidecarSelectedWaveUpdate(
                2,
                WAVELET_NAME,
                true,
                CHANNEL_ID,
                50L,
                "HASH2",
                Arrays.asList("user@example.com"),
                Collections.<SidecarSelectedWaveDocument>emptyList(),
                new SidecarSelectedWaveFragments(
                    50L,
                    45L,
                    50L,
                    Arrays.asList(
                        new SidecarSelectedWaveFragmentRange(MANIFEST_SEGMENT, 45L, 50L),
                        new SidecarSelectedWaveFragmentRange("blip:b+root", 45L, 50L)),
                    Arrays.asList(
                        new SidecarSelectedWaveFragment(MANIFEST_SEGMENT, "metadata", 0, 0),
                        new SidecarSelectedWaveFragment("blip:b+root", "New root text", 0, 0)))),
            first,
            0);

    Assert.assertEquals(50L, mixedFragments.getViewportState().getSnapshotVersion());
    Assert.assertEquals(2, mixedFragments.getViewportState().getEntries().size());
    assertNoEntryBySegment(mixedFragments.getViewportState(), "blip:b+stale");
    Assert.assertEquals(
        "metadata",
        entryBySegment(mixedFragments.getViewportState(), MANIFEST_SEGMENT).getRawSnapshot());
    Assert.assertEquals(
        "New root text",
        entryBySegment(mixedFragments.getViewportState(), "blip:b+root").getRawSnapshot());
  }

  @Test
  public void projectMergesSameWaveLiveBlipFragmentsIntoPreviousViewportWindow() {
    J2clSelectedWaveModel first =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 0),
            rootFragmentUpdate(1, 40L, "HASH", "Root text"),
            null,
            0);

    // Live deltas from the server have snapshotVersion < 0 (the codec defaults to -1 when the
    // server omits the field). Using -1L here matches the wire semantics for a post-submit push.
    J2clSelectedWaveModel liveReply =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 0),
            new SidecarSelectedWaveUpdate(
                2,
                WAVELET_NAME,
                true,
                CHANNEL_ID,
                50L,
                "HASH2",
                Arrays.asList("user@example.com"),
                Collections.<SidecarSelectedWaveDocument>emptyList(),
                new SidecarSelectedWaveFragments(
                    -1L,
                    45L,
                    50L,
                    Arrays.asList(
                        new SidecarSelectedWaveFragmentRange("blip:b+reply", 45L, 50L)),
                    Arrays.asList(
                        new SidecarSelectedWaveFragment(
                            "blip:b+reply", "Reply submitted from composer", 0, 0)))),
            first,
            0);

    Assert.assertEquals(2, liveReply.getViewportState().getEntries().size());
    Assert.assertEquals(
        "Root text",
        entryBySegment(liveReply.getViewportState(), "blip:b+root").getRawSnapshot());
    Assert.assertEquals(
        "Reply submitted from composer",
        entryBySegment(liveReply.getViewportState(), "blip:b+reply").getRawSnapshot());
    // The merged viewport retains the initial snapshot's version (max(-1, 40) = 40).
    Assert.assertEquals(40L, liveReply.getViewportState().getSnapshotVersion());
    Assert.assertEquals(2, liveReply.getReadBlips().size());
    Assert.assertEquals("b+reply", liveReply.getReadBlips().get(1).getBlipId());
    Assert.assertEquals("Reply submitted from composer", liveReply.getReadBlips().get(1).getText());
    Assert.assertEquals(50L, liveReply.getWriteSession().getBaseVersion());
  }

  @Test
  public void snapshotFragmentUpdateReplacesViewportInsteadOfMerging() {
    // First update: blip-only snapshot (snapshotVersion >= 0) establishes initial viewport.
    J2clSelectedWaveModel first =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 0),
            new SidecarSelectedWaveUpdate(
                1,
                WAVELET_NAME,
                true,
                CHANNEL_ID,
                40L,
                "HASH",
                Arrays.asList("user@example.com"),
                Collections.<SidecarSelectedWaveDocument>emptyList(),
                new SidecarSelectedWaveFragments(
                    1L,
                    30L,
                    40L,
                    Arrays.asList(
                        new SidecarSelectedWaveFragmentRange("blip:b+root", 30L, 40L),
                        new SidecarSelectedWaveFragmentRange("blip:b+old", 30L, 40L)),
                    Arrays.asList(
                        new SidecarSelectedWaveFragment("blip:b+root", "Root text", 0, 0),
                        new SidecarSelectedWaveFragment("blip:b+old", "Old blip text", 0, 0)))),
            null,
            0);

    // Second update: another full-window blip snapshot (snapshotVersion >= 0, e.g. on reconnect).
    // Must REPLACE the previous viewport — stale "b+old" must not survive.
    J2clSelectedWaveModel snapshot =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 0),
            new SidecarSelectedWaveUpdate(
                2,
                WAVELET_NAME,
                true,
                CHANNEL_ID,
                60L,
                "HASH2",
                Arrays.asList("user@example.com"),
                Collections.<SidecarSelectedWaveDocument>emptyList(),
                new SidecarSelectedWaveFragments(
                    0L,
                    50L,
                    60L,
                    Arrays.asList(
                        new SidecarSelectedWaveFragmentRange("blip:b+root", 50L, 60L),
                        new SidecarSelectedWaveFragmentRange("blip:b+new", 50L, 60L)),
                    Arrays.asList(
                        new SidecarSelectedWaveFragment("blip:b+root", "Updated root", 0, 0),
                        new SidecarSelectedWaveFragment("blip:b+new", "New blip text", 0, 0)))),
            first,
            0);

    // Viewport must contain only the new snapshot entries — stale b+old must be gone.
    Assert.assertEquals(0L, snapshot.getViewportState().getSnapshotVersion());
    Assert.assertEquals(2, snapshot.getViewportState().getEntries().size());
    assertNoEntryBySegment(snapshot.getViewportState(), "blip:b+old");
    Assert.assertEquals(
        "Updated root",
        entryBySegment(snapshot.getViewportState(), "blip:b+root").getRawSnapshot());
    Assert.assertEquals(
        "New blip text",
        entryBySegment(snapshot.getViewportState(), "blip:b+new").getRawSnapshot());
  }

  @Test
  public void liveFragmentDeltaMergesWithPreviousViewport() {
    // First update: full-window snapshot (snapshotVersion >= 0) establishes viewport.
    J2clSelectedWaveModel first =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 0),
            new SidecarSelectedWaveUpdate(
                1,
                WAVELET_NAME,
                true,
                CHANNEL_ID,
                40L,
                "HASH",
                Arrays.asList("user@example.com"),
                Collections.<SidecarSelectedWaveDocument>emptyList(),
                new SidecarSelectedWaveFragments(
                    1L,
                    30L,
                    40L,
                    Arrays.asList(
                        new SidecarSelectedWaveFragmentRange("blip:b+root", 30L, 40L)),
                    Arrays.asList(
                        new SidecarSelectedWaveFragment("blip:b+root", "Root text", 0, 0)))),
            null,
            0);

    // Second update: live delta (snapshotVersion = -1, the codec default for server push).
    // Must MERGE with the previous viewport — both old and new blips must be visible.
    J2clSelectedWaveModel merged =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 0),
            new SidecarSelectedWaveUpdate(
                2,
                WAVELET_NAME,
                true,
                CHANNEL_ID,
                50L,
                "HASH2",
                Arrays.asList("user@example.com"),
                Collections.<SidecarSelectedWaveDocument>emptyList(),
                new SidecarSelectedWaveFragments(
                    -1L,
                    45L,
                    50L,
                    Arrays.asList(
                        new SidecarSelectedWaveFragmentRange("blip:b+reply", 45L, 50L)),
                    Arrays.asList(
                        new SidecarSelectedWaveFragment("blip:b+reply", "Live reply", 0, 0)))),
            first,
            0);

    // Both original and new blip must be present after a live-delta merge.
    Assert.assertEquals(2, merged.getViewportState().getEntries().size());
    Assert.assertEquals(
        "Root text",
        entryBySegment(merged.getViewportState(), "blip:b+root").getRawSnapshot());
    Assert.assertEquals(
        "Live reply",
        entryBySegment(merged.getViewportState(), "blip:b+reply").getRawSnapshot());
  }

  @Test
  public void projectPreservesDocumentMergedViewportAcrossMetadataOnlyFragments() {
    J2clSelectedWaveModel first =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 0),
            rootFragmentUpdate(1, 40L, "HASH", "Root text"),
            null,
            0);

    J2clSelectedWaveModel documentMerged =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 0),
            new SidecarSelectedWaveUpdate(
                2,
                WAVELET_NAME,
                true,
                CHANNEL_ID,
                44L,
                "HASH2",
                Arrays.asList("user@example.com"),
                Arrays.asList(
                    new SidecarSelectedWaveDocument(
                        "b+root", "user@example.com", 44L, 45L, "Document text")),
                null),
            first,
            0);

    J2clSelectedWaveModel metadataOnly =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 0),
            new SidecarSelectedWaveUpdate(
                3,
                WAVELET_NAME,
                true,
                CHANNEL_ID,
                45L,
                "HASH3",
                Arrays.asList("user@example.com"),
                Collections.<SidecarSelectedWaveDocument>emptyList(),
                metadataOnlyFragments(45L, 44L, 45L)),
            documentMerged,
            0);

    Assert.assertEquals(44L, metadataOnly.getViewportState().getSnapshotVersion());
    Assert.assertEquals(1, metadataOnly.getViewportState().getEntries().size());
    Assert.assertEquals("b+root", metadataOnly.getViewportState().getEntries().get(0).getBlipId());
    Assert.assertEquals(
        "Document text", metadataOnly.getViewportState().getEntries().get(0).getRawSnapshot());
  }

  @Test
  public void projectMergesDocumentOnlyUpdateIntoPreviousViewportWindow() {
    J2clSelectedWaveModel first =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 0),
            new SidecarSelectedWaveUpdate(
                1,
                WAVELET_NAME,
                true,
                CHANNEL_ID,
                40L,
                "HASH",
                Arrays.asList("user@example.com"),
                Collections.<SidecarSelectedWaveDocument>emptyList(),
                new SidecarSelectedWaveFragments(
                    40L,
                    30L,
                    40L,
                    Arrays.asList(
                        new SidecarSelectedWaveFragmentRange("blip:b+root", 30L, 36L),
                        new SidecarSelectedWaveFragmentRange("blip:b+missing", 36L, 40L)),
                    Arrays.asList(
                        new SidecarSelectedWaveFragment("blip:b+root", "Root text", 0, 0)))),
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
                44L,
                "HASH2",
                Arrays.asList("user@example.com"),
                Arrays.asList(
                    new SidecarSelectedWaveDocument(
                        "b+root", "user@example.com", 44L, 45L, "Root text updated")),
                null),
            first,
            0);

    Assert.assertEquals(2, second.getViewportState().getEntries().size());
    J2clSelectedWaveViewportState.Entry root = second.getViewportState().getEntries().get(0);
    Assert.assertEquals("blip:b+root", root.getSegment());
    Assert.assertEquals(30L, root.getFromVersion());
    Assert.assertEquals(44L, root.getToVersion());
    Assert.assertTrue(root.isLoaded());
    Assert.assertEquals("Root text updated", root.getRawSnapshot());
    J2clSelectedWaveViewportState.Entry missing = second.getViewportState().getEntries().get(1);
    Assert.assertEquals("blip:b+missing", missing.getSegment());
    Assert.assertEquals(36L, missing.getFromVersion());
    Assert.assertEquals(40L, missing.getToVersion());
    Assert.assertFalse(missing.isLoaded());
  }

  @Test
  public void projectMergesDocumentOnlyUpdateWithoutDowngradingViewportVersion() {
    J2clSelectedWaveModel first =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 0),
            new SidecarSelectedWaveUpdate(
                1,
                WAVELET_NAME,
                true,
                CHANNEL_ID,
                50L,
                "HASH",
                Arrays.asList("user@example.com"),
                Collections.<SidecarSelectedWaveDocument>emptyList(),
                new SidecarSelectedWaveFragments(
                    50L,
                    30L,
                    50L,
                    Arrays.asList(
                        new SidecarSelectedWaveFragmentRange("blip:b+root", 30L, 50L)),
                    Arrays.asList(
                        new SidecarSelectedWaveFragment("blip:b+root", "Root text", 0, 0)))),
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
                44L,
                "HASH2",
                Arrays.asList("user@example.com"),
                Arrays.asList(
                    new SidecarSelectedWaveDocument(
                        "b+reply", "user@example.com", 44L, 45L, "Reply text")),
                null),
            first,
            0);

    Assert.assertEquals(50L, second.getViewportState().getSnapshotVersion());
    Assert.assertEquals(50L, second.getViewportState().getEndVersion());
    Assert.assertEquals(2, second.getViewportState().getEntries().size());
    Assert.assertEquals("b+reply", second.getViewportState().getEntries().get(1).getBlipId());
    Assert.assertEquals(44L, second.getViewportState().getEntries().get(1).getToVersion());
  }
}
