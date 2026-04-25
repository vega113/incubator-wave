package org.waveprotocol.box.j2cl.read;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.waveprotocol.box.j2cl.attachment.J2clAttachmentRenderModel;

public final class J2clReadBlip {
  private final String blipId;
  private final String text;
  private final List<J2clAttachmentRenderModel> attachments;

  public J2clReadBlip(String blipId, String text) {
    this(blipId, text, Collections.<J2clAttachmentRenderModel>emptyList());
  }

  public J2clReadBlip(
      String blipId, String text, List<J2clAttachmentRenderModel> attachments) {
    this.blipId = blipId == null ? "" : blipId;
    this.text = text == null ? "" : text;
    this.attachments =
        attachments == null
            ? Collections.<J2clAttachmentRenderModel>emptyList()
            : Collections.unmodifiableList(new ArrayList<J2clAttachmentRenderModel>(attachments));
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
}
