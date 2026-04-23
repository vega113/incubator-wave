package org.waveprotocol.box.server.frontend;

/** Shared limit policy for viewport-scoped fragment windows. */
public final class ViewportLimitPolicy {
  private static volatile int defaultLimit = 5;
  private static volatile int maxLimit = 50;

  private ViewportLimitPolicy() {
  }

  public static void setLimits(int configuredDefaultLimit, int configuredMaxLimit) {
    int normalizedDefault = configuredDefaultLimit <= 0 ? 1 : configuredDefaultLimit;
    int normalizedMax =
        configuredMaxLimit < normalizedDefault ? normalizedDefault : configuredMaxLimit;
    maxLimit = normalizedMax;
    defaultLimit = normalizedDefault;
  }

  public static int getDefaultLimit() {
    return defaultLimit;
  }

  public static int getMaxLimit() {
    return maxLimit;
  }

  public static int resolveLimit(String rawLimit) {
    if (rawLimit == null) {
      return defaultLimit;
    }
    try {
      return resolveLimit(Integer.parseInt(rawLimit));
    } catch (NumberFormatException e) {
      return defaultLimit;
    }
  }

  public static int resolveLimit(int requestedLimit) {
    if (requestedLimit <= 0) {
      return defaultLimit;
    }
    return Math.min(requestedLimit, maxLimit);
  }
}
