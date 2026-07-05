package org.waveprotocol.box.j2cl.search;

import com.google.j2cl.junit.apt.J2clTestInput;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Assert;
import org.junit.Test;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveDocument;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveFragment;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveFragmentRange;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveFragments;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveReadState;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveUpdate;
import org.waveprotocol.box.j2cl.viewport.J2clViewportGrowthDirection;

/**
 * #1270: split from the monster J2clSelectedWaveProjectorTest. Covers
 * document-only viewport windows, version-zero builds and read-state reprojection.
 */
@J2clTestInput(J2clSelectedWaveProjectorDocumentViewportMergeTest.class)
public class J2clSelectedWaveProjectorDocumentViewportMergeTest extends J2clSelectedWaveProjectorTestSupport {

  @Test
  public void projectMergesDocumentOnlyUpdateWithoutWideningKnownFragmentStart() {
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
                20L,
                "HASH2",
                Arrays.asList("user@example.com"),
                Arrays.asList(
                    new SidecarSelectedWaveDocument(
                        "b+root", "user@example.com", 20L, 21L, "Older document text")),
                null),
            first,
            0);

    J2clSelectedWaveViewportState.Entry root = second.getViewportState().getEntries().get(0);
    Assert.assertEquals(30L, second.getViewportState().getStartVersion());
    Assert.assertEquals(30L, root.getFromVersion());
    Assert.assertEquals(50L, root.getToVersion());
    Assert.assertEquals("Older document text", root.getRawSnapshot());
  }

  @Test
  public void projectMergesDocumentsIntoFragmentUpdate() {
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
                Arrays.asList(
                    new SidecarSelectedWaveDocument(
                        "b+reply", "user@example.com", 42L, 43L, "Reply text")),
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

    Assert.assertEquals(2, projected.getViewportState().getEntries().size());
    Assert.assertEquals("b+root", projected.getViewportState().getEntries().get(0).getBlipId());
    Assert.assertEquals("b+reply", projected.getViewportState().getEntries().get(1).getBlipId());
    Assert.assertEquals(
        "Reply text", projected.getViewportState().getEntries().get(1).getRawSnapshot());
  }

  @Test
  public void projectUpgradesFragmentPlaceholderFromDocumentInSameUpdate() {
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
                Arrays.asList(
                    new SidecarSelectedWaveDocument(
                        "b+root", "user@example.com", 44L, 45L, "Root text from document")),
                new SidecarSelectedWaveFragments(
                    40L,
                    30L,
                    40L,
                    Arrays.asList(
                        new SidecarSelectedWaveFragmentRange("blip:b+root", 30L, 40L)),
                    Arrays.asList(
                        new SidecarSelectedWaveFragment("blip:b+root", null, 0, 0)))),
            null,
            0);

    Assert.assertEquals(1, projected.getViewportState().getEntries().size());
    J2clSelectedWaveViewportState.Entry root = projected.getViewportState().getEntries().get(0);
    Assert.assertTrue(root.isLoaded());
    Assert.assertEquals("Root text from document", root.getRawSnapshot());
    Assert.assertEquals(44L, root.getToVersion());
  }

  @Test
  public void projectResolvesUnknownFragmentStartFromDocumentMerge() {
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
                        "b+root", "user@example.com", 44L, 45L, "Root text from document")),
                new SidecarSelectedWaveFragments(
                    44L,
                    -1L,
                    44L,
                    Arrays.asList(
                        new SidecarSelectedWaveFragmentRange("blip:b+root", -1L, -1L)),
                    Arrays.asList(
                        new SidecarSelectedWaveFragment("blip:b+root", null, 0, 0)))),
            null,
            0);

    J2clSelectedWaveViewportState viewport = projected.getViewportState();
    J2clSelectedWaveViewportState.Entry root = viewport.getEntries().get(0);
    Assert.assertEquals(44L, viewport.getStartVersion());
    Assert.assertEquals(44L, root.getFromVersion());
    Assert.assertEquals(44L, root.getToVersion());
  }

  @Test
  public void projectKeepsLoadedFragmentWhenSameUpdateDocumentAlsoExists() {
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
                Arrays.asList(
                    new SidecarSelectedWaveDocument(
                        "b+root", "user@example.com", 44L, 45L, "Document text")),
                new SidecarSelectedWaveFragments(
                    40L,
                    30L,
                    40L,
                    Arrays.asList(
                        new SidecarSelectedWaveFragmentRange("blip:b+root", 30L, 40L)),
                    Arrays.asList(
                        new SidecarSelectedWaveFragment(
                            "blip:b+root", "Fragment text", 0, 0)))),
            null,
            0);

    J2clSelectedWaveViewportState.Entry root =
        projected.getViewportState().getEntries().get(0);
    Assert.assertEquals("Fragment text", root.getRawSnapshot());
    Assert.assertEquals(40L, root.getToVersion());
  }

  @Test
  public void projectBuildsViewportWindowFromDocumentOnlyUpdate() {
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
                null),
            null,
            0);

    Assert.assertEquals(1, projected.getViewportState().getReadWindowEntries().size());
    Assert.assertEquals(
        "b+root", projected.getViewportState().getReadWindowEntries().get(0).getBlipId());
    Assert.assertEquals(
        "Document text",
        projected.getViewportState().getReadWindowEntries().get(0).getText());
  }

  @Test
  public void projectKeepsEmptyTextDocumentAsLoadedViewportAnchor() {
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
                        "b+empty", "user@example.com", 7L, 8L, "")),
                null),
            null,
            0);

    Assert.assertEquals(1, projected.getViewportState().getReadWindowEntries().size());
    Assert.assertEquals(
        "b+empty", projected.getViewportState().getReadWindowEntries().get(0).getBlipId());
    Assert.assertEquals("", projected.getViewportState().getReadWindowEntries().get(0).getText());
  }

  @Test
  public void projectKeepsNonBlipDocumentsAsNonReadViewportEntries() {
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
                        "conversation", "user@example.com", 7L, 8L, "metadata")),
                null),
            null,
            0);

    J2clSelectedWaveViewportState.Entry entry = projected.getViewportState().getEntries().get(0);
    Assert.assertEquals("conversation", entry.getSegment());
    Assert.assertFalse(entry.isBlip());
    Assert.assertTrue(entry.isLoaded());
    Assert.assertTrue(projected.getViewportState().getReadWindowEntries().isEmpty());
  }

  @Test
  public void mergeFragmentsDoesNotDowngradeLoadedEntryWithPlaceholderOverlap() {
    J2clSelectedWaveViewportState initial =
        J2clSelectedWaveViewportState.fromFragments(
            new SidecarSelectedWaveFragments(
                44L,
                40L,
                44L,
                Arrays.asList(new SidecarSelectedWaveFragmentRange("blip:b+root", 40L, 44L)),
                Arrays.asList(new SidecarSelectedWaveFragment("blip:b+root", "Root text", 0, 0))));

    J2clSelectedWaveViewportState merged =
        initial.mergeFragments(
            new SidecarSelectedWaveFragments(
                48L,
                44L,
                48L,
                Arrays.asList(new SidecarSelectedWaveFragmentRange("blip:b+root", 44L, 48L)),
                Arrays.asList(new SidecarSelectedWaveFragment("blip:b+root", null, 0, 0))),
            J2clViewportGrowthDirection.FORWARD);

    J2clSelectedWaveViewportState.Entry root = merged.getEntries().get(0);
    Assert.assertTrue(root.isLoaded());
    Assert.assertEquals("Root text", root.getRawSnapshot());
    Assert.assertEquals(40L, root.getFromVersion());
    Assert.assertEquals(48L, root.getToVersion());
  }

  @Test
  public void mergeFragmentsPrependsMissingEntriesForBackwardGrowth() {
    J2clSelectedWaveViewportState initial =
        J2clSelectedWaveViewportState.fromFragments(
            new SidecarSelectedWaveFragments(
                44L,
                40L,
                44L,
                Arrays.asList(new SidecarSelectedWaveFragmentRange("blip:b+root", 40L, 44L)),
                Arrays.asList(new SidecarSelectedWaveFragment("blip:b+root", "Root text", 0, 0))));

    J2clSelectedWaveViewportState merged =
        initial.mergeFragments(
            new SidecarSelectedWaveFragments(
                48L,
                36L,
                40L,
                Arrays.asList(new SidecarSelectedWaveFragmentRange("blip:b+before", 36L, 40L)),
                Arrays.asList(
                    new SidecarSelectedWaveFragment("blip:b+before", "Before text", 0, 0))),
            J2clViewportGrowthDirection.BACKWARD);

    Assert.assertEquals(2, merged.getEntries().size());
    Assert.assertEquals("b+before", merged.getEntries().get(0).getBlipId());
    Assert.assertEquals("b+root", merged.getEntries().get(1).getBlipId());
  }

  @Test
  public void projectBuildsDocumentOnlyViewportAtVersionZero() {
    J2clSelectedWaveModel projected =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 0),
            new SidecarSelectedWaveUpdate(
                1,
                WAVELET_NAME,
                true,
                CHANNEL_ID,
                0L,
                "HASH",
                Arrays.asList("user@example.com"),
                Arrays.asList(
                    new SidecarSelectedWaveDocument(
                        "b+root", "user@example.com", 0L, 8L, "Bootstrap text")),
                null),
            null,
            0);

    Assert.assertEquals(0L, projected.getViewportState().getSnapshotVersion());
    Assert.assertEquals(0L, projected.getViewportState().getStartVersion());
    Assert.assertEquals(0L, projected.getViewportState().getEndVersion());
  }

  @Test
  public void projectSkipsDocumentViewportEntriesWithoutDocumentIds() {
    J2clSelectedWaveModel projected =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 0),
            new SidecarSelectedWaveUpdate(
                1,
                WAVELET_NAME,
                true,
                CHANNEL_ID,
                0L,
                "HASH",
                Arrays.asList("user@example.com"),
                Arrays.asList(
                    new SidecarSelectedWaveDocument(
                        null, "user@example.com", 0L, 8L, "No id"),
                    new SidecarSelectedWaveDocument(
                        "", "user@example.com", 1L, 9L, "Empty id")),
                null),
            null,
            0);

    Assert.assertTrue(projected.getViewportState().isEmpty());
  }

  @Test
  public void projectNormalizesNullDocumentTextToLoadedEmptyViewportAnchor() {
    J2clSelectedWaveModel projected =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 0),
            new SidecarSelectedWaveUpdate(
                1,
                WAVELET_NAME,
                true,
                CHANNEL_ID,
                0L,
                "HASH",
                Arrays.asList("user@example.com"),
                Arrays.asList(
                    new SidecarSelectedWaveDocument(
                        "b+null", "user@example.com", 1L, 9L, null)),
                null),
            null,
            0);

    Assert.assertEquals(1, projected.getViewportState().getReadWindowEntries().size());
    Assert.assertEquals(
        "b+null", projected.getViewportState().getReadWindowEntries().get(0).getBlipId());
    Assert.assertEquals("", projected.getViewportState().getReadWindowEntries().get(0).getText());
  }

  @Test
  public void reprojectReadStatePreservesViewportWindow() {
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
                        new SidecarSelectedWaveFragmentRange("blip:b+root", 30L, 40L)),
                    Arrays.asList(
                        new SidecarSelectedWaveFragment("blip:b+root", "Root text", 0, 0)))),
            null,
            0);

    J2clSelectedWaveModel reprojected =
        J2clSelectedWaveProjector.reprojectReadState(
            projected,
            digest("Wave A", "snippet", 0),
            new SidecarSelectedWaveReadState(WAVE_ID, 0, true),
            false);

    Assert.assertEquals(40L, reprojected.getViewportState().getSnapshotVersion());
    Assert.assertEquals(1, reprojected.getViewportState().getEntries().size());
    Assert.assertEquals(
        "Root text", reprojected.getViewportState().getEntries().get(0).getRawSnapshot());
  }
}
