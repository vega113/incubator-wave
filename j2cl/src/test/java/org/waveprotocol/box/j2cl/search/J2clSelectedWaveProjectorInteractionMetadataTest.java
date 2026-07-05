package org.waveprotocol.box.j2cl.search;

import com.google.j2cl.junit.apt.J2clTestInput;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Assert;
import org.junit.Test;
import org.waveprotocol.box.j2cl.overlay.J2clInteractionBlipModel;
import org.waveprotocol.box.j2cl.overlay.J2clMentionRange;
import org.waveprotocol.box.j2cl.overlay.J2clReactionSummary;
import org.waveprotocol.box.j2cl.overlay.J2clTaskItemModel;
import org.waveprotocol.box.j2cl.transport.SidecarAnnotationRange;
import org.waveprotocol.box.j2cl.transport.SidecarReactionEntry;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveDocument;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveUpdate;

/**
 * #1270: split from the monster J2clSelectedWaveProjectorTest. Covers
 * interaction-blip metadata: mentions, tasks and reaction summaries from documents.
 */
@J2clTestInput(J2clSelectedWaveProjectorInteractionMetadataTest.class)
public class J2clSelectedWaveProjectorInteractionMetadataTest extends J2clSelectedWaveProjectorTestSupport {

  @Test
  public void projectBuildsInteractionBlipMetadataFromDocumentAnnotationsAndReactionDocs() {
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
                        8L,
                        "@Teammate review",
                        Arrays.asList(
                            new SidecarAnnotationRange(
                                "mention/user", "teammate@example.com", 0, 9),
                            new SidecarAnnotationRange("task/id", "task-123", 10, 16)),
                        Collections.<SidecarReactionEntry>emptyList()),
                    new SidecarSelectedWaveDocument(
                        "react+b+root",
                        "user@example.com",
                        7L,
                        8L,
                        "",
                        Collections.<SidecarAnnotationRange>emptyList(),
                        Arrays.asList(
                            new SidecarReactionEntry(
                                "thumbs_up", Arrays.asList("alice@example.com"))))),
                null),
            null,
            0);

    Assert.assertEquals(1, projected.getInteractionBlips().size());
    J2clInteractionBlipModel blip = projected.getInteractionBlips().get(0);
    Assert.assertEquals("b+root", blip.getBlipId());
    Assert.assertEquals("@Teammate review", blip.getText());
    Assert.assertEquals(2, blip.getAnnotationRanges().size());
    Assert.assertEquals("mention/user", blip.getAnnotationRanges().get(0).getKey());
    Assert.assertEquals("task/id", blip.getAnnotationRanges().get(1).getKey());
    Assert.assertEquals(1, blip.getReactionEntries().size());
    Assert.assertEquals("thumbs_up", blip.getReactionEntries().get(0).getEmoji());
  }

  @Test
  public void projectRefinesMentionRangesFromMentionAnnotations() {
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
                        8L,
                        "Hi @Teammate",
                        Arrays.asList(
                            new SidecarAnnotationRange(
                                "mention/user", "teammate@example.com", 3, 12)),
                        Collections.<SidecarReactionEntry>emptyList())),
                null),
            null,
            0);

    J2clInteractionBlipModel blip = projected.getInteractionBlips().get(0);
    Assert.assertEquals(1, blip.getMentionRanges().size());
    J2clMentionRange mention = blip.getMentionRanges().get(0);
    Assert.assertEquals(3, mention.getStartOffset());
    Assert.assertEquals(12, mention.getEndOffset());
    Assert.assertEquals("teammate@example.com", mention.getUserAddress());
    Assert.assertEquals("@Teammate", mention.getDisplayText());
    Assert.assertTrue(projected.getReadBlips().get(0).hasMention());
  }

  @Test
  public void projectRejectsManualLinkAddressAsMentionMetadata() {
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
                        8L,
                        "Hi @Teammate",
                        Arrays.asList(
                            new SidecarAnnotationRange(
                                "link/manual", "teammate@example.com", 3, 12)),
                        Collections.<SidecarReactionEntry>emptyList())),
                null),
            null,
            0);

    J2clInteractionBlipModel interactionBlip = projected.getInteractionBlips().get(0);
    Assert.assertTrue(interactionBlip.getMentionRanges().isEmpty());
    Assert.assertFalse(projected.getReadBlips().get(0).hasMention());
  }

  @Test
  public void projectRejectsManualLinkValueStartingWithAtAsMentionMetadata() {
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
                        8L,
                        "Hi @Teammate",
                        Arrays.asList(
                            new SidecarAnnotationRange(
                                "link/manual", "@teammate@example.com", 3, 12)),
                        Collections.<SidecarReactionEntry>emptyList())),
                null),
            null,
            0);

    J2clInteractionBlipModel interactionBlip = projected.getInteractionBlips().get(0);
    Assert.assertTrue(interactionBlip.getMentionRanges().isEmpty());
    Assert.assertFalse(projected.getReadBlips().get(0).hasMention());
  }

  @Test
  public void projectRejectsManualLinkUrlWithAtAsMentionMetadata() {
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
                        8L,
                        "See @Teammate",
                        Arrays.asList(
                            new SidecarAnnotationRange(
                                "link/manual", "mailto:teammate@example.com", 4, 13)),
                        Collections.<SidecarReactionEntry>emptyList())),
                null),
            null,
            0);

    J2clInteractionBlipModel interactionBlip = projected.getInteractionBlips().get(0);
    Assert.assertTrue(interactionBlip.getMentionRanges().isEmpty());
    Assert.assertFalse(projected.getReadBlips().get(0).hasMention());
  }

  @Test
  public void projectRefinesTaskItemsFromTaskAnnotationsWithSharedRange() {
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
                        8L,
                        "Review spec",
                        Arrays.asList(
                            new SidecarAnnotationRange("task/id", "task-123", 0, 11),
                            new SidecarAnnotationRange(
                                "task/assignee", "alice@example.com", 0, 11),
                            new SidecarAnnotationRange("task/dueTs", "1714000000000", 0, 11)),
                        Collections.<SidecarReactionEntry>emptyList())),
                null),
            null,
            0);

    J2clInteractionBlipModel blip = projected.getInteractionBlips().get(0);
    Assert.assertEquals(1, blip.getTaskItems().size());
    J2clTaskItemModel task = blip.getTaskItems().get(0);
    Assert.assertEquals("task-123", task.getTaskId());
    Assert.assertEquals(0, task.getTextOffset());
    Assert.assertEquals("task-b+root-task-123", task.getElementAnchorId());
    Assert.assertEquals("alice@example.com", task.getAssigneeAddress());
    Assert.assertEquals(1714000000000L, task.getDueTimestamp());
    Assert.assertFalse(task.isChecked());
    Assert.assertTrue(task.isEditable());
  }

  @Test
  public void projectMarksInteractionBlipsReadOnlyWithoutWriteSession() {
    J2clSelectedWaveModel projected =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 0),
            new SidecarSelectedWaveUpdate(
                1,
                WAVELET_NAME,
                true,
                "",
                9L,
                "HASH",
                Arrays.asList("user@example.com"),
                Arrays.asList(
                    new SidecarSelectedWaveDocument(
                        "b+root",
                        "user@example.com",
                        7L,
                        8L,
                        "Review spec",
                        Arrays.asList(
                            new SidecarAnnotationRange("task/id", "task-123", 0, 11)),
                        Collections.<SidecarReactionEntry>emptyList())),
                null),
            null,
            0);

    Assert.assertNull(projected.getWriteSession());
    J2clInteractionBlipModel blip = projected.getInteractionBlips().get(0);
    Assert.assertFalse(blip.isEditable());
    Assert.assertEquals(1, blip.getTaskItems().size());
    Assert.assertFalse(blip.getTaskItems().get(0).isEditable());
  }

  @Test
  public void projectSkipsTaskItemsWithoutTaskId() {
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
                        8L,
                        "Review spec",
                        Arrays.asList(
                            new SidecarAnnotationRange("task/id", "", 0, 11),
                            new SidecarAnnotationRange(
                                "task/assignee", "alice@example.com", 0, 11)),
                        Collections.<SidecarReactionEntry>emptyList())),
                null),
            null,
            0);

    Assert.assertTrue(projected.getInteractionBlips().get(0).getTaskItems().isEmpty());
  }

  @Test
  public void projectRequiresTaskMetadataAnnotationsToShareTaskIdRange() {
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
                        8L,
                        "Review spec",
                        Arrays.asList(
                            new SidecarAnnotationRange("task/id", "task-123", 0, 11),
                            new SidecarAnnotationRange(
                                "task/assignee", "alice@example.com", 0, 6)),
                        Collections.<SidecarReactionEntry>emptyList())),
                null),
            null,
            0);

    J2clTaskItemModel task = projected.getInteractionBlips().get(0).getTaskItems().get(0);
    Assert.assertEquals("", task.getAssigneeAddress());
  }

  @Test
  public void projectUsesUnknownDueTimestampForInvalidTaskDueAnnotation() {
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
                        8L,
                        "Review spec",
                        Arrays.asList(
                            new SidecarAnnotationRange("task/id", "task-123", 0, 11),
                            new SidecarAnnotationRange("task/dueTs", "tomorrow", 0, 11)),
                        Collections.<SidecarReactionEntry>emptyList())),
                null),
            null,
            0);

    J2clTaskItemModel task = projected.getInteractionBlips().get(0).getTaskItems().get(0);
    Assert.assertEquals(J2clTaskItemModel.UNKNOWN_DUE_TIMESTAMP, task.getDueTimestamp());
  }

  @Test
  public void projectRefinesReactionSummariesFromReactionDataDocuments() {
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
                        "b+root", "user@example.com", 7L, 8L, "Root text"),
                    new SidecarSelectedWaveDocument(
                        "react+b+root",
                        "user@example.com",
                        7L,
                        8L,
                        "",
                        Collections.<SidecarAnnotationRange>emptyList(),
                        Arrays.asList(
                            new SidecarReactionEntry(
                                "tada",
                                Arrays.asList("alice@example.com", "bob@example.com"))))),
                null),
            null,
            0);

    J2clInteractionBlipModel blip = projected.getInteractionBlips().get(0);
    Assert.assertEquals(1, blip.getReactionSummaries().size());
    J2clReactionSummary reaction = blip.getReactionSummaries().get(0);
    Assert.assertEquals("tada", reaction.getEmoji());
    Assert.assertEquals(2, reaction.getCount());
    Assert.assertEquals("alice@example.com", reaction.getParticipantAddresses().get(0));
    Assert.assertEquals("bob@example.com", reaction.getParticipantAddresses().get(1));
    Assert.assertFalse(reaction.isActiveForCurrentUser());
    Assert.assertEquals("2 reactions for tada.", reaction.getInspectLabel());
  }

  @Test
  public void projectKeepsEmptyOverlayListsAndContentEntriesForPlainBlips() {
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
                        "b+root", "user@example.com", 7L, 8L, "Plain text")),
                null),
            null,
            0);

    Assert.assertEquals(1, projected.getContentEntries().size());
    Assert.assertEquals("Plain text", projected.getContentEntries().get(0));
    J2clInteractionBlipModel blip = projected.getInteractionBlips().get(0);
    Assert.assertTrue(blip.getMentionRanges().isEmpty());
    Assert.assertTrue(blip.getTaskItems().isEmpty());
    Assert.assertTrue(blip.getReactionSummaries().isEmpty());
  }

  @Test
  public void projectMergesReactionOnlyDocumentUpdateIntoPreviousInteractionBlip() {
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
                Arrays.asList("user@example.com"),
                Arrays.asList(
                    new SidecarSelectedWaveDocument(
                        "b+root",
                        "user@example.com",
                        7L,
                        8L,
                        "Root text",
                        Arrays.asList(
                            new SidecarAnnotationRange(
                                "mention/user", "teammate@example.com", 0, 4)),
                        Collections.<SidecarReactionEntry>emptyList())),
                null),
            null,
            0);

    J2clSelectedWaveModel reactionOnly =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 0),
            new SidecarSelectedWaveUpdate(
                2,
                WAVELET_NAME,
                true,
                CHANNEL_ID,
                10L,
                "HASH2",
                Arrays.asList("user@example.com"),
                Arrays.asList(
                    new SidecarSelectedWaveDocument(
                        "react+b+root",
                        "user@example.com",
                        10L,
                        10L,
                        "",
                        Collections.<SidecarAnnotationRange>emptyList(),
                        Arrays.asList(
                            new SidecarReactionEntry(
                                "tada", Arrays.asList("alice@example.com"))))),
                null),
            first,
            0);

    Assert.assertEquals(1, reactionOnly.getInteractionBlips().size());
    J2clInteractionBlipModel blip = reactionOnly.getInteractionBlips().get(0);
    Assert.assertEquals("b+root", blip.getBlipId());
    Assert.assertEquals("Root text", blip.getText());
    Assert.assertEquals(1, blip.getAnnotationRanges().size());
    Assert.assertEquals("mention/user", blip.getAnnotationRanges().get(0).getKey());
    Assert.assertEquals(1, blip.getReactionEntries().size());
    Assert.assertEquals("tada", blip.getReactionEntries().get(0).getEmoji());
  }

  @Test
  public void projectTreatsEmptyReactionDocumentAsExplicitReactionClear() {
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
                Arrays.asList("user@example.com"),
                Arrays.asList(
                    new SidecarSelectedWaveDocument(
                        "b+root",
                        "user@example.com",
                        7L,
                        8L,
                        "Root text",
                        Arrays.asList(
                            new SidecarAnnotationRange(
                                "mention/user", "teammate@example.com", 0, 4)),
                        Collections.<SidecarReactionEntry>emptyList()),
                    new SidecarSelectedWaveDocument(
                        "react+b+root",
                        "user@example.com",
                        8L,
                        8L,
                        "",
                        Collections.<SidecarAnnotationRange>emptyList(),
                        Arrays.asList(
                            new SidecarReactionEntry(
                                "tada", Arrays.asList("alice@example.com"))))),
                null),
            null,
            0);

    J2clSelectedWaveModel cleared =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 0),
            new SidecarSelectedWaveUpdate(
                2,
                WAVELET_NAME,
                true,
                CHANNEL_ID,
                10L,
                "HASH2",
                Arrays.asList("user@example.com"),
                Arrays.asList(
                    new SidecarSelectedWaveDocument(
                        "react+b+root",
                        "user@example.com",
                        10L,
                        10L,
                        "",
                        Collections.<SidecarAnnotationRange>emptyList(),
                        Collections.<SidecarReactionEntry>emptyList())),
                null),
            first,
            0);

    J2clInteractionBlipModel blip = cleared.getInteractionBlips().get(0);
    Assert.assertEquals("Root text", blip.getText());
    Assert.assertEquals(1, blip.getAnnotationRanges().size());
    Assert.assertEquals(0, blip.getReactionEntries().size());
  }

  @Test
  public void projectPreservesInteractionBlipTextAcrossConsecutiveReactionOnlyUpdates() {
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
                Arrays.asList("user@example.com"),
                Arrays.asList(
                    new SidecarSelectedWaveDocument(
                        "b+root",
                        "user@example.com",
                        7L,
                        8L,
                        "Root text",
                        Arrays.asList(
                            new SidecarAnnotationRange(
                                "mention/user", "teammate@example.com", 0, 4)),
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
                "HASH2",
                Arrays.asList("user@example.com"),
                Arrays.asList(
                    new SidecarSelectedWaveDocument(
                        "react+b+root",
                        "user@example.com",
                        10L,
                        10L,
                        "",
                        Collections.<SidecarAnnotationRange>emptyList(),
                        Arrays.asList(
                            new SidecarReactionEntry(
                                "tada", Arrays.asList("alice@example.com"))))),
                null),
            first,
            0);

    J2clSelectedWaveModel third =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave A", "snippet", 0),
            new SidecarSelectedWaveUpdate(
                3,
                WAVELET_NAME,
                true,
                CHANNEL_ID,
                11L,
                "HASH3",
                Arrays.asList("user@example.com"),
                Arrays.asList(
                    new SidecarSelectedWaveDocument(
                        "react+b+root",
                        "user@example.com",
                        11L,
                        11L,
                        "",
                        Collections.<SidecarAnnotationRange>emptyList(),
                        Arrays.asList(
                            new SidecarReactionEntry(
                                "thumbs_up", Arrays.asList("bob@example.com"))))),
                null),
            second,
            0);

    J2clInteractionBlipModel blip = third.getInteractionBlips().get(0);
    Assert.assertEquals("Root text", blip.getText());
    Assert.assertEquals(1, blip.getAnnotationRanges().size());
    Assert.assertEquals("mention/user", blip.getAnnotationRanges().get(0).getKey());
    Assert.assertEquals(1, blip.getReactionEntries().size());
    Assert.assertEquals("thumbs_up", blip.getReactionEntries().get(0).getEmoji());
  }
}
