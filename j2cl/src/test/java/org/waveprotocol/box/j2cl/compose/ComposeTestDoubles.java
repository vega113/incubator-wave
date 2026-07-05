package org.waveprotocol.box.j2cl.compose;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.waveprotocol.box.j2cl.attachment.J2clAttachmentUploadClient;
import org.waveprotocol.box.j2cl.search.J2clPlainTextDeltaFactory;
import org.waveprotocol.box.j2cl.search.J2clSearchPanelController;
import org.waveprotocol.box.j2cl.search.J2clSidecarWriteSession;
import org.waveprotocol.box.j2cl.transport.SidecarSessionBootstrap;
import org.waveprotocol.box.j2cl.transport.SidecarSubmitRequest;
import org.waveprotocol.box.j2cl.transport.SidecarSubmitResponse;

// #1270: the shared compose test doubles, moved out of the 5 kLOC
// J2clComposeSurfaceControllerTest so they can be reused by focused per-flow
// split test classes. Kept in this package (not testsupport) so the existing
// package-private field access from the test classes is preserved verbatim —
// these are stateless doubles that need no @J2clTestInput.

final class FakeGateway implements J2clComposeSurfaceController.Gateway {
  int fetchBootstrapCalls;
  int submitCalls;
  boolean autoResolveBootstrap = true;
  String bootstrapError;
  String submitError;
  SidecarSubmitResponse submitResponse = new SidecarSubmitResponse(1, "", 45L);
  SidecarSubmitRequest lastSubmitRequest;
  J2clSearchPanelController.SuccessCallback<SidecarSessionBootstrap> pendingBootstrapSuccess;
  J2clSearchPanelController.ErrorCallback pendingBootstrapError;

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

  void resolveBootstrap() {
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

final class FakeView implements J2clComposeSurfaceController.View {
  J2clComposeSurfaceModel model;
  J2clComposeSurfaceController.Listener listener;
  int openAttachmentPickerCalls;
  int focusReplyComposerCalls;
  int focusCreateSurfaceCalls;
  int focusCreateComposerCalls;
  int closeActiveReplyComposerCalls;

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

final class FakeAttachmentTransport implements J2clAttachmentUploadClient.UploadTransport {
  final List<J2clAttachmentUploadClient.MultipartUploadRequest> requests =
      new ArrayList<J2clAttachmentUploadClient.MultipartUploadRequest>();
  final List<J2clAttachmentUploadClient.ResponseHandler> handlers =
      new ArrayList<J2clAttachmentUploadClient.ResponseHandler>();

  @Override
  public void post(
      J2clAttachmentUploadClient.MultipartUploadRequest request,
      J2clAttachmentUploadClient.ResponseHandler handler) {
    requests.add(request);
    handlers.add(handler);
  }

  void complete(int index, J2clAttachmentUploadClient.HttpResponse response) {
    handlers.get(index).onResponse(response);
  }
}

final class CapturingDeltaFactory implements J2clComposeSurfaceController.DeltaFactory {
  List<String> lastAdditionalParticipants = Collections.emptyList();
  String lastDraftText = null;
  int lastDocumentComponentCount = -1;
  J2clSidecarWriteSession lastReplySession = null;
  int lastInlineAnchorItemOffset = Integer.MIN_VALUE;
  int lastInlineAnchorParentBodyItemCount = Integer.MIN_VALUE;
  String lastEditBlipId = null;
  int lastEditBodyItemCount = Integer.MIN_VALUE;
  String lastEditOriginalText = null;

  @Override
  public J2clComposeSurfaceController.CreateWaveRequest createWaveRequest(
      String address, String draftText, org.waveprotocol.box.j2cl.richtext.J2clComposerDocument document) {
    return createWaveRequest(address, draftText, document, Collections.emptyList());
  }

  @Override
  public J2clComposeSurfaceController.CreateWaveRequest createWaveRequest(
      String address,
      String draftText,
      org.waveprotocol.box.j2cl.richtext.J2clComposerDocument document,
      List<String> additionalParticipants) {
    lastDraftText = draftText;
    lastAdditionalParticipants = new ArrayList<String>(additionalParticipants);
    lastDocumentComponentCount = componentCount(document);
    return new J2clComposeSurfaceController.CreateWaveRequest(
        "example.com/w+direct",
        new SidecarSubmitRequest(
            "example.com/w+direct/~/conv+root", "{\"create\":true}", null));
  }

  @Override
  public SidecarSubmitRequest createReplyRequest(
      String address,
      J2clSidecarWriteSession session,
      String draftText,
      org.waveprotocol.box.j2cl.richtext.J2clComposerDocument document) {
    lastReplySession = session;
    lastDraftText = draftText;
    lastDocumentComponentCount = componentCount(document);
    return new SidecarSubmitRequest(
        session.getSelectedWaveId() + "/~/conv+root", "{\"reply\":true}", session.getChannelId());
  }

  @Override
  public SidecarSubmitRequest createReplyRequest(
      String address,
      J2clSidecarWriteSession session,
      String draftText,
      org.waveprotocol.box.j2cl.richtext.J2clComposerDocument document,
      int inlineAnchorItemOffset,
      int parentBodyItemCount) {
    lastInlineAnchorItemOffset = inlineAnchorItemOffset;
    lastInlineAnchorParentBodyItemCount = parentBodyItemCount;
    return createReplyRequest(address, session, draftText, document);
  }

  @Override
  public SidecarSubmitRequest createBlipEditRequest(
      String address,
      J2clSidecarWriteSession session,
      String blipId,
      String draftText,
      org.waveprotocol.box.j2cl.richtext.J2clComposerDocument document,
      int bodyItemCount,
      String originalText) {
    lastEditBlipId = blipId;
    lastDraftText = draftText;
    lastDocumentComponentCount = componentCount(document);
    lastEditBodyItemCount = bodyItemCount;
    lastEditOriginalText = originalText;
    return new SidecarSubmitRequest(
        session.getSelectedWaveId() + "/~/conv+root", "{\"edit\":true}", session.getChannelId());
  }

  private static int componentCount(org.waveprotocol.box.j2cl.richtext.J2clComposerDocument document) {
    try {
      Field field = document.getClass().getDeclaredField("components");
      field.setAccessible(true);
      return ((List<?>) field.get(document)).size();
    } catch (ReflectiveOperationException e) {
      throw new AssertionError(e);
    }
  }
}

final class FakeFactory extends J2clPlainTextDeltaFactory {
  String lastReplyText;

  FakeFactory() {
    super("seed");
  }

  @Override
  public CreateWaveRequest createWaveRequest(String address, String text) {
    return new CreateWaveRequest(
        "example.com/w+new",
        new SidecarSubmitRequest("example.com/w+new/~/conv+root", "{\"create\":true}", null));
  }

  @Override
  public SidecarSubmitRequest createReplyRequest(
      String address, J2clSidecarWriteSession session, String text) {
    lastReplyText = text;
    return new SidecarSubmitRequest(
        session.getSelectedWaveId() + "/~/conv+root", "{\"reply\":true}", session.getChannelId());
  }
}
