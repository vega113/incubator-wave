package org.waveprotocol.box.j2cl.search;

import com.google.j2cl.junit.apt.J2clTestInput;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Assert;
import org.junit.Test;
import org.waveprotocol.box.j2cl.attachment.J2clAttachmentMetadata;
import org.waveprotocol.box.j2cl.attachment.J2clAttachmentRenderModel;
import org.waveprotocol.box.j2cl.read.J2clInlineReplyAnchor;
import org.waveprotocol.box.j2cl.read.J2clReadBlip;
import org.waveprotocol.box.j2cl.read.J2clReadBlipContent;
import org.waveprotocol.box.j2cl.transport.SidecarAnnotationRange;
import org.waveprotocol.box.j2cl.transport.SidecarReactionEntry;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveDocument;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveFragment;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveFragmentRange;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveFragments;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveUpdate;
import org.waveprotocol.box.j2cl.viewport.J2clViewportGrowthDirection;

/**
 * #1270: split from the monster J2clSelectedWaveProjectorTest. Covers
 * inline-reply anchor projection and attachment-model extraction/resolution across viewport fragments.
 */
@J2clTestInput(J2clSelectedWaveProjectorViewportProjectionTest.class)
public class J2clSelectedWaveProjectorViewportProjectionTest extends J2clSelectedWaveProjectorTestSupport {

  @Test
  public void viewportFragmentsProjectBodyItemCountToReadModelsAndWindowEntries() {
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
                    Arrays.asList(new SidecarSelectedWaveFragmentRange("blip:b+task", 0L, 9L)),
                    Arrays.asList(
                        new SidecarSelectedWaveFragment(
                            "blip:b+task",
                            "<body><line/>Review launch<?a \"task/done\"=\"true\"?></body>",
                            0,
                            0)))),
            null,
            0);

    Assert.assertEquals(1, projected.getReadBlips().size());
    Assert.assertEquals("Review launch", projected.getReadBlips().get(0).getText());
    Assert.assertTrue(projected.getReadBlips().get(0).isTaskDone());
    Assert.assertEquals(17, projected.getReadBlips().get(0).getBodyItemCount());
    Assert.assertTrue(
        projected.getViewportState().getReadWindowEntries().get(0).isTaskDone());
    Assert.assertEquals(
        17,
        projected.getViewportState().getReadWindowEntries().get(0).getBodyItemCount());
  }

  @Test
  public void viewportFragmentsProjectInlineReplyAnchorsToReadBlips() {
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
                    Arrays.asList(new SidecarSelectedWaveFragmentRange("blip:b+root", 0L, 9L)),
                    Arrays.asList(
                        new SidecarSelectedWaveFragment(
                            "blip:b+root",
                            "<body><line/>Before <reply id=\"t+inline\"></reply> after</body>",
                            0,
                            0)))),
            null,
            0);

    J2clReadBlip blip = projected.getReadBlips().get(0);
    Assert.assertEquals("Before  after", blip.getText());
    Assert.assertEquals(1, blip.getInlineReplyAnchors().size());
    Assert.assertEquals("t+inline", blip.getInlineReplyAnchors().get(0).getThreadId());
    Assert.assertEquals("Before ".length(), blip.getInlineReplyAnchors().get(0).getTextOffset());
    Assert.assertEquals(
        1,
        projected.getViewportState().getReadWindowEntries().get(0).getInlineReplyAnchors().size());
  }

  @Test
  public void documentSnapshotsProjectInlineReplyAnchorsToReadBlips() {
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
                        "b+root",
                        "user@example.com",
                        7L,
                        10L,
                        "Before  after",
                        /* bodyItemCount= */ 19,
                        Collections.<SidecarAnnotationRange>emptyList(),
                        Collections.<SidecarReactionEntry>emptyList(),
                        SidecarSelectedWaveDocument.LOCK_STATE_UNLOCKED,
                        Arrays.asList(new J2clInlineReplyAnchor("t+inline", "Before ".length())))),
                null),
            null,
            0);

    J2clReadBlip blip = projected.getReadBlips().get(0);
    Assert.assertEquals("Before  after", blip.getText());
    Assert.assertEquals(1, blip.getInlineReplyAnchors().size());
    Assert.assertEquals("t+inline", blip.getInlineReplyAnchors().get(0).getThreadId());
    Assert.assertEquals("Before ".length(), blip.getInlineReplyAnchors().get(0).getTextOffset());
    Assert.assertEquals(
        1,
        projected.getViewportState().getReadWindowEntries().get(0).getInlineReplyAnchors().size());
  }

  @Test
  public void projectExtractsAttachmentModelsFromImageElementsInFragments() {
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
                    Arrays.asList(new SidecarSelectedWaveFragmentRange("blip:b+root", 0L, 9L)),
                    Arrays.asList(
                        new SidecarSelectedWaveFragment(
                            "blip:b+root",
                            "Intro <image attachment=\"example.com/att+hero\" display-size=\"medium\">"
                                + "<caption>Hero diagram</caption></image> outro",
                            0,
                            0)))),
            null,
            0);

    Assert.assertEquals(1, projected.getReadBlips().size());
    J2clReadBlip blip = projected.getReadBlips().get(0);
    Assert.assertEquals("Intro  outro", blip.getText());
    Assert.assertEquals(1, blip.getAttachments().size());
    J2clAttachmentRenderModel attachment = blip.getAttachments().get(0);
    Assert.assertEquals("example.com/att+hero", attachment.getAttachmentId());
    Assert.assertEquals("medium", attachment.getDisplaySize());
    Assert.assertEquals("Hero diagram", attachment.getCaption());
    Assert.assertTrue(attachment.isMetadataPending());
    Assert.assertEquals(
        1, projected.getViewportState().getReadWindowEntries().get(0).getAttachments().size());
  }

  @Test
  public void viewportHydratesPendingFragmentAttachmentMetadata() {
    J2clSelectedWaveViewportState state = viewportWithAttachment();

    state =
        state.withAttachmentMetadata(
            Arrays.asList(
                attachmentMetadata(
                    "example.com/att+hero",
                    "hero.png",
                    "image/png",
                    "/attachments/hero.png",
                    "/thumbnails/hero.png",
                    false)),
            Collections.<String>emptyList());

    J2clAttachmentRenderModel attachment =
        state.getLoadedReadBlips().get(0).getAttachments().get(0);
    Assert.assertFalse(attachment.isMetadataPending());
    Assert.assertTrue(attachment.canOpen());
    Assert.assertTrue(attachment.canDownload());
    Assert.assertEquals("/attachments/hero.png", attachment.getOpenUrl());
    Assert.assertEquals("/attachments/hero.png", attachment.getSourceUrl());
    Assert.assertEquals(
        attachment, state.getReadWindowEntries().get(0).getAttachments().get(0));
  }

  @Test
  public void viewportMarksMissingAttachmentMetadataAsFailure() {
    J2clSelectedWaveViewportState state = viewportWithAttachment();

    state =
        state.withAttachmentMetadata(
            Collections.<J2clAttachmentMetadata>emptyList(),
            Arrays.asList("example.com/att+hero"));

    J2clAttachmentRenderModel attachment =
        state.getLoadedReadBlips().get(0).getAttachments().get(0);
    Assert.assertFalse(attachment.isMetadataPending());
    Assert.assertTrue(attachment.isMetadataFailure());
    Assert.assertFalse(attachment.canOpen());
    Assert.assertEquals("Hero diagram", attachment.getCaption());
    Assert.assertEquals(
        attachment, state.getReadWindowEntries().get(0).getAttachments().get(0));
  }

  @Test
  public void viewportPreservesResolvedAttachmentWhenResolvingAnotherBatch() {
    J2clSelectedWaveViewportState state = viewportWithTwoAttachments();

    state =
        state.withAttachmentMetadata(
            Arrays.asList(
                attachmentMetadata(
                    "example.com/att+hero",
                    "hero.png",
                    "image/png",
                    "/attachments/hero.png",
                    "/thumbnails/hero.png",
                    false)),
            Collections.<String>emptyList());
    J2clAttachmentRenderModel resolvedHero =
        state.getLoadedReadBlips().get(0).getAttachments().get(0);

    state =
        state.withAttachmentMetadata(
            Collections.<J2clAttachmentMetadata>emptyList(),
            Arrays.asList("example.com/att+diagram"));

    J2clAttachmentRenderModel hero =
        state.getLoadedReadBlips().get(0).getAttachments().get(0);
    J2clAttachmentRenderModel diagram =
        state.getLoadedReadBlips().get(0).getAttachments().get(1);
    Assert.assertEquals(resolvedHero, hero);
    Assert.assertFalse(hero.isMetadataPending());
    Assert.assertTrue(hero.canOpen());
    Assert.assertTrue(diagram.isMetadataFailure());
    Assert.assertFalse(diagram.isMetadataPending());
  }

  @Test
  public void viewportReusesParsedContentAcrossAttachmentResolution() {
    J2clSelectedWaveViewportState state = viewportWithAttachment();
    J2clReadBlipContent parsed = state.getEntries().get(0).getParsedContent();

    state =
        state.withAttachmentMetadata(
            Arrays.asList(
                attachmentMetadata(
                    "example.com/att+hero",
                    "hero.png",
                    "image/png",
                    "/attachments/hero.png",
                    "/thumbnails/hero.png",
                    false)),
            Collections.<String>emptyList());

    Assert.assertSame(parsed, state.getEntries().get(0).getParsedContent());
  }

  @Test
  public void viewportPreservesResolvedAttachmentAcrossSameRawFragmentMerge() {
    J2clSelectedWaveViewportState state = viewportWithAttachment();
    J2clReadBlipContent parsed = state.getEntries().get(0).getParsedContent();
    state =
        state.withAttachmentMetadata(
            Arrays.asList(
                attachmentMetadata(
                    "example.com/att+hero",
                    "hero.png",
                    "image/png",
                    "/attachments/hero.png",
                    "/thumbnails/hero.png",
                    false)),
            Collections.<String>emptyList());
    J2clAttachmentRenderModel resolved =
        state.getLoadedReadBlips().get(0).getAttachments().get(0);

    state =
        state.mergeFragments(
            attachmentFragments(10L, 0L, 10L),
            J2clViewportGrowthDirection.FORWARD);

    Assert.assertSame(parsed, state.getEntries().get(0).getParsedContent());
    Assert.assertEquals(resolved, state.getLoadedReadBlips().get(0).getAttachments().get(0));
    Assert.assertFalse(state.getPendingAttachmentIds().contains("example.com/att+hero"));
  }

  @Test
  public void viewportPreservesResolvedAttachmentAcrossPlaceholderFragmentMerge() {
    J2clSelectedWaveViewportState state = viewportWithAttachment();
    J2clReadBlipContent parsed = state.getEntries().get(0).getParsedContent();
    state =
        state.withAttachmentMetadata(
            Arrays.asList(
                attachmentMetadata(
                    "example.com/att+hero",
                    "hero.png",
                    "image/png",
                    "/attachments/hero.png",
                    "/thumbnails/hero.png",
                    false)),
            Collections.<String>emptyList());
    J2clAttachmentRenderModel resolved =
        state.getLoadedReadBlips().get(0).getAttachments().get(0);

    state =
        state.mergeFragments(
            new SidecarSelectedWaveFragments(
                10L,
                0L,
                10L,
                Arrays.asList(new SidecarSelectedWaveFragmentRange("blip:b+root", 0L, 10L)),
                Collections.<SidecarSelectedWaveFragment>emptyList()),
            J2clViewportGrowthDirection.FORWARD);

    Assert.assertSame(parsed, state.getEntries().get(0).getParsedContent());
    Assert.assertEquals(resolved, state.getLoadedReadBlips().get(0).getAttachments().get(0));
    Assert.assertFalse(state.getPendingAttachmentIds().contains("example.com/att+hero"));
  }

  @Test
  public void viewportDropsParsedCacheAndOverridesWhenRawFragmentChanges() {
    J2clSelectedWaveViewportState state = viewportWithAttachment();
    J2clReadBlipContent parsed = state.getEntries().get(0).getParsedContent();
    state =
        state.withAttachmentMetadata(
            Arrays.asList(
                attachmentMetadata(
                    "example.com/att+hero",
                    "hero.png",
                    "image/png",
                    "/attachments/hero.png",
                    "/thumbnails/hero.png",
                    false)),
            Collections.<String>emptyList());

    state =
        state.mergeFragments(
            new SidecarSelectedWaveFragments(
                10L,
                0L,
                10L,
                Arrays.asList(new SidecarSelectedWaveFragmentRange("blip:b+root", 0L, 10L)),
                Arrays.asList(
                    new SidecarSelectedWaveFragment(
                        "blip:b+root",
                        "Changed <image attachment=\"example.com/att+hero\" display-size=\"medium\">"
                            + "<caption>Changed hero</caption></image>",
                        0,
                        0))),
            J2clViewportGrowthDirection.FORWARD);

    Assert.assertNotSame(parsed, state.getEntries().get(0).getParsedContent());
    Assert.assertTrue(state.getLoadedReadBlips().get(0).getAttachments().get(0).isMetadataPending());
  }
}
