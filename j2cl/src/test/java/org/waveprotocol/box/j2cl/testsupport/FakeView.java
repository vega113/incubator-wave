package org.waveprotocol.box.j2cl.testsupport;

import org.waveprotocol.box.j2cl.compose.J2clComposeSurfaceController;
import org.waveprotocol.box.j2cl.compose.J2clComposeSurfaceModel;

/**
 * #1270: shared compose test double, moved out of J2clComposeSurfaceControllerTest.
 * Public so the split per-flow test classes in the {@code compose} package can use it.
 */
public final class FakeView implements J2clComposeSurfaceController.View {
  public J2clComposeSurfaceModel model;
  public J2clComposeSurfaceController.Listener listener;
  public int openAttachmentPickerCalls;
  public int focusReplyComposerCalls;
  public int focusCreateSurfaceCalls;
  public int focusCreateComposerCalls;
  public int closeActiveReplyComposerCalls;

  @Override
  public void bind(J2clComposeSurfaceController.Listener listener) {
    this.listener = listener;
  }

  @Override
  public void render(J2clComposeSurfaceModel model) {
    this.model = model;
  }

  @Override
  public void openAttachmentPicker() {
    openAttachmentPickerCalls++;
  }

  @Override
  public void focusReplyComposer() {
    focusReplyComposerCalls++;
  }

  @Override
  public void closeActiveReplyComposer() {
    closeActiveReplyComposerCalls++;
  }

  @Override
  public void focusCreateSurface() {
    focusCreateSurfaceCalls++;
  }

  @Override
  public void focusCreateComposer() {
    focusCreateComposerCalls++;
  }
}
