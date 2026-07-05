package org.waveprotocol.box.j2cl.search;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import org.waveprotocol.box.j2cl.attachment.J2clAttachmentMetadata;
import org.waveprotocol.box.j2cl.transport.SidecarAnnotationRange;
import org.waveprotocol.box.j2cl.transport.SidecarReactionEntry;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveDocument;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveFragment;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveFragmentRange;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveFragments;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveUpdate;

/**
 * #1270: shared scaffolding for the split J2clSelectedWaveProjector test classes
 * (formerly one ~4.1k-line file). Holds the update/fragment/document builders and
 * shared constants; package-private so the per-scenario subclasses can reuse them.
 */
abstract class J2clSelectedWaveProjectorTestSupport {

  static final String WAVE_ID = "example.com/w+1";

  static final String WAVELET_NAME = "example.com!w+1/example.com!conv+root";

  static final String WAVELET_NAME_2 = "example.com!w+2/example.com!conv+root";

  static final String CHANNEL_ID = "chan-1";

  static final String INDEX_SEGMENT = "index";

  static final String MANIFEST_SEGMENT = "manifest";

  static final String ATTACHMENT_RAW_SNAPSHOT =
      "Intro <image attachment=\"example.com/att+hero\" display-size=\"medium\">"
          + "<caption>Hero diagram</caption></image> outro";

  // -- Helpers ----------------------------------------------------------------

  static J2clSearchDigestItem digest(String title, String snippet, int unreadCount) {
    return new J2clSearchDigestItem(
        WAVE_ID, title, snippet, "user@example.com", unreadCount, 2, 1L, false);
  }

  static SidecarSelectedWaveUpdate sampleUpdate() {
    return new SidecarSelectedWaveUpdate(
        1,
        WAVELET_NAME,
        true,
        CHANNEL_ID,
        -1L,
        null,
        Arrays.asList("user@example.com"),
        new ArrayList<SidecarSelectedWaveDocument>(),
        null);
  }

  static SidecarSelectedWaveUpdate fragmentUpdate(
      String firstBlipId, String firstText, String secondBlipId, String secondText) {
    return new SidecarSelectedWaveUpdate(
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
                new SidecarSelectedWaveFragmentRange("blip:" + firstBlipId, 0L, 9L),
                new SidecarSelectedWaveFragmentRange("blip:" + secondBlipId, 0L, 9L)),
            Arrays.asList(
                new SidecarSelectedWaveFragment("blip:" + firstBlipId, firstText, 0, 0),
                new SidecarSelectedWaveFragment("blip:" + secondBlipId, secondText, 0, 0))));
  }

  static SidecarSelectedWaveDocument lockDocument(String lockState) {
    return new SidecarSelectedWaveDocument(
        "m/lock",
        "user@example.com",
        44L,
        45L,
        "",
        1,
        Collections.<SidecarAnnotationRange>emptyList(),
        Collections.<SidecarReactionEntry>emptyList(),
        lockState);
  }

  static SidecarSelectedWaveUpdate rootFragmentUpdate(
      int sequence, long version, String historyHash, String rawSnapshot) {
    // Keep the helper window non-zero so tests catch accidental range downgrades.
    long fromVersion = Math.max(0L, version - 10L);
    return new SidecarSelectedWaveUpdate(
        sequence,
        WAVELET_NAME,
        true,
        CHANNEL_ID,
        version,
        historyHash,
        Arrays.asList("user@example.com"),
        Collections.<SidecarSelectedWaveDocument>emptyList(),
        new SidecarSelectedWaveFragments(
            version,
            fromVersion,
            version,
            Arrays.asList(
                new SidecarSelectedWaveFragmentRange("blip:b+root", fromVersion, version)),
            Arrays.asList(
                new SidecarSelectedWaveFragment("blip:b+root", rawSnapshot, 0, 0))));
  }

  static SidecarSelectedWaveFragments metadataOnlyFragments(
      long snapshotVersion, long fromVersion, long toVersion) {
    return new SidecarSelectedWaveFragments(
        snapshotVersion,
        fromVersion,
        toVersion,
        Arrays.asList(
            new SidecarSelectedWaveFragmentRange(INDEX_SEGMENT, fromVersion, toVersion),
            new SidecarSelectedWaveFragmentRange(MANIFEST_SEGMENT, fromVersion, toVersion)),
        Arrays.asList(
            new SidecarSelectedWaveFragment(INDEX_SEGMENT, "index", 0, 0),
            new SidecarSelectedWaveFragment(MANIFEST_SEGMENT, "metadata", 0, 0)));
  }

  static J2clSelectedWaveViewportState viewportWithAttachment() {
    return J2clSelectedWaveViewportState.fromFragments(attachmentFragments(9L, 0L, 9L));
  }

  static SidecarSelectedWaveFragments attachmentFragments(
      long snapshotVersion, long fromVersion, long toVersion) {
    return new SidecarSelectedWaveFragments(
        snapshotVersion,
        fromVersion,
        toVersion,
        Arrays.asList(
            new SidecarSelectedWaveFragmentRange("blip:b+root", fromVersion, toVersion)),
        Arrays.asList(
            new SidecarSelectedWaveFragment("blip:b+root", ATTACHMENT_RAW_SNAPSHOT, 0, 0)));
  }

  static J2clSelectedWaveViewportState viewportWithTwoAttachments() {
    return J2clSelectedWaveViewportState.fromFragments(
        new SidecarSelectedWaveFragments(
            9L,
            0L,
            9L,
            Arrays.asList(new SidecarSelectedWaveFragmentRange("blip:b+root", 0L, 9L)),
            Arrays.asList(
                new SidecarSelectedWaveFragment(
                    "blip:b+root",
                    "Intro <image attachment=\"example.com/att+hero\" display-size=\"medium\">"
                        + "<caption>Hero diagram</caption></image>"
                        + " and <image attachment=\"example.com/att+diagram\" "
                        + "display-size=\"small\"><caption>Diagram</caption></image>",
                    0,
                    0))));
  }

  static J2clAttachmentMetadata attachmentMetadata(
      String attachmentId,
      String fileName,
      String mimeType,
      String attachmentUrl,
      String thumbnailUrl,
      boolean malware) {
    return new J2clAttachmentMetadata(
        attachmentId,
        "example.com/w+1/~/conv+root",
        fileName,
        mimeType,
        4096L,
        "user@example.com",
        attachmentUrl,
        thumbnailUrl,
        new J2clAttachmentMetadata.ImageMetadata(1200, 800),
        new J2clAttachmentMetadata.ImageMetadata(320, 200),
        malware);
  }

  static J2clSelectedWaveViewportState.Entry entryBySegment(
      J2clSelectedWaveViewportState viewport, String segment) {
    for (J2clSelectedWaveViewportState.Entry entry : viewport.getEntries()) {
      if (segment.equals(entry.getSegment())) {
        return entry;
      }
    }
    throw new AssertionError("Missing segment: " + segment);
  }

  static void assertNoEntryBySegment(
      J2clSelectedWaveViewportState viewport, String segment) {
    for (J2clSelectedWaveViewportState.Entry entry : viewport.getEntries()) {
      if (segment.equals(entry.getSegment())) {
        throw new AssertionError("Unexpected segment: " + segment);
      }
    }
  }

  static SidecarSelectedWaveUpdate updateWithVersionAndHash(
      long resultingVersion, String resultingVersionHistoryHash) {
    return new SidecarSelectedWaveUpdate(
        1,
        WAVELET_NAME,
        true,
        CHANNEL_ID,
        resultingVersion,
        resultingVersionHistoryHash,
        Arrays.asList("user@example.com"),
        Arrays.asList(
            new SidecarSelectedWaveDocument(
                "b+root", "user@example.com", 33L, 44L, "content")),
        new SidecarSelectedWaveFragments(
            resultingVersion >= 0 ? resultingVersion : 0L,
            0L,
            resultingVersion >= 0 ? resultingVersion : 0L,
            Arrays.asList(
                new SidecarSelectedWaveFragmentRange("blip:b+root", 0L, 0L)),
            Arrays.asList(
                new SidecarSelectedWaveFragment("blip:b+root", "content", 0, 0))));
  }

  static J2clSelectedWaveModel modelWithWriteSession(long baseVersion, String historyHash) {
    J2clSidecarWriteSession writeSession =
        new J2clSidecarWriteSession(WAVE_ID, CHANNEL_ID, baseVersion, historyHash, "b+root");
    return new J2clSelectedWaveModel(
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
        Collections.<String>emptyList(),
        Collections.<String>emptyList(),
        writeSession,
        J2clSelectedWaveModel.UNKNOWN_UNREAD_COUNT,
        false,
        false,
        false);
  }
}
