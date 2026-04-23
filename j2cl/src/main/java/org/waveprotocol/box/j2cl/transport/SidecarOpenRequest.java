package org.waveprotocol.box.j2cl.transport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SidecarOpenRequest {
  private final String participantId;
  private final String waveId;
  private final List<String> waveletIdPrefixes;
  private final SidecarViewportHints viewportHints;

  /** No-hint convenience constructor retained for legacy selected-wave and test callers. */
  public SidecarOpenRequest(String participantId, String waveId, List<String> waveletIdPrefixes) {
    this(participantId, waveId, waveletIdPrefixes, null);
  }

  public SidecarOpenRequest(
      String participantId,
      String waveId,
      List<String> waveletIdPrefixes,
      SidecarViewportHints viewportHints) {
    this.participantId = participantId;
    this.waveId = waveId;
    this.waveletIdPrefixes =
        waveletIdPrefixes == null
            ? Collections.<String>emptyList()
            : Collections.unmodifiableList(new ArrayList<>(waveletIdPrefixes));
    this.viewportHints = viewportHints == null ? SidecarViewportHints.none() : viewportHints;
  }

  public String getParticipantId() {
    return participantId;
  }

  public String getWaveId() {
    return waveId;
  }

  public List<String> getWaveletIdPrefixes() {
    return waveletIdPrefixes;
  }

  public SidecarViewportHints getViewportHints() {
    return viewportHints;
  }
}
