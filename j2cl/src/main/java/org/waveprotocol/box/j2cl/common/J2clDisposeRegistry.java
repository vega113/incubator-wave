package org.waveprotocol.box.j2cl.common;

import elemental2.dom.DomGlobal;
import elemental2.dom.EventListener;
import elemental2.dom.EventTarget;
import java.util.ArrayList;
import java.util.List;

/**
 * #1268: collects the teardown actions a {@link Disposable} surface accumulates
 * (event listeners, timers, arbitrary un-binders) so {@link #destroy()} can
 * release them all at once — the single place that makes leak-free teardown
 * mechanical instead of hand-tracking every handler ref.
 *
 * <p>Registering after {@link #destroy()} runs the disposer immediately (so a
 * late binding cannot leak), and {@code destroy()} is idempotent.
 */
public final class J2clDisposeRegistry implements Disposable {

  private final List<Runnable> disposers = new ArrayList<Runnable>();
  private boolean destroyed;

  /**
   * Adds {@code listener} to {@code target} for {@code type} and tracks its
   * removal. No-op when target/listener is null.
   */
  public void addListener(EventTarget target, String type, EventListener listener) {
    if (target == null || listener == null) {
      return;
    }
    target.addEventListener(type, listener);
    track(() -> target.removeEventListener(type, listener));
  }

  /** Tracks a {@code setTimeout}/{@code setInterval} handle for cancellation. */
  public void trackTimeout(double handle) {
    track(() -> DomGlobal.clearTimeout(handle));
  }

  /** Tracks an arbitrary teardown action (e.g. a transport {@code Subscription::close}). */
  public void track(Runnable disposer) {
    if (disposer == null) {
      return;
    }
    if (destroyed) {
      // Already torn down — run the disposer now rather than retain it.
      runQuietly(disposer);
      return;
    }
    disposers.add(disposer);
  }

  /** True once {@link #destroy()} has run. */
  public boolean isDestroyed() {
    return destroyed;
  }

  @Override
  public void destroy() {
    if (destroyed) {
      return;
    }
    destroyed = true;
    // Tear down in reverse registration order (mirrors nested resource scoping).
    for (int i = disposers.size() - 1; i >= 0; i--) {
      runQuietly(disposers.get(i));
    }
    disposers.clear();
  }

  private static void runQuietly(Runnable disposer) {
    try {
      disposer.run();
    } catch (RuntimeException | Error ignored) {
      // One failing disposer must not abort the rest of teardown.
    }
  }
}
