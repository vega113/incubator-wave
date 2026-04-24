package org.waveprotocol.box.j2cl.overlay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.waveprotocol.box.j2cl.transport.SidecarAnnotationRange;
import org.waveprotocol.box.j2cl.transport.SidecarReactionEntry;

public final class J2clInteractionBlipModel {
  private final String blipId;
  private final String text;
  private final List<SidecarAnnotationRange> annotationRanges;
  private final List<SidecarReactionEntry> reactionEntries;

  public J2clInteractionBlipModel(
      String blipId,
      String text,
      List<SidecarAnnotationRange> annotationRanges,
      List<SidecarReactionEntry> reactionEntries) {
    this.blipId = blipId == null ? "" : blipId;
    this.text = text == null ? "" : text;
    this.annotationRanges =
        annotationRanges == null
            ? Collections.<SidecarAnnotationRange>emptyList()
            : Collections.unmodifiableList(new ArrayList<SidecarAnnotationRange>(annotationRanges));
    this.reactionEntries =
        reactionEntries == null
            ? Collections.<SidecarReactionEntry>emptyList()
            : Collections.unmodifiableList(new ArrayList<SidecarReactionEntry>(reactionEntries));
  }

  public String getBlipId() {
    return blipId;
  }

  public String getText() {
    return text;
  }

  public List<SidecarAnnotationRange> getAnnotationRanges() {
    return annotationRanges;
  }

  public List<SidecarReactionEntry> getReactionEntries() {
    return reactionEntries;
  }
}
