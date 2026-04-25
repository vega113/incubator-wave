package org.waveprotocol.box.j2cl.read;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.waveprotocol.box.j2cl.attachment.J2clAttachmentRenderModel;

public final class J2clReadWindowEntry {
  private final String segment;
  private final long fromVersion;
  private final long toVersion;
  private final String blipId;
  private final String text;
  private final List<J2clAttachmentRenderModel> attachments;
  private final boolean loaded;

  private J2clReadWindowEntry(
      String segment,
      long fromVersion,
      long toVersion,
      String blipId,
      String text,
      List<J2clAttachmentRenderModel> attachments,
      boolean loaded) {
    this.segment = segment == null ? "" : segment;
    this.fromVersion = fromVersion;
    this.toVersion = toVersion;
    this.blipId = blipId == null ? "" : blipId;
    this.text = text == null ? "" : text;
    this.attachments =
        attachments == null
            ? Collections.<J2clAttachmentRenderModel>emptyList()
            : Collections.unmodifiableList(new ArrayList<J2clAttachmentRenderModel>(attachments));
    this.loaded = loaded;
  }

  public static J2clReadWindowEntry loaded(
      String segment, long fromVersion, long toVersion, String blipId, String text) {
    return loaded(
        segment,
        fromVersion,
        toVersion,
        blipId,
        text,
        Collections.<J2clAttachmentRenderModel>emptyList());
  }

  public static J2clReadWindowEntry loaded(
      String segment,
      long fromVersion,
      long toVersion,
      String blipId,
      String text,
      List<J2clAttachmentRenderModel> attachments) {
    return new J2clReadWindowEntry(
        segment, fromVersion, toVersion, blipId, text, attachments, true);
  }

  public static J2clReadWindowEntry placeholder(
      String segment, long fromVersion, long toVersion, String blipId) {
    return new J2clReadWindowEntry(
        segment,
        fromVersion,
        toVersion,
        blipId,
        "",
        Collections.<J2clAttachmentRenderModel>emptyList(),
        false);
  }

  public String getSegment() {
    return segment;
  }

  public long getFromVersion() {
    return fromVersion;
  }

  public long getToVersion() {
    return toVersion;
  }

  public String getBlipId() {
    return blipId;
  }

  public String getText() {
    return text;
  }

  public List<J2clAttachmentRenderModel> getAttachments() {
    return attachments;
  }

  public boolean isLoaded() {
    return loaded;
  }
}
