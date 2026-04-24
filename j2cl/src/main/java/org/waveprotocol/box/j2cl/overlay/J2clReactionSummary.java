package org.waveprotocol.box.j2cl.overlay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class J2clReactionSummary {
  private final String emoji;
  private final List<String> participantAddresses;
  private final boolean activeForCurrentUser;
  private final String inspectLabel;

  public J2clReactionSummary(
      String emoji,
      List<String> participantAddresses,
      boolean activeForCurrentUser,
      String inspectLabel) {
    this.emoji = emoji == null ? "" : emoji;
    this.participantAddresses =
        participantAddresses == null
            ? Collections.<String>emptyList()
            : Collections.unmodifiableList(new ArrayList<String>(participantAddresses));
    this.activeForCurrentUser = activeForCurrentUser;
    this.inspectLabel =
        inspectLabel == null || inspectLabel.isEmpty()
            ? buildInspectLabel(this.emoji, this.participantAddresses.size())
            : inspectLabel;
  }

  public String getEmoji() {
    return emoji;
  }

  public List<String> getParticipantAddresses() {
    return participantAddresses;
  }

  public boolean isActiveForCurrentUser() {
    return activeForCurrentUser;
  }

  public int getCount() {
    return participantAddresses.size();
  }

  public String getInspectLabel() {
    return inspectLabel;
  }

  private static String buildInspectLabel(String emoji, int count) {
    return count + (count == 1 ? " reaction" : " reactions") + " for " + emoji + ".";
  }
}
