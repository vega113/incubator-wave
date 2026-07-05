package org.waveprotocol.box.j2cl.testsupport;

import java.util.ArrayList;
import java.util.List;
import org.waveprotocol.box.j2cl.attachment.J2clAttachmentUploadClient;

/**
 * #1270: shared compose test double, moved out of J2clComposeSurfaceControllerTest.
 * Public so the split per-flow test classes in the {@code compose} package can use it.
 */
public final class FakeAttachmentTransport implements J2clAttachmentUploadClient.UploadTransport {
  public final List<J2clAttachmentUploadClient.MultipartUploadRequest> requests =
      new ArrayList<J2clAttachmentUploadClient.MultipartUploadRequest>();
  public final List<J2clAttachmentUploadClient.ResponseHandler> handlers =
      new ArrayList<J2clAttachmentUploadClient.ResponseHandler>();

  @Override
  public void post(
      J2clAttachmentUploadClient.MultipartUploadRequest request,
      J2clAttachmentUploadClient.ResponseHandler handler) {
    requests.add(request);
    handlers.add(handler);
  }

  public void complete(int index, J2clAttachmentUploadClient.HttpResponse response) {
    handlers.get(index).onResponse(response);
  }
}
