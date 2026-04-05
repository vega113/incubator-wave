package org.waveprotocol.box.common;

/** Decides whether a prolonged reconnect should force a full page reload. */
public final class ReconnectReloadPolicy {

  private static final long PROLONGED_DISCONNECT_THRESHOLD_MS = 5000L;

  private ReconnectReloadPolicy() {}

  public static boolean shouldReloadAfterProlongedDisconnect(long disconnectMs) {
    return disconnectMs > PROLONGED_DISCONNECT_THRESHOLD_MS;
  }
}
