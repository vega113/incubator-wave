package org.waveprotocol.box.j2cl.transport;

public final class SidecarAnnotationRange {
  private final String key;
  private final String value;
  private final int startOffset;
  private final int endOffset;

  public SidecarAnnotationRange(String key, String value, int startOffset, int endOffset) {
    this.key = key == null ? "" : key;
    this.value = value == null ? "" : value;
    this.startOffset = Math.max(0, startOffset);
    this.endOffset = Math.max(this.startOffset, endOffset);
  }

  public String getKey() {
    return key;
  }

  public String getValue() {
    return value;
  }

  public int getStartOffset() {
    return startOffset;
  }

  public int getEndOffset() {
    return endOffset;
  }

  public boolean isMention() {
    return key.startsWith("mention/");
  }

  public boolean isTask() {
    return key.startsWith("task/");
  }
}
