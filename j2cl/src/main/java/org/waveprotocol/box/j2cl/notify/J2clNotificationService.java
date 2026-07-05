package org.waveprotocol.box.j2cl.notify;

/**
 * #1271: single sink for the transient error / status / success messages that
 * were previously scattered across per-surface model fields (create/reply/
 * command error text, fetch failures, etc.).
 *
 * <p>Implementations own the presentation (toast stacking + auto-dismiss) and
 * the ARIA live-region announcement so every failure path gets consistent
 * timing, level semantics, and screen-reader treatment instead of overwriting a
 * per-surface string.
 */
public interface J2clNotificationService {

  /** Default auto-dismiss window for error toasts. */
  int DEFAULT_ERROR_TIMEOUT_MS = 8000;

  /** Default auto-dismiss window for calmer success/status toasts. */
  int DEFAULT_TRANSIENT_TIMEOUT_MS = 4000;

  /** Severity of a notification; drives styling and assertive-vs-polite a11y. */
  enum Level {
    INFO,
    SUCCESS,
    ERROR
  }

  /**
   * Surface an error. Announced assertively for AT. {@code timeoutMs <= 0}
   * keeps it until explicitly cleared; otherwise it auto-dismisses.
   */
  void showError(String message, int timeoutMs);

  /** Surface a calmer success message (polite AT, auto-dismiss). */
  void showSuccess(String message);

  /** Surface a transient message at the given level (polite AT, auto-dismiss). */
  void showTransient(String message, Level level);

  /** Remove any visible notifications and clear the live regions. */
  void clear();

  /** Convenience: error with the default auto-dismiss window. */
  default void showError(String message) {
    showError(message, DEFAULT_ERROR_TIMEOUT_MS);
  }
}
