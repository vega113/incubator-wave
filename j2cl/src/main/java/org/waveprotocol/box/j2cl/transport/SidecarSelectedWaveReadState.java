package org.waveprotocol.box.j2cl.transport;

/**
 * Per-user unread/read state for a single selected wave. Populated from the
 * server's {@code /read-state} endpoint (issue #931).
 */
public final class SidecarSelectedWaveReadState {
  private final String waveId;
  private final int unreadCount;
  private final boolean read;

  public SidecarSelectedWaveReadState(String waveId, int unreadCount, boolean read) {
    this.waveId = waveId;
    this.unreadCount = unreadCount;
    this.read = read;
  }

  public String getWaveId() {
    return waveId;
  }

  public int getUnreadCount() {
    return unreadCount;
  }

  public boolean isRead() {
    return read;
  }
}
