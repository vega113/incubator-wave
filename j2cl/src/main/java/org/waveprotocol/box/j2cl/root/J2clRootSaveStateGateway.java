package org.waveprotocol.box.j2cl.root;

import org.waveprotocol.box.j2cl.compose.J2clComposeSurfaceController;
import org.waveprotocol.box.j2cl.search.J2clSearchPanelController;
import org.waveprotocol.box.j2cl.transport.SidecarSessionBootstrap;
import org.waveprotocol.box.j2cl.transport.SidecarSubmitRequest;
import org.waveprotocol.box.j2cl.transport.SidecarSubmitResponse;

/**
 * #1233: compose-gateway decorator that reports real save state to the root
 * live-surface status chips.
 *
 * <p>Every write path in the J2CL root shell (create, reply, blip edit, and the
 * wave-header actions) funnels through {@link J2clComposeSurfaceController.Gateway#submit}.
 * Wrapping that single choke point lets the {@code savestatus} chip flip to
 * {@code saving} the instant an op is in flight and back to {@code saved} once
 * every pending op has been acknowledged or has failed, without threading a
 * listener through each submit site.
 */
public final class J2clRootSaveStateGateway implements J2clComposeSurfaceController.Gateway {

  /** Sink for save-state transitions (typically {@code liveSurfaceController::onSaveState}). */
  @FunctionalInterface
  public interface SaveStateSink {
    void onSaveState(String saveState);
  }

  private final J2clComposeSurfaceController.Gateway delegate;
  private final SaveStateSink saveStateSink;
  private int pendingSubmits;

  public J2clRootSaveStateGateway(
      J2clComposeSurfaceController.Gateway delegate, SaveStateSink saveStateSink) {
    if (delegate == null) {
      throw new IllegalArgumentException("delegate is required");
    }
    if (saveStateSink == null) {
      throw new IllegalArgumentException("saveStateSink is required");
    }
    this.delegate = delegate;
    this.saveStateSink = saveStateSink;
  }

  @Override
  public void fetchRootSessionBootstrap(
      J2clSearchPanelController.SuccessCallback<SidecarSessionBootstrap> onSuccess,
      J2clSearchPanelController.ErrorCallback onError) {
    delegate.fetchRootSessionBootstrap(onSuccess, onError);
  }

  @Override
  public void submit(
      SidecarSessionBootstrap bootstrap,
      SidecarSubmitRequest request,
      J2clSearchPanelController.SuccessCallback<SidecarSubmitResponse> onSuccess,
      J2clSearchPanelController.ErrorCallback onError) {
    beginSubmit();
    // Guard against a delegate that invokes both callbacks (or one twice): the
    // ref-count must move exactly once per submit or the chip could latch.
    final boolean[] settled = new boolean[] {false};
    try {
      delegate.submit(
          bootstrap,
          request,
          response -> {
            endSubmit(settled);
            onSuccess.accept(response);
          },
          error -> {
            endSubmit(settled);
            onError.accept(error);
          });
    } catch (RuntimeException | Error e) {
      // A delegate that throws synchronously (e.g. WebSocket allocation /
      // input validation) must still release the ref-count, or the savestatus
      // chip latches on "saving" for the rest of the session.
      endSubmit(settled);
      throw e;
    }
  }

  private void beginSubmit() {
    pendingSubmits++;
    if (pendingSubmits == 1) {
      saveStateSink.onSaveState(J2clRootLiveSurfaceModel.SAVE_SAVING);
    }
  }

  private void endSubmit(boolean[] settled) {
    if (settled[0]) {
      return;
    }
    settled[0] = true;
    if (pendingSubmits > 0) {
      pendingSubmits--;
    }
    if (pendingSubmits == 0) {
      saveStateSink.onSaveState(J2clRootLiveSurfaceModel.SAVE_SAVED);
    }
  }
}
