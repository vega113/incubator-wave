package org.waveprotocol.box.j2cl.search;

import com.google.j2cl.junit.apt.J2clTestInput;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Assert;
import org.junit.Test;
import org.waveprotocol.box.j2cl.read.J2clInlineReplyAnchor;
import org.waveprotocol.box.j2cl.read.J2clReadBlip;
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
 * document-blip fallback when fragments are absent and document/fragment merge switching.
 */
@J2clTestInput(J2clSelectedWaveProjectorDocumentFallbackTest.class)
public class J2clSelectedWaveProjectorDocumentFallbackTest extends J2clSelectedWaveProjectorTestSupport {

  @Test
  public void projectFallsBackToDocumentBlipsWhenFragmentsAreAbsent() {
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
                        "b+root", "user@example.com", 7L, 8L, "Document text"),
                    new SidecarSelectedWaveDocument(
                        "conversation", "user@example.com", 7L, 8L, "metadata")),
                null),
            null,
            0);

    Assert.assertEquals(1, projected.getReadBlips().size());
    J2clReadBlip blip = projected.getReadBlips().get(0);
    Assert.assertEquals("b+root", blip.getBlipId());
    Assert.assertEquals("Document text", blip.getText());
  }

  @Test
  public void projectDocumentFallbackPreservesLiteralMarkupAndComparisons() {
    String literalText =
        "Literal 2 < 3 and <image attachment=\"example.com/att+literal\"> stays text";
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
                        "b+root", "user@example.com", 7L, 8L, literalText)),
                null),
            null,
            0);

    Assert.assertEquals(1, projected.getReadBlips().size());
    J2clReadBlip blip = projected.getReadBlips().get(0);
    Assert.assertEquals(literalText, blip.getText());
    Assert.assertTrue(blip.getAttachments().isEmpty());
    Assert.assertEquals(
        literalText, projected.getViewportState().getReadWindowEntries().get(0).getText());
    Assert.assertTrue(
        projected.getViewportState().getReadWindowEntries().get(0).getAttachments().isEmpty());
  }

  @Test
  public void viewportDocumentMergeOverFragmentSwitchesBackToLiteralText() {
    J2clSelectedWaveViewportState state =
        J2clSelectedWaveViewportState.fromFragments(
            new SidecarSelectedWaveFragments(
                9L,
                0L,
                9L,
                Arrays.asList(new SidecarSelectedWaveFragmentRange("blip:b+root", 0L, 9L)),
                Arrays.asList(
                    new SidecarSelectedWaveFragment(
                        "blip:b+root",
                        "<image attachment=\"example.com/att+hero\">"
                            + "<caption>Hero</caption></image>",
                        0,
                        0))));
    String literalText = "Literal 2 < 3 and <image attachment=\"example.com/att+literal\">";

    state =
        state.mergeDocuments(
            Arrays.asList(
                new SidecarSelectedWaveDocument(
                    "b+root",
                    "user@example.com",
                    7L,
                    10L,
                    literalText,
                    /* bodyItemCount= */ 31,
                    Collections.<SidecarAnnotationRange>emptyList(),
                    Collections.<SidecarReactionEntry>emptyList())));

    Assert.assertEquals(literalText, state.getLoadedReadBlips().get(0).getText());
    Assert.assertTrue(state.getLoadedReadBlips().get(0).getAttachments().isEmpty());
    Assert.assertEquals(31, state.getLoadedReadBlips().get(0).getBodyItemCount());
    Assert.assertEquals(literalText, state.getReadWindowEntries().get(0).getText());
    Assert.assertTrue(state.getReadWindowEntries().get(0).getAttachments().isEmpty());
    Assert.assertEquals(31, state.getReadWindowEntries().get(0).getBodyItemCount());
  }

  @Test
  public void viewportDocumentMergePreservesInlineReplyAnchorsFromFragment() {
    J2clSelectedWaveViewportState state =
        J2clSelectedWaveViewportState.fromFragments(
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
                        0))));

    state =
        state.mergeDocuments(
            Arrays.asList(
                new SidecarSelectedWaveDocument(
                    "b+root",
                    "user@example.com",
                    7L,
                    10L,
                    "Before  after",
                    /* bodyItemCount= */ 13,
                    Collections.<SidecarAnnotationRange>emptyList(),
                    Collections.<SidecarReactionEntry>emptyList())));

    Assert.assertEquals("Before  after", state.getLoadedReadBlips().get(0).getText());
    Assert.assertEquals(1, state.getLoadedReadBlips().get(0).getInlineReplyAnchors().size());
    Assert.assertEquals(
        "t+inline", state.getLoadedReadBlips().get(0).getInlineReplyAnchors().get(0).getThreadId());
    Assert.assertEquals(1, state.getReadWindowEntries().get(0).getInlineReplyAnchors().size());
    Assert.assertEquals(
        "t+inline",
        state.getReadWindowEntries().get(0).getInlineReplyAnchors().get(0).getThreadId());
  }

  @Test
  public void viewportDocumentMergeUsesFreshDocumentInlineReplyAnchors() {
    J2clSelectedWaveViewportState state =
        J2clSelectedWaveViewportState.fromFragments(
            new SidecarSelectedWaveFragments(
                9L,
                0L,
                9L,
                Arrays.asList(new SidecarSelectedWaveFragmentRange("blip:b+root", 0L, 9L)),
                Arrays.asList(
                    new SidecarSelectedWaveFragment(
                        "blip:b+root",
                        "<body><line/>Before <reply id=\"t+old\"></reply> after</body>",
                        0,
                        0))));

    state =
        state.mergeDocuments(
            Arrays.asList(
                new SidecarSelectedWaveDocument(
                    "b+root",
                    "user@example.com",
                    7L,
                    10L,
                    "Updated  after",
                    /* bodyItemCount= */ 18,
                    Collections.<SidecarAnnotationRange>emptyList(),
                    Collections.<SidecarReactionEntry>emptyList(),
                    SidecarSelectedWaveDocument.LOCK_STATE_UNLOCKED,
                    Arrays.asList(new J2clInlineReplyAnchor("t+new", "Updated ".length())))));

    Assert.assertEquals("Updated  after", state.getLoadedReadBlips().get(0).getText());
    Assert.assertEquals(1, state.getLoadedReadBlips().get(0).getInlineReplyAnchors().size());
    Assert.assertEquals(
        "t+new", state.getLoadedReadBlips().get(0).getInlineReplyAnchors().get(0).getThreadId());
    Assert.assertEquals(
        "Updated ".length(),
        state.getLoadedReadBlips().get(0).getInlineReplyAnchors().get(0).getTextOffset());
    Assert.assertEquals(
        "t+new",
        state.getReadWindowEntries().get(0).getInlineReplyAnchors().get(0).getThreadId());
  }

  @Test
  public void viewportFragmentMergeOverDocumentRestoresAttachmentParsing() {
    J2clSelectedWaveViewportState state =
        J2clSelectedWaveViewportState.fromDocuments(
            Arrays.asList(
                new SidecarSelectedWaveDocument(
                    "b+root", "user@example.com", 7L, 8L, "Literal <image> text")));

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
                        "Intro <image attachment=\"example.com/att+hero\">"
                            + "<caption>Hero</caption></image> outro",
                        0,
                        0))),
            J2clViewportGrowthDirection.FORWARD);

    Assert.assertEquals("Intro  outro", state.getLoadedReadBlips().get(0).getText());
    Assert.assertEquals(1, state.getLoadedReadBlips().get(0).getAttachments().size());
    Assert.assertEquals(1, state.getReadWindowEntries().get(0).getAttachments().size());
  }

  @Test
  public void viewportPlaceholderMergePreservesFragmentAttachmentParsing() {
    J2clSelectedWaveViewportState state =
        J2clSelectedWaveViewportState.fromFragments(
            new SidecarSelectedWaveFragments(
                9L,
                0L,
                9L,
                Arrays.asList(new SidecarSelectedWaveFragmentRange("blip:b+root", 0L, 9L)),
                Arrays.asList(
                    new SidecarSelectedWaveFragment(
                        "blip:b+root",
                        "Intro <image attachment=\"example.com/att+hero\">"
                            + "<caption>Hero</caption></image> outro",
                        /* bodyItemCount= */ 42,
                        0,
                        0))));

    state =
        state.mergeFragments(
            new SidecarSelectedWaveFragments(
                10L,
                0L,
                10L,
                Arrays.asList(new SidecarSelectedWaveFragmentRange("blip:b+root", 0L, 10L)),
                Collections.<SidecarSelectedWaveFragment>emptyList()),
            J2clViewportGrowthDirection.FORWARD);

    Assert.assertEquals("Intro  outro", state.getLoadedReadBlips().get(0).getText());
    Assert.assertEquals(1, state.getLoadedReadBlips().get(0).getAttachments().size());
    Assert.assertEquals(42, state.getLoadedReadBlips().get(0).getBodyItemCount());
    Assert.assertEquals(1, state.getReadWindowEntries().get(0).getAttachments().size());
    Assert.assertEquals(42, state.getReadWindowEntries().get(0).getBodyItemCount());
  }

  @Test
  public void loadedEntryWithoutExplicitBodyItemCountTreatsSizeUnknown() {
    J2clSelectedWaveViewportState.Entry entry =
        J2clSelectedWaveViewportState.Entry.loaded(
            "blip:b+root",
            0L,
            9L,
            "<body><line/>Raw debug XML should not imply a body count</body>",
            0,
            0);

    Assert.assertEquals(0, entry.getBodyItemCount());
  }
}
