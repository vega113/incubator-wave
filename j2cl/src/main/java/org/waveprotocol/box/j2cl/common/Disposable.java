package org.waveprotocol.box.j2cl.common;

/**
 * #1268: explicit teardown contract for J2CL controllers and views that bind
 * DOM/custom-event listeners, timers, or transport subscriptions.
 *
 * <p>Without this, listeners and in-flight callbacks accumulate across wave
 * switches / sidecar open-close / shell re-render, producing leaks, duplicate
 * handlers, and stale callbacks mutating detached DOM. A surface that owns any
 * such resource implements {@link #destroy()} to release them.
 */
public interface Disposable {

  /**
   * Release all listeners, timers, and subscriptions this instance owns.
   *
   * <p>Must be idempotent and safe to call on an instance that never registered
   * anything or that has already been destroyed.
   */
  void destroy();
}
