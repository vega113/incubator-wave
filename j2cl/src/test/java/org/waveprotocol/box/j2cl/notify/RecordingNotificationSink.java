package org.waveprotocol.box.j2cl.notify;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * #1271: test double for {@link J2clNotificationService} that records every
 * notification so call-site migrations can assert the exact user-visible text
 * without a DOM. Mirrors {@code RecordingTelemetrySink}.
 */
public final class RecordingNotificationSink implements J2clNotificationService {

  /** A single recorded notification. */
  public static final class Notification {
    public final J2clNotificationService.Level level;
    public final String message;
    public final int timeoutMs;

    Notification(J2clNotificationService.Level level, String message, int timeoutMs) {
      this.level = level;
      this.message = message;
      this.timeoutMs = timeoutMs;
    }
  }

  private final List<Notification> notifications = new ArrayList<Notification>();
  private int clearCount;

  @Override
  public void showError(String message, int timeoutMs) {
    notifications.add(new Notification(Level.ERROR, message, timeoutMs));
  }

  @Override
  public void showSuccess(String message) {
    notifications.add(new Notification(Level.SUCCESS, message, DEFAULT_TRANSIENT_TIMEOUT_MS));
  }

  @Override
  public void showTransient(String message, Level level) {
    notifications.add(
        new Notification(level == null ? Level.INFO : level, message, DEFAULT_TRANSIENT_TIMEOUT_MS));
  }

  @Override
  public void clear() {
    clearCount++;
  }

  public List<Notification> notifications() {
    return Collections.unmodifiableList(notifications);
  }

  public Notification last() {
    if (notifications.isEmpty()) {
      throw new IllegalStateException("No notifications recorded");
    }
    return notifications.get(notifications.size() - 1);
  }

  public List<String> messages() {
    List<String> out = new ArrayList<String>();
    for (Notification n : notifications) {
      out.add(n.message);
    }
    return out;
  }

  public int clearCount() {
    return clearCount;
  }
}
