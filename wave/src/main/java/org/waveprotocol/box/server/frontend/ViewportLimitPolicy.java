package org.waveprotocol.box.server.frontend;

import java.util.Locale;

/** Shared limit policy for viewport-scoped fragment windows. */
public final class ViewportLimitPolicy {
  public static final String DIRECTION_FORWARD = "forward";
  public static final String DIRECTION_BACKWARD = "backward";

  private static volatile Limits limits = new Limits(5, 50);

  private ViewportLimitPolicy() {
  }

  public static void setLimits(int configuredDefaultLimit, int configuredMaxLimit) {
    int normalizedDefault = configuredDefaultLimit <= 0 ? 1 : configuredDefaultLimit;
    int normalizedMax =
        configuredMaxLimit < normalizedDefault ? normalizedDefault : configuredMaxLimit;
    limits = new Limits(normalizedDefault, normalizedMax);
  }

  public static int getDefaultLimit() {
    return limits.defaultLimit;
  }

  public static int getMaxLimit() {
    return limits.maxLimit;
  }

  public static int resolveLimit(String rawLimit) {
    Limits snapshot = limits;
    if (rawLimit == null) {
      return snapshot.defaultLimit;
    }
    try {
      return resolveLimit(Integer.parseInt(rawLimit.trim()), snapshot);
    } catch (NumberFormatException e) {
      return snapshot.defaultLimit;
    }
  }

  public static int resolveLimit(int requestedLimit) {
    return resolveLimit(requestedLimit, limits);
  }

  private static int resolveLimit(int requestedLimit, Limits snapshot) {
    if (requestedLimit <= 0) {
      return snapshot.defaultLimit;
    }
    return Math.min(requestedLimit, snapshot.maxLimit);
  }

  public static String normalizeDirection(String rawDirection) {
    if (rawDirection == null) {
      return DIRECTION_FORWARD;
    }
    String normalized = rawDirection.trim().toLowerCase(Locale.ROOT);
    return DIRECTION_BACKWARD.equals(normalized) ? DIRECTION_BACKWARD : DIRECTION_FORWARD;
  }

  private static final class Limits {
    private final int defaultLimit;
    private final int maxLimit;

    private Limits(int defaultLimit, int maxLimit) {
      this.defaultLimit = defaultLimit;
      this.maxLimit = maxLimit;
    }
  }
}
