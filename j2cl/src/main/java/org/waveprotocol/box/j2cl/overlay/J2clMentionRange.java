package org.waveprotocol.box.j2cl.overlay;

public final class J2clMentionRange {
  private final int startOffset;
  private final int endOffset;
  private final String userAddress;
  private final String displayText;

  public J2clMentionRange(
      int startOffset, int endOffset, String userAddress, String displayText) {
    this.startOffset = Math.max(0, startOffset);
    this.endOffset = Math.max(this.startOffset, endOffset);
    this.userAddress = userAddress == null ? "" : userAddress;
    this.displayText = displayText == null ? "" : displayText;
  }

  public int getStartOffset() {
    return startOffset;
  }

  public int getEndOffset() {
    return endOffset;
  }

  public String getUserAddress() {
    return userAddress;
  }

  public String getDisplayText() {
    return displayText;
  }
}
