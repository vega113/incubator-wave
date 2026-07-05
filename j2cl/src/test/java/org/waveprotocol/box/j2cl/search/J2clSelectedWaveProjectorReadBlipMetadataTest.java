package org.waveprotocol.box.j2cl.search;

import com.google.j2cl.junit.apt.J2clTestInput;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Assert;
import org.junit.Test;
import org.waveprotocol.box.j2cl.attachment.J2clAttachmentRenderModel;
import org.waveprotocol.box.j2cl.overlay.J2clTaskItemModel;
import org.waveprotocol.box.j2cl.read.J2clReadBlip;
import org.waveprotocol.box.j2cl.transport.SidecarAnnotationRange;
import org.waveprotocol.box.j2cl.transport.SidecarReactionEntry;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveDocument;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveFragment;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveFragmentRange;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveFragments;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveUpdate;

/**
 * #1270: split from the monster J2clSelectedWaveProjectorTest. Covers
 * read-blip metadata enrichment, digest fallbacks and task done/assignee/due parsing.
 */
@J2clTestInput(J2clSelectedWaveProjectorReadBlipMetadataTest.class)
public class J2clSelectedWaveProjectorReadBlipMetadataTest extends J2clSelectedWaveProjectorTestSupport {

  // -- F-2 (#1037) per-blip metadata enrichment --------------------------------

  @Test
  public void documentReadBlipsCarryAuthorTimestampMention() {
    SidecarAnnotationRange mention =
        new SidecarAnnotationRange("mention/me", "alice@example.com", 0, 5);
    SidecarSelectedWaveDocument doc =
        new SidecarSelectedWaveDocument(
            "b+root",
            "alice@example.com",
            7L,
            1714134000000L,
            "Hello @alice",
            Arrays.asList(mention),
            Collections.<SidecarReactionEntry>emptyList());

    J2clSelectedWaveModel projected =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave", "snippet", 0),
            new SidecarSelectedWaveUpdate(
                1,
                WAVELET_NAME,
                true,
                CHANNEL_ID,
                7L,
                "HASH",
                Arrays.asList("alice@example.com"),
                Arrays.asList(doc),
                null),
            null,
            0);

    Assert.assertEquals(1, projected.getReadBlips().size());
    J2clReadBlip blip = projected.getReadBlips().get(0);
    Assert.assertEquals("b+root", blip.getBlipId());
    Assert.assertEquals("Hello @alice", blip.getText());
    Assert.assertEquals("alice@example.com", blip.getAuthorId());
    Assert.assertEquals("alice@example.com", blip.getAuthorDisplayName());
    Assert.assertEquals(1714134000000L, blip.getLastModifiedTimeMillis());
    Assert.assertTrue("annotation key 'mention/me' marks the blip as a mention", blip.hasMention());
  }

  @Test
  public void viewportReadBlipsAreEnrichedWithDocumentMetadata() {
    // Viewport-shaped fragment payload (no per-blip metadata) PLUS the same
    // wire-update carrying a document for the same blip — F-2 grafts the
    // document's author + timestamp + mention onto the viewport-derived blip.
    SidecarSelectedWaveDocument doc =
        new SidecarSelectedWaveDocument(
            "b+root",
            "bob@example.com",
            42L,
            1714240000000L,
            "Body",
            Collections.<SidecarAnnotationRange>emptyList(),
            Collections.<SidecarReactionEntry>emptyList());

    J2clSelectedWaveModel projected =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest("Wave", "snippet", 0),
            new SidecarSelectedWaveUpdate(
                1,
                WAVELET_NAME,
                true,
                CHANNEL_ID,
                42L,
                "HASH",
                Arrays.asList("bob@example.com"),
                Arrays.asList(doc),
                new SidecarSelectedWaveFragments(
                    42L,
                    0L,
                    42L,
                    Arrays.asList(new SidecarSelectedWaveFragmentRange("blip:b+root", 0L, 42L)),
                    Arrays.asList(new SidecarSelectedWaveFragment("blip:b+root", "Body", 0, 0)))),
            null,
            0);

    Assert.assertEquals(1, projected.getReadBlips().size());
    J2clReadBlip blip = projected.getReadBlips().get(0);
    Assert.assertEquals("b+root", blip.getBlipId());
    Assert.assertEquals("Body", blip.getText());
    Assert.assertEquals("bob@example.com", blip.getAuthorId());
    Assert.assertEquals(1714240000000L, blip.getLastModifiedTimeMillis());
    Assert.assertFalse(blip.hasMention());
  }

  @Test
  public void viewportReadBlipsUseDigestMetadataFallbackWhenDocumentsLag() {
    J2clSearchDigestItem digest =
        new J2clSearchDigestItem(
            WAVE_ID,
            "Wave",
            "snippet",
            "author@example.com",
            0,
            1,
            1714240000000L,
            false);

    J2clSelectedWaveModel projected =
        J2clSelectedWaveProjector.project(
            WAVE_ID,
            digest,
            new SidecarSelectedWaveUpdate(
                1,
                WAVELET_NAME,
                true,
                CHANNEL_ID,
                42L,
                "HASH",
                Arrays.asList("author@example.com"),
                Collections.<SidecarSelectedWaveDocument>emptyList(),
                new SidecarSelectedWaveFragments(
                    42L,
                    0L,
                    42L,
                    Arrays.asList(new SidecarSelectedWaveFragmentRange("blip:b+root", 0L, 42L)),
                    Arrays.asList(new SidecarSelectedWaveFragment("blip:b+root", "Body", 0, 0)))),
            null,
            0);

    Assert.assertEquals(1, projected.getReadBlips().size());
    J2clReadBlip blip = projected.getReadBlips().get(0);
    Assert.assertEquals("author@example.com", blip.getAuthorId());
    Assert.assertEquals("author@example.com", blip.getAuthorDisplayName());
    Assert.assertEquals(1714240000000L, blip.getLastModifiedTimeMillis());
  }

  @Test
  public void viewportMetadataFallbacksPreservePreviousBlipsAndPatchNewFragments() {
    J2clSearchDigestItem digest =
        new J2clSearchDigestItem(
            WAVE_ID,
            "Wave",
            "snippet",
            "digest-author@example.com",
            0,
            2,
            1714240000000L,
            false);
    J2clReadBlip previousRoot =
        new J2clReadBlip(
            "b+root",
            "Root before growth",
            Collections.<J2clAttachmentRenderModel>emptyList(),
            "root-author@example.com",
            "Root Author",
            1714230000000L,
            "",
            "",
            /* unread= */ true,
            /* hasMention= */ true,
            /* deleted= */ false,
            /* taskDone= */ true,
            /* taskAssignee= */ "assignee@example.com",
            /* taskDueTimestamp= */ 1714560000000L);

    java.util.List<J2clReadBlip> enriched =
        J2clSelectedWaveProjector.applyViewportMetadataFallbacks(
            Arrays.asList(
                new J2clReadBlip("b+root", "Root after growth"),
                new J2clReadBlip("b+next", "Next after growth")),
            Arrays.asList(previousRoot),
            digest);

    Assert.assertEquals(2, enriched.size());
    J2clReadBlip root = enriched.get(0);
    Assert.assertEquals("Root after growth", root.getText());
    Assert.assertEquals("root-author@example.com", root.getAuthorId());
    Assert.assertEquals(1714230000000L, root.getLastModifiedTimeMillis());
    Assert.assertFalse(root.isUnread());
    Assert.assertFalse(root.hasMention());
    Assert.assertFalse(root.isTaskDone());
    Assert.assertEquals("assignee@example.com", root.getTaskAssignee());
    Assert.assertEquals(1714560000000L, root.getTaskDueTimestamp());

    J2clReadBlip next = enriched.get(1);
    Assert.assertEquals("Next after growth", next.getText());
    Assert.assertEquals("digest-author@example.com", next.getAuthorId());
    Assert.assertEquals(1714240000000L, next.getLastModifiedTimeMillis());
  }

  @Test
  public void viewportMetadataFallbacksDoNotResurrectAuthoritativeFalseBooleans() {
    J2clReadBlip previous =
        new J2clReadBlip(
            "b+root",
            "Root before growth",
            Collections.<J2clAttachmentRenderModel>emptyList(),
            "root-author@example.com",
            "Root Author",
            1714230000000L,
            "",
            "",
            /* unread= */ true,
            /* hasMention= */ true,
            /* deleted= */ true,
            /* taskDone= */ true,
            /* taskAssignee= */ "assignee@example.com",
            /* taskDueTimestamp= */ 1714560000000L);
    J2clReadBlip refreshed =
        new J2clReadBlip(
            "b+root",
            "Root after growth",
            Collections.<J2clAttachmentRenderModel>emptyList(),
            "",
            "",
            0L,
            "",
            "",
            /* unread= */ false,
            /* hasMention= */ false,
            /* deleted= */ false,
            /* taskDone= */ false,
            /* taskAssignee= */ "",
            /* taskDueTimestamp= */ J2clTaskItemModel.UNKNOWN_DUE_TIMESTAMP);

    java.util.List<J2clReadBlip> enriched =
        J2clSelectedWaveProjector.applyViewportMetadataFallbacks(
            Arrays.asList(refreshed), Arrays.asList(previous), null);

    Assert.assertEquals(1, enriched.size());
    J2clReadBlip root = enriched.get(0);
    Assert.assertEquals("root-author@example.com", root.getAuthorId());
    Assert.assertEquals(1714230000000L, root.getLastModifiedTimeMillis());
    Assert.assertFalse("fresh unread=false must not be OR-merged with stale true", root.isUnread());
    Assert.assertFalse("fresh hasMention=false must not be OR-merged with stale true", root.hasMention());
    Assert.assertFalse("fresh deleted=false must not be OR-merged with stale true", root.isDeleted());
    Assert.assertFalse("fresh taskDone=false must not be OR-merged with stale true", root.isTaskDone());
    Assert.assertEquals("assignee@example.com", root.getTaskAssignee());
    Assert.assertEquals(1714560000000L, root.getTaskDueTimestamp());
  }

  @Test
  public void viewportMetadataFallbacksCanPreservePreviousBooleansForFragmentGrowth() {
    J2clReadBlip previous =
        new J2clReadBlip(
            "b+root",
            "Root before growth",
            Collections.<J2clAttachmentRenderModel>emptyList(),
            "root-author@example.com",
            "Root Author",
            1714230000000L,
            "b+parent",
            "thread-1",
            /* unread= */ true,
            /* hasMention= */ true,
            /* deleted= */ false,
            /* taskDone= */ true,
            /* taskAssignee= */ "assignee@example.com",
            /* taskDueTimestamp= */ 1714560000000L);
    J2clReadBlip refreshed =
        new J2clReadBlip(
            "b+root",
            "Root after growth",
            Collections.<J2clAttachmentRenderModel>emptyList(),
            "",
            "",
            0L,
            "",
            "",
            /* unread= */ false,
            /* hasMention= */ false,
            /* deleted= */ false,
            /* taskDone= */ false,
            /* taskAssignee= */ "",
            /* taskDueTimestamp= */ J2clTaskItemModel.UNKNOWN_DUE_TIMESTAMP);

    java.util.List<J2clReadBlip> enriched =
        J2clSelectedWaveProjector.applyViewportMetadataFallbacks(
            Arrays.asList(refreshed),
            Arrays.asList(previous),
            null,
            /* preserveFallbackBooleans= */ true);

    Assert.assertEquals(1, enriched.size());
    J2clReadBlip root = enriched.get(0);
    Assert.assertEquals("Root after growth", root.getText());
    Assert.assertEquals("root-author@example.com", root.getAuthorId());
    Assert.assertEquals("Root Author", root.getAuthorDisplayName());
    Assert.assertEquals(1714230000000L, root.getLastModifiedTimeMillis());
    Assert.assertEquals("b+parent", root.getParentBlipId());
    Assert.assertEquals("thread-1", root.getThreadId());
    Assert.assertTrue("fragment growth must preserve prior unread state", root.isUnread());
    Assert.assertTrue("fragment growth must preserve prior mention state", root.hasMention());
    Assert.assertFalse(root.isDeleted());
    Assert.assertTrue("fragment growth must preserve prior taskDone state", root.isTaskDone());
    Assert.assertEquals("assignee@example.com", root.getTaskAssignee());
    Assert.assertEquals(1714560000000L, root.getTaskDueTimestamp());
  }

  @Test
  public void enrichReadBlipMetadataReturnsInputWhenInputsAreEmpty() {
    Assert.assertSame(
        Collections.<J2clReadBlip>emptyList(),
        J2clSelectedWaveProjector.enrichReadBlipMetadata(
            Collections.<J2clReadBlip>emptyList(),
            Collections.<SidecarSelectedWaveDocument>emptyList()));

    java.util.List<J2clReadBlip> blips =
        Arrays.asList(new J2clReadBlip("b+x", "y"));
    Assert.assertSame(
        blips,
        J2clSelectedWaveProjector.enrichReadBlipMetadata(
            blips, Collections.<SidecarSelectedWaveDocument>emptyList()));
  }

  @Test
  public void enrichReadBlipMetadataPreservesParentAndThreadLinkage() {
    // F-2 (#1037, R-3.7) — the helper is meant to *enrich* viewport-derived
    // read blips with author + last-modified + mention metadata sourced
    // from the matching SidecarSelectedWaveDocument. It must not erase the
    // parentBlipId / threadId already carried on the read blip — the
    // projector's metadata source does not know about thread linkage and
    // wiping those fields breaks R-3.7 depth-nav drill-in / inline-reply
    // chip rendering.
    J2clReadBlip blipWithLinkage =
        new J2clReadBlip(
            "b+child",
            "Reply text",
            Collections.<org.waveprotocol.box.j2cl.attachment.J2clAttachmentRenderModel>emptyList(),
            /* authorId= */ "",
            /* authorDisplayName= */ "",
            /* lastModifiedTimeMillis= */ 0L,
            /* parentBlipId= */ "b+parent",
            /* threadId= */ "t+inline",
            /* unread= */ false,
            /* hasMention= */ false);

    SidecarSelectedWaveDocument document =
        new SidecarSelectedWaveDocument(
            "b+child",
            "alice@example.com",
            7L,
            1714240000000L,
            "Reply text");

    java.util.List<J2clReadBlip> enriched =
        J2clSelectedWaveProjector.enrichReadBlipMetadata(
            Arrays.asList(blipWithLinkage), Arrays.asList(document));

    Assert.assertEquals(1, enriched.size());
    J2clReadBlip out = enriched.get(0);
    Assert.assertEquals("alice@example.com", out.getAuthorId());
    Assert.assertEquals(1714240000000L, out.getLastModifiedTimeMillis());
    Assert.assertEquals("b+parent", out.getParentBlipId());
    Assert.assertEquals("t+inline", out.getThreadId());
  }

  // -- J-UI-6 (#1084, R-5.4) — task done state plumbing ------------------------

  @Test
  public void documentTaskDoneTrueWhenAnnotationCarriesTrue() {
    SidecarSelectedWaveDocument document =
        new SidecarSelectedWaveDocument(
            "b+root",
            "alice@example.com",
            7L,
            1714240000000L,
            "Pin the retry",
            Arrays.asList(new SidecarAnnotationRange("task/done", "true", 0, 14)),
            Collections.<SidecarReactionEntry>emptyList());
    Assert.assertTrue(J2clSelectedWaveProjector.documentTaskDone(document));
  }

  @Test
  public void documentTaskDoneFalseForFalsyOrAbsentValues() {
    // task/done annotation with a non-"true" value reads as open. The
    // delta-factory writes the literal string "false" when reopening.
    SidecarSelectedWaveDocument falseDoc =
        new SidecarSelectedWaveDocument(
            "b+root",
            "alice@example.com",
            7L,
            1714240000000L,
            "Pin the retry",
            Arrays.asList(new SidecarAnnotationRange("task/done", "false", 0, 14)),
            Collections.<SidecarReactionEntry>emptyList());
    Assert.assertFalse(J2clSelectedWaveProjector.documentTaskDone(falseDoc));

    SidecarSelectedWaveDocument noAnnotation =
        new SidecarSelectedWaveDocument(
            "b+root", "alice@example.com", 7L, 1714240000000L, "Pin the retry");
    Assert.assertFalse(J2clSelectedWaveProjector.documentTaskDone(noAnnotation));
  }

  @Test
  public void documentTaskAssigneeReadsAnnotation() {
    SidecarSelectedWaveDocument document =
        new SidecarSelectedWaveDocument(
            "b+root",
            "alice@example.com",
            7L,
            1714240000000L,
            "Pin the retry",
            Arrays.asList(new SidecarAnnotationRange("task/assignee", "bob@example.com", 0, 14)),
            Collections.<SidecarReactionEntry>emptyList());
    Assert.assertEquals("bob@example.com", J2clSelectedWaveProjector.documentTaskAssignee(document));
  }

  @Test
  public void documentTaskAssigneeEmptyForUnsetAnnotation() {
    SidecarSelectedWaveDocument document =
        new SidecarSelectedWaveDocument(
            "b+root", "alice@example.com", 7L, 1714240000000L, "Pin the retry");
    Assert.assertEquals("", J2clSelectedWaveProjector.documentTaskAssignee(document));
  }

  @Test
  public void documentTaskDueTimestampParsesNumericAnnotation() {
    SidecarSelectedWaveDocument document =
        new SidecarSelectedWaveDocument(
            "b+root",
            "alice@example.com",
            7L,
            1714240000000L,
            "Pin the retry",
            Arrays.asList(new SidecarAnnotationRange("task/dueTs", "1714560000000", 0, 14)),
            Collections.<SidecarReactionEntry>emptyList());
    Assert.assertEquals(
        1714560000000L, J2clSelectedWaveProjector.documentTaskDueTimestamp(document));
  }

  @Test
  public void documentTaskDueTimestampUnknownForBlankOrUnparseable() {
    SidecarSelectedWaveDocument blank =
        new SidecarSelectedWaveDocument(
            "b+root",
            "alice@example.com",
            7L,
            1714240000000L,
            "Pin the retry",
            Arrays.asList(new SidecarAnnotationRange("task/dueTs", "", 0, 14)),
            Collections.<SidecarReactionEntry>emptyList());
    Assert.assertEquals(
        J2clTaskItemModel.UNKNOWN_DUE_TIMESTAMP,
        J2clSelectedWaveProjector.documentTaskDueTimestamp(blank));

    SidecarSelectedWaveDocument garbage =
        new SidecarSelectedWaveDocument(
            "b+root",
            "alice@example.com",
            7L,
            1714240000000L,
            "Pin the retry",
            Arrays.asList(new SidecarAnnotationRange("task/dueTs", "tomorrow", 0, 14)),
            Collections.<SidecarReactionEntry>emptyList());
    Assert.assertEquals(
        J2clTaskItemModel.UNKNOWN_DUE_TIMESTAMP,
        J2clSelectedWaveProjector.documentTaskDueTimestamp(garbage));
  }

  @Test
  public void enrichReadBlipMetadataPropagatesTaskDoneFromDocument() {
    // The enrichment pass is the bridge from wire-format documents to the
    // read model. A blip whose document carries task/done=true MUST end up
    // with isTaskDone() = true so the renderer can paint the strikethrough
    // on reload + live updates.
    J2clReadBlip viewportBlip = new J2clReadBlip("b+root", "Pin the retry");
    SidecarSelectedWaveDocument document =
        new SidecarSelectedWaveDocument(
            "b+root",
            "alice@example.com",
            7L,
            1714240000000L,
            "Pin the retry",
            18,
            Arrays.asList(
                new SidecarAnnotationRange("task/done", "true", 0, 14),
                new SidecarAnnotationRange("task/assignee", "bob@example.com", 0, 14),
                new SidecarAnnotationRange("task/dueTs", "1714560000000", 0, 14)),
            Collections.<SidecarReactionEntry>emptyList());

    java.util.List<J2clReadBlip> enriched =
        J2clSelectedWaveProjector.enrichReadBlipMetadata(
            Arrays.asList(viewportBlip), Arrays.asList(document));

    Assert.assertEquals(1, enriched.size());
    J2clReadBlip out = enriched.get(0);
    Assert.assertTrue(out.isTaskDone());
    Assert.assertEquals("bob@example.com", out.getTaskAssignee());
    Assert.assertEquals(1714560000000L, out.getTaskDueTimestamp());
    Assert.assertEquals(18, out.getBodyItemCount());
  }

  @Test
  public void enrichWindowEntriesFromReadBlipsCarriesTaskMetadata() {
    // The dominant production code path is renderWindow over the flat
    // render — without this enrichment, the wave-blip elements emitted by
    // the renderWindow path lose data-task-completed and the strikethrough
    // never shows on reload.
    J2clReadBlip enrichedBlip =
        new J2clReadBlip(
            "b+root",
            "Pin the retry",
            Collections.<J2clAttachmentRenderModel>emptyList(),
            "alice@example.com",
            "alice@example.com",
            1714240000000L,
            "",
            "",
            /* unread= */ false,
            /* hasMention= */ false,
            /* deleted= */ false,
            /* taskDone= */ true,
            /* taskAssignee= */ "bob@example.com",
            /* taskDueTimestamp= */ 1714560000000L,
            /* bodyItemCount= */ 18);

    org.waveprotocol.box.j2cl.read.J2clReadWindowEntry plain =
        org.waveprotocol.box.j2cl.read.J2clReadWindowEntry.loaded(
            "blip:b+root", 0L, 9L, "b+root", "Pin the retry");

    java.util.List<org.waveprotocol.box.j2cl.read.J2clReadWindowEntry> enriched =
        J2clSelectedWaveProjector.enrichWindowEntriesFromReadBlips(
            Arrays.asList(plain), Arrays.asList(enrichedBlip));

    Assert.assertEquals(1, enriched.size());
    org.waveprotocol.box.j2cl.read.J2clReadWindowEntry out = enriched.get(0);
    Assert.assertTrue(out.isTaskDone());
    Assert.assertEquals("bob@example.com", out.getTaskAssignee());
    Assert.assertEquals(1714560000000L, out.getTaskDueTimestamp());
    Assert.assertEquals(18, out.getBodyItemCount());
    // Author + timestamp metadata also propagates so the existing F-2 flat
    // render path metadata works through the window path.
    Assert.assertEquals("alice@example.com", out.getAuthorId());
    Assert.assertEquals(1714240000000L, out.getLastModifiedTimeMillis());
  }

  @Test
  public void enrichWindowEntriesFromReadBlipsLeavesPlaceholdersUnchanged() {
    // Placeholders carry no blip text; enrichment must leave them alone so
    // the renderer's placeholder branch keeps its contract.
    org.waveprotocol.box.j2cl.read.J2clReadWindowEntry placeholder =
        org.waveprotocol.box.j2cl.read.J2clReadWindowEntry.placeholder(
            "blip:b+missing", 0L, 9L, "b+missing");

    java.util.List<org.waveprotocol.box.j2cl.read.J2clReadWindowEntry> enriched =
        J2clSelectedWaveProjector.enrichWindowEntriesFromReadBlips(
            Arrays.asList(placeholder),
            Arrays.asList(new J2clReadBlip("b+missing", "ignored")));

    Assert.assertEquals(1, enriched.size());
    Assert.assertSame(placeholder, enriched.get(0));
  }

  @Test
  public void enrichWindowEntriesFromReadBlipsReturnsInputWhenInputsAreEmpty() {
    Assert.assertSame(
        Collections.<org.waveprotocol.box.j2cl.read.J2clReadWindowEntry>emptyList(),
        J2clSelectedWaveProjector.enrichWindowEntriesFromReadBlips(
            Collections.<org.waveprotocol.box.j2cl.read.J2clReadWindowEntry>emptyList(),
            Arrays.asList(new J2clReadBlip("b+root", "ignored"))));

    java.util.List<org.waveprotocol.box.j2cl.read.J2clReadWindowEntry> entries =
        Arrays.asList(
            org.waveprotocol.box.j2cl.read.J2clReadWindowEntry.loaded(
                "blip:b+root", 0L, 9L, "b+root", "Pin the retry"));
    Assert.assertSame(
        entries,
        J2clSelectedWaveProjector.enrichWindowEntriesFromReadBlips(
            entries, Collections.<J2clReadBlip>emptyList()));
  }
}
