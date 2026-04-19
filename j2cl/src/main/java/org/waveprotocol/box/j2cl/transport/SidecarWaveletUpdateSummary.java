package org.waveprotocol.box.j2cl.transport;

public final class SidecarWaveletUpdateSummary {
  private final int sequenceNumber;
  private final String waveletName;
  private final int appliedDeltaCount;
  private final boolean marker;
  private final String channelId;

  public SidecarWaveletUpdateSummary(
      int sequenceNumber, String waveletName, int appliedDeltaCount, boolean marker, String channelId) {
    this.sequenceNumber = sequenceNumber;
    this.waveletName = waveletName;
    this.appliedDeltaCount = appliedDeltaCount;
    this.marker = marker;
    this.channelId = channelId;
  }

  public int getSequenceNumber() {
    return sequenceNumber;
  }

  public String getWaveletName() {
    return waveletName;
  }

  public int getAppliedDeltaCount() {
    return appliedDeltaCount;
  }

  public boolean hasMarker() {
    return marker;
  }

  public String getChannelId() {
    return channelId;
  }
}
