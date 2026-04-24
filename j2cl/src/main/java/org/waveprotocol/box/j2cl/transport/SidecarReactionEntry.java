package org.waveprotocol.box.j2cl.transport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SidecarReactionEntry {
  private final String emoji;
  private final List<String> addresses;

  public SidecarReactionEntry(String emoji, List<String> addresses) {
    this.emoji = emoji == null ? "" : emoji;
    this.addresses =
        addresses == null
            ? Collections.<String>emptyList()
            : Collections.unmodifiableList(new ArrayList<String>(addresses));
  }

  public String getEmoji() {
    return emoji;
  }

  public List<String> getAddresses() {
    return addresses;
  }
}
