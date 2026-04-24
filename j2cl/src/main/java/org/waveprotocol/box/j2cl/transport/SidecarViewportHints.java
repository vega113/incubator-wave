package org.waveprotocol.box.j2cl.transport;

/** Optional viewport hints for selected-wave ProtocolOpenRequest frames. */
public final class SidecarViewportHints {
  private static final SidecarViewportHints NONE = new SidecarViewportHints(null, null, null);
  // Explicit zero means "viewport mode requested; server should apply its configured default limit".
  private static final SidecarViewportHints DEFAULT_LIMIT =
      new SidecarViewportHints(null, null, Integer.valueOf(0));

  private final String startBlipId;
  private final String direction;
  private final Integer limit;

  public SidecarViewportHints(String startBlipId, String direction, Integer limit) {
    this.startBlipId = normalize(startBlipId);
    this.direction = normalize(direction);
    this.limit = limit;
  }

  public static SidecarViewportHints none() {
    return NONE;
  }

  public static SidecarViewportHints defaultLimit() {
    return DEFAULT_LIMIT;
  }

  public boolean hasHints() {
    return startBlipId != null || direction != null || limit != null;
  }

  public String getStartBlipId() {
    return startBlipId;
  }

  public String getDirection() {
    return direction;
  }

  public Integer getLimit() {
    return limit;
  }

  private static String normalize(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
