package org.waveprotocol.box.j2cl.overlay;

public final class J2clOverlayFocusTarget {
  private final String targetId;

  public J2clOverlayFocusTarget(String targetId) {
    this.targetId = targetId == null ? "" : targetId;
  }

  public String getTargetId() {
    return targetId;
  }
}
