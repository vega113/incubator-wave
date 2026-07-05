package org.waveprotocol.box.j2cl.testsupport;

import org.waveprotocol.box.j2cl.compose.J2clComposeSurfaceController;
import org.waveprotocol.box.j2cl.search.J2clSearchPanelController;
import org.waveprotocol.box.j2cl.transport.SidecarSessionBootstrap;
import org.waveprotocol.box.j2cl.transport.SidecarSubmitRequest;
import org.waveprotocol.box.j2cl.transport.SidecarSubmitResponse;

/**
 * #1270: shared compose test double, moved out of the ~5 kLOC
 * J2clComposeSurfaceControllerTest so the focused per-flow split classes can
 * reuse it. Public (class + fields) because the split test classes live in the
 * {@code compose} package while these doubles live here; a stateless double
 * needs no {@code @J2clTestInput}.
 */
public final class FakeGateway implements J2clComposeSurfaceController.Gateway {
  public int fetchBootstrapCalls;
  public int submitCalls;
  public boolean autoResolveBootstrap = true;
  public String bootstrapError;
  public String submitError;
  public SidecarSubmitResponse submitResponse = new SidecarSubmitResponse(1, "", 45L);
  public SidecarSubmitRequest lastSubmitRequest;
  public J2clSearchPanelController.SuccessCallback<SidecarSessionBootstrap> pendingBootstrapSuccess;
  public J2clSearchPanelController.ErrorCallback pendingBootstrapError;

  @Override
  public void fetchRootSessionBootstrap(
      J2clSearchPanelController.SuccessCallback<SidecarSessionBootstrap> onSuccess,
      J2clSearchPanelController.ErrorCallback onError) {
    fetchBootstrapCalls++;
    if (bootstrapError != null) {
      onError.accept(bootstrapError);
      return;
    }
    if (autoResolveBootstrap) {
      onSuccess.accept(new SidecarSessionBootstrap("user@example.com", "socket.example.test"));
      return;
    }
    pendingBootstrapSuccess = onSuccess;
    pendingBootstrapError = onError;
  }

  @Override
  public void submit(
      SidecarSessionBootstrap bootstrap,
      SidecarSubmitRequest request,
      J2clSearchPanelController.SuccessCallback<SidecarSubmitResponse> onSuccess,
      J2clSearchPanelController.ErrorCallback onError) {
    submitCalls++;
    lastSubmitRequest = request;
    if (submitError != null) {
      onError.accept(submitError);
      return;
    }
    onSuccess.accept(submitResponse);
  }

  public void resolveBootstrap() {
    if (pendingBootstrapSuccess == null) {
      throw new IllegalStateException("No pending bootstrap to resolve");
    }
    J2clSearchPanelController.SuccessCallback<SidecarSessionBootstrap> success =
        pendingBootstrapSuccess;
    pendingBootstrapSuccess = null;
    pendingBootstrapError = null;
    success.accept(new SidecarSessionBootstrap("user@example.com", "socket.example.test"));
  }
}
