package org.waveprotocol.box.j2cl.transport;

public final class SidecarOpenRequest {
  private final String participantId;
  private final String waveId;

  public SidecarOpenRequest(String participantId, String waveId) {
    this.participantId = participantId;
    this.waveId = waveId;
  }

  public String getParticipantId() {
    return participantId;
  }

  public String getWaveId() {
    return waveId;
  }
}
