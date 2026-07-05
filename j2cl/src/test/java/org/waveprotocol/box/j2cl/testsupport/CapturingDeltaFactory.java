package org.waveprotocol.box.j2cl.testsupport;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.waveprotocol.box.j2cl.compose.J2clComposeSurfaceController;
import org.waveprotocol.box.j2cl.search.J2clSidecarWriteSession;
import org.waveprotocol.box.j2cl.transport.SidecarSubmitRequest;

/**
 * #1270: shared compose test double, moved out of J2clComposeSurfaceControllerTest.
 * Public so the split per-flow test classes in the {@code compose} package can use it.
 */
public final class CapturingDeltaFactory implements J2clComposeSurfaceController.DeltaFactory {
  public List<String> lastAdditionalParticipants = Collections.emptyList();
  public String lastDraftText = null;
  public int lastDocumentComponentCount = -1;
  public J2clSidecarWriteSession lastReplySession = null;
  public int lastInlineAnchorItemOffset = Integer.MIN_VALUE;
  public int lastInlineAnchorParentBodyItemCount = Integer.MIN_VALUE;
  public String lastEditBlipId = null;
  public int lastEditBodyItemCount = Integer.MIN_VALUE;
  public String lastEditOriginalText = null;

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
