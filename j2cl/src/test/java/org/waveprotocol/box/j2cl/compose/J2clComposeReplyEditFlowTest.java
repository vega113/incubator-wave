package org.waveprotocol.box.j2cl.compose;

import com.google.j2cl.junit.apt.J2clTestInput;
import java.util.ArrayList;
import java.util.Arrays;
import org.junit.Assert;
import org.junit.Test;
import org.waveprotocol.box.j2cl.search.J2clSidecarWriteSession;
import org.waveprotocol.box.j2cl.testsupport.CapturingDeltaFactory;
import org.waveprotocol.box.j2cl.testsupport.FakeAttachmentTransport;
import org.waveprotocol.box.j2cl.testsupport.FakeFactory;
import org.waveprotocol.box.j2cl.testsupport.FakeGateway;
import org.waveprotocol.box.j2cl.testsupport.FakeView;

/**
 * #1270: split from the monster J2clComposeSurfaceControllerTest. Covers
 * reply availability, target-aware reply, edit-submit and inline-reply anchoring.
 */
@J2clTestInput(J2clComposeReplyEditFlowTest.class)
public class J2clComposeReplyEditFlowTest extends J2clComposeControllerTestSupport {

  @Test
  public void initialModelEnablesCreateAndKeepsReplyUnavailableUntilWriteSession() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(gateway, view, new FakeFactory(), waveId -> { }, waveId -> { });

    controller.start();

    Assert.assertTrue(view.model.isCreateEnabled());
    Assert.assertFalse(view.model.isReplyAvailable());
    Assert.assertEquals("Open a wave before replying.", view.model.getReplyStatusText());
  }

  @Test
  public void writeSessionEnablesReplyAndPublishesTargetLabel() {
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        newController(new FakeGateway(), view, new FakeFactory(), new ArrayList<String>(), new ArrayList<String>());

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));

    Assert.assertTrue(view.model.isReplyAvailable());
    Assert.assertEquals("b+root", view.model.getReplyTargetLabel());
  }

  @Test
  public void targetAwareReplySubmitUsesRequestedBlipSession() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    CapturingDeltaFactory factory = new CapturingDeltaFactory();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            factory,
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(writeSessionWithReplyTargets());
    controller.onReplySubmitted("aa under 555", "b+child");

    Assert.assertNotNull(factory.lastReplySession);
    Assert.assertEquals("b+child", factory.lastReplySession.getReplyTargetBlipId());
    Assert.assertEquals(6, factory.lastReplySession.getReplyManifestInsertPosition());
    Assert.assertEquals(12, factory.lastReplySession.getReplyManifestItemCount());
    Assert.assertEquals("aa under 555", factory.lastDraftText);
  }

  @Test
  public void targetAwareComponentReplySubmitKeepsRequestedBlipSession() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    CapturingDeltaFactory factory = new CapturingDeltaFactory();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            factory,
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(writeSessionWithReplyTargets());
    controller.onReplySubmittedWithComponents(
        Arrays.asList(
            J2clComposeSurfaceController.SubmittedComponent.text("plain "),
            J2clComposeSurfaceController.SubmittedComponent.annotated(
                "bold", "fontWeight", "bold")),
        "b+child");

    Assert.assertNotNull(factory.lastReplySession);
    Assert.assertEquals("b+child", factory.lastReplySession.getReplyTargetBlipId());
    Assert.assertEquals(6, factory.lastReplySession.getReplyManifestInsertPosition());
    Assert.assertEquals(12, factory.lastReplySession.getReplyManifestItemCount());
    Assert.assertEquals("plain bold", factory.lastDraftText);
    Assert.assertEquals(2, factory.lastDocumentComponentCount);
  }

  @Test
  public void editSubmitUsesExistingBlipInsteadOfReplySession() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    CapturingDeltaFactory factory = new CapturingDeltaFactory();
    String originalText = "original root text";
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            factory,
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(writeSessionWithReplyTargets());
    controller.onBlipEditSubmitted(
        "edited root text", "b+root", originalText.length(), originalText, "example.com/w+1");

    Assert.assertNull("Edit submit must not build a reply session.", factory.lastReplySession);
    Assert.assertEquals("b+root", factory.lastEditBlipId);
    Assert.assertEquals(originalText.length(), factory.lastEditBodyItemCount);
    Assert.assertEquals(originalText, factory.lastEditOriginalText);
    Assert.assertEquals("edited root text", factory.lastDraftText);
  }

  @Test
  public void editSubmitRejectsStructuralBodyBeforeBuildingRequest() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    CapturingDeltaFactory factory = new CapturingDeltaFactory();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            factory,
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(writeSessionWithReplyTargets());
    controller.onBlipEditSubmitted("edited root text", "b+root", 19, "original root text", "example.com/w+1");

    Assert.assertNull(factory.lastEditBlipId);
    Assert.assertFalse(view.model.isReplySubmitting());
    Assert.assertEquals(
        J2clComposeSurfaceController.STRUCTURAL_BLIP_EDIT_MESSAGE,
        view.model.getReplyErrorText());
  }

  @Test
  public void editSubmitAllowsEmptyVisibleTextStructuralBody() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    CapturingDeltaFactory factory = new CapturingDeltaFactory();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            factory,
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(writeSessionWithReplyTargets());
    controller.onBlipEditSubmitted(
        "first visible text", "b+root", 2, "", "example.com/w+1");

    Assert.assertEquals("b+root", factory.lastEditBlipId);
    Assert.assertEquals(2, factory.lastEditBodyItemCount);
    Assert.assertEquals("", factory.lastEditOriginalText);
    Assert.assertEquals("first visible text", factory.lastDraftText);
    Assert.assertEquals("", view.model.getReplyErrorText());
  }

  @Test
  public void editSubmitWaitsForInFlightAttachmentUploadBeforeBuildingRequest() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    FakeAttachmentTransport transport = new FakeAttachmentTransport();
    CapturingDeltaFactory factory = new CapturingDeltaFactory();
    String originalText = "original root text";
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            factory,
            testAttachmentControllerFactory(transport),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(writeSessionWithReplyTargets());
    controller.onAttachmentFilesSelected(
        Arrays.asList(
            new J2clComposeSurfaceController.AttachmentFileSelection(new Object(), "late.png")));

    controller.onBlipEditSubmitted("edited root text", "b+root", originalText.length(), originalText, "example.com/w+1");

    Assert.assertNull(factory.lastEditBlipId);
    Assert.assertFalse(view.model.isReplySubmitting());
    Assert.assertEquals(
        J2clComposeSurfaceController.PENDING_ATTACHMENT_REPLY_MESSAGE,
        view.model.getReplyErrorText());
  }

  @Test
  public void emptyEditUsesEditSpecificValidationMessage() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    CapturingDeltaFactory factory = new CapturingDeltaFactory();
    String originalText = "original root text";
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            factory,
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(writeSessionWithReplyTargets());
    controller.onBlipEditSubmitted("", "b+root", originalText.length(), originalText, "example.com/w+1");

    Assert.assertNull(factory.lastEditBlipId);
    Assert.assertFalse(view.model.isReplySubmitting());
    Assert.assertEquals(
        J2clComposeSurfaceController.EMPTY_EDIT_VALIDATION_MESSAGE,
        view.model.getReplyErrorText());
  }

  @Test
  public void editSubmitRejectsStaleWaveIdAfterWaveChange() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    CapturingDeltaFactory factory = new CapturingDeltaFactory();
    String originalText = "original root text";
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            factory,
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(writeSessionWithReplyTargets());
    // Submit with a waveId that no longer matches the selected wave.
    controller.onBlipEditSubmitted(
        "edited root text", "b+root", originalText.length(), originalText, "example.com/w+stale");

    Assert.assertNull(factory.lastEditBlipId);
    Assert.assertFalse(view.model.isReplySubmitting());
    Assert.assertEquals(
        "The wave was switched since the edit was opened. Please try again.",
        view.model.getReplyErrorText());
  }

  @Test
  public void inlineReplyWithoutCapturedCaretFallsBackToParentBodyEndLikeGwt() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    CapturingDeltaFactory factory = new CapturingDeltaFactory();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            factory,
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(writeSessionWithReplyTargets());
    controller.onInlineReplyAnchorRequested(
        "b+child",
        /* anchorItemOffset= */ -1,
        /* parentBodyItemCount= */ TEST_BODY_ITEM_COUNT);
    controller.onReplySubmitted("reply without live selection", "b+child");

    Assert.assertNotNull(factory.lastReplySession);
    Assert.assertEquals("b+child", factory.lastReplySession.getReplyTargetBlipId());
    Assert.assertEquals(
        "GWT ReplyLocationResolver falls back to blip.getContent().size() - 1",
        TEST_BODY_ITEM_COUNT - 1,
        factory.lastInlineAnchorItemOffset);
    Assert.assertEquals(TEST_BODY_ITEM_COUNT, factory.lastInlineAnchorParentBodyItemCount);
  }

  @Test
  public void inlineReplyWithOffsetZeroProducesNoAnchor() {
    // offset 0 is below the minimum accepted by downstream anchor-op builders (>= 1),
    // so it is treated as an invalid anchor and results in no inline anchor (-1).
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    CapturingDeltaFactory factory = new CapturingDeltaFactory();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            factory,
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(writeSessionWithReplyTargets());
    controller.onInlineReplyAnchorRequested(
        "b+child",
        /* anchorItemOffset= */ 0,
        /* parentBodyItemCount= */ TEST_BODY_ITEM_COUNT);
    controller.onReplySubmitted("reply at body start", "b+child");

    Assert.assertNotNull(factory.lastReplySession);
    Assert.assertEquals(-1, factory.lastInlineAnchorItemOffset);
  }

  @Test
  public void continuationReplyIgnoresPendingInlineAnchor() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    CapturingDeltaFactory factory = new CapturingDeltaFactory();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            factory,
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(writeSessionWithReplyTargets());
    controller.onInlineReplyAnchorRequested(
        "b+child",
        /* anchorItemOffset= */ TEST_BODY_ITEM_COUNT - 1,
        /* parentBodyItemCount= */ TEST_BODY_ITEM_COUNT);
    controller.onContinuationSubmitted("same-level reply", "b+child");

    Assert.assertNotNull(factory.lastReplySession);
    Assert.assertTrue(factory.lastReplySession.isContinuationReply());
    Assert.assertEquals("b+child", factory.lastReplySession.getReplyTargetBlipId());
    Assert.assertEquals(-1, factory.lastInlineAnchorItemOffset);
    Assert.assertEquals(0, factory.lastInlineAnchorParentBodyItemCount);
  }
}
