package org.waveprotocol.box.j2cl.compose;

import com.google.j2cl.junit.apt.J2clTestInput;
import java.util.Arrays;
import org.junit.Assert;
import org.junit.Test;
import org.waveprotocol.box.j2cl.attachment.J2clAttachmentUploadClient;
import org.waveprotocol.box.j2cl.search.J2clSidecarWriteSession;
import org.waveprotocol.box.j2cl.testsupport.FakeAttachmentTransport;
import org.waveprotocol.box.j2cl.testsupport.FakeGateway;
import org.waveprotocol.box.j2cl.testsupport.FakeView;
import org.waveprotocol.box.j2cl.toolbar.J2clDailyToolbarAction;
import org.waveprotocol.box.j2cl.transport.SidecarSubmitResponse;

/**
 * #1270: split from the monster J2clComposeSurfaceControllerTest. Covers
 * attachment cancel, id-generator continuity and late-completion handling.
 */
@J2clTestInput(J2clComposeAttachmentCancelLifecycleTest.class)
public class J2clComposeAttachmentCancelLifecycleTest extends J2clComposeControllerTestSupport {

  @Test
  public void replySubmitWaitsForInFlightAttachmentUploadBeforeBuildingRequest() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    FakeAttachmentTransport transport = new FakeAttachmentTransport();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(transport),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onAttachmentFilesSelected(
        Arrays.asList(
            new J2clComposeSurfaceController.AttachmentFileSelection(new Object(), "late.png")));

    controller.onReplySubmitted("Draft with late file");

    Assert.assertEquals(0, gateway.submitCalls);
    Assert.assertFalse(view.model.isReplySubmitting());
    Assert.assertEquals(
        J2clComposeSurfaceController.PENDING_ATTACHMENT_REPLY_MESSAGE,
        view.model.getReplyErrorText());

    transport.complete(0, new J2clAttachmentUploadClient.HttpResponse(200, "OK", null));
    Assert.assertEquals("", view.model.getReplyErrorText());
    Assert.assertFalse(view.model.isReplySubmitting());
    controller.onReplySubmitted("Draft with late file");

    Assert.assertEquals(1, gateway.submitCalls);
    assertContains(
        gateway.lastSubmitRequest.getDeltaJson(),
        "{\"1\":\"attachment\",\"2\":\"example.com/seedA\"}",
        "\"2\":\"late.png\"");
  }

  @Test
  public void submitWaitThenCancelFallsBackToEmptyReplyGuard() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    FakeAttachmentTransport transport = new FakeAttachmentTransport();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(transport),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onAttachmentFilesSelected(
        Arrays.asList(
            new J2clComposeSurfaceController.AttachmentFileSelection(new Object(), "cancel.png")));
    controller.onReplySubmitted("");
    Assert.assertEquals(
        J2clComposeSurfaceController.PENDING_ATTACHMENT_REPLY_MESSAGE,
        view.model.getReplyErrorText());

    Assert.assertTrue(controller.onToolbarAction(J2clDailyToolbarAction.ATTACHMENT_CANCEL));
    Assert.assertEquals("", view.model.getReplyErrorText());
    Assert.assertFalse(view.model.isReplySubmitting());
    controller.onReplySubmitted("");

    Assert.assertEquals(0, gateway.submitCalls);
    Assert.assertEquals(
        J2clComposeSurfaceController.EMPTY_REPLY_VALIDATION_MESSAGE, view.model.getReplyErrorText());
  }

  @Test
  public void blankAttachmentFileNameFallsBackToGenericCaption() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    FakeAttachmentTransport transport = new FakeAttachmentTransport();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(transport),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onAttachmentFilesSelected(
        Arrays.asList(new J2clComposeSurfaceController.AttachmentFileSelection(new Object(), "   ")));
    transport.complete(0, new J2clAttachmentUploadClient.HttpResponse(200, "OK", null));
    Assert.assertEquals("Attached attachment.", view.model.getCommandStatusText());
    controller.onReplySubmitted("");

    assertContains(gateway.lastSubmitRequest.getDeltaJson(), "\"2\":\"attachment\"");
  }

  @Test
  public void insertedAttachmentClearsEmptyReplyValidationError() {
    FakeView view = new FakeView();
    FakeAttachmentTransport transport = new FakeAttachmentTransport();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            new FakeGateway(),
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(transport),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onReplySubmitted("");
    Assert.assertEquals(
        J2clComposeSurfaceController.EMPTY_REPLY_VALIDATION_MESSAGE, view.model.getReplyErrorText());

    controller.onAttachmentFilesSelected(
        Arrays.asList(new J2clComposeSurfaceController.AttachmentFileSelection(new Object(), "ok.png")));
    Assert.assertEquals(
        J2clComposeSurfaceController.EMPTY_REPLY_VALIDATION_MESSAGE, view.model.getReplyErrorText());
    transport.complete(0, new J2clAttachmentUploadClient.HttpResponse(200, "OK", null));

    Assert.assertEquals("", view.model.getReplyErrorText());
  }

  @Test
  public void insertedAndCompletedAttachmentDoesNotClearUnrelatedReplyFailure() {
    FakeGateway gateway = new FakeGateway();
    gateway.submitResponse = new SidecarSubmitResponse(1, "conflict", 45L);
    FakeView view = new FakeView();
    FakeAttachmentTransport transport = new FakeAttachmentTransport();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(transport),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onReplySubmitted("will fail");
    Assert.assertEquals("conflict", view.model.getReplyErrorText());

    controller.onAttachmentFilesSelected(
        Arrays.asList(new J2clComposeSurfaceController.AttachmentFileSelection(new Object(), "new.png")));
    Assert.assertEquals("conflict", view.model.getReplyErrorText());
    transport.complete(0, new J2clAttachmentUploadClient.HttpResponse(200, "OK", null));

    Assert.assertEquals("conflict", view.model.getReplyErrorText());
  }

  @Test
  public void cancelledAttachmentStateChangeDoesNotClearUnrelatedReplyFailure() {
    FakeGateway gateway = new FakeGateway();
    gateway.submitResponse = new SidecarSubmitResponse(1, "conflict", 45L);
    FakeView view = new FakeView();
    FakeAttachmentTransport transport = new FakeAttachmentTransport();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(transport),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onReplySubmitted("will fail");
    Assert.assertEquals("conflict", view.model.getReplyErrorText());

    controller.onAttachmentFilesSelected(
        Arrays.asList(new J2clComposeSurfaceController.AttachmentFileSelection(new Object(), "new.png")));
    Assert.assertEquals("conflict", view.model.getReplyErrorText());
    Assert.assertTrue(controller.onToolbarAction(J2clDailyToolbarAction.ATTACHMENT_CANCEL));

    Assert.assertEquals("conflict", view.model.getReplyErrorText());
  }

  @Test
  public void waveChangeResetsAttachmentDisplaySizeToMedium() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    FakeAttachmentTransport transport = new FakeAttachmentTransport();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(transport),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onToolbarAction(J2clDailyToolbarAction.ATTACHMENT_SIZE_LARGE);
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+2", "chan-2", 50L, "BCDE", "b+root"));
    controller.onAttachmentFilesSelected(
        Arrays.asList(
            new J2clComposeSurfaceController.AttachmentFileSelection(new Object(), "medium.png")));
    transport.complete(0, new J2clAttachmentUploadClient.HttpResponse(200, "OK", null));
    controller.onReplySubmitted("");

    assertContains(gateway.lastSubmitRequest.getDeltaJson(), "{\"1\":\"display-size\",\"2\":\"medium\"}");
  }

  @Test
  public void pastedImageFailureShowsAttachmentErrorAndDoesNotSubmitEmptyReply() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    FakeAttachmentTransport transport = new FakeAttachmentTransport();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(transport),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onPastedImage(new Object());
    transport.complete(0, new J2clAttachmentUploadClient.HttpResponse(202, "accepted", null));

    Assert.assertEquals("attachment-error-state", view.model.getActiveCommandId());
    Assert.assertTrue(view.model.getCommandErrorText().contains("pasted-image.png"));
    Assert.assertTrue(view.model.getCommandErrorText().contains("unexpected HTTP 202"));

    controller.onReplySubmitted("");

    Assert.assertEquals(0, gateway.submitCalls);
    Assert.assertEquals(
        J2clComposeSurfaceController.EMPTY_REPLY_VALIDATION_MESSAGE, view.model.getReplyErrorText());
  }

  @Test
  public void latestAttachmentFailureIsSurfacedWhenFailuresAccumulate() {
    FakeView view = new FakeView();
    FakeAttachmentTransport transport = new FakeAttachmentTransport();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            new FakeGateway(),
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(transport),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onAttachmentFilesSelected(
        Arrays.asList(new J2clComposeSurfaceController.AttachmentFileSelection(new Object(), "first.png")));
    transport.complete(0, new J2clAttachmentUploadClient.HttpResponse(500, "first", null));
    controller.onAttachmentFilesSelected(
        Arrays.asList(new J2clComposeSurfaceController.AttachmentFileSelection(new Object(), "second.png")));
    transport.complete(1, new J2clAttachmentUploadClient.HttpResponse(500, "second", null));

    Assert.assertTrue(view.model.getCommandErrorText().contains("second.png"));
  }

  @Test
  public void failedReplyPreservesUploadedAttachmentForRetry() {
    FakeGateway gateway = new FakeGateway();
    gateway.submitResponse = new SidecarSubmitResponse(1, "conflict", 45L);
    FakeView view = new FakeView();
    FakeAttachmentTransport transport = new FakeAttachmentTransport();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(transport),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onAttachmentFilesSelected(
        Arrays.asList(
            new J2clComposeSurfaceController.AttachmentFileSelection(new Object(), "retry.png")));
    transport.complete(0, new J2clAttachmentUploadClient.HttpResponse(200, "OK", null));

    controller.onReplySubmitted("");

    Assert.assertEquals("conflict", view.model.getReplyErrorText());
    Assert.assertEquals("", view.model.getCommandStatusText());
    Assert.assertEquals(1, gateway.submitCalls);

    gateway.submitResponse = new SidecarSubmitResponse(1, "", 46L);
    controller.onReplySubmitted("");

    Assert.assertEquals(2, gateway.submitCalls);
    assertContains(
        gateway.lastSubmitRequest.getDeltaJson(),
        "{\"1\":\"attachment\",\"2\":\"example.com/seedA\"}",
        "\"2\":\"retry.png\"");
  }

  @Test
  public void cancelAttachmentToolbarActionClearsUploadQueueState() {
    FakeView view = new FakeView();
    FakeAttachmentTransport transport = new FakeAttachmentTransport();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            new FakeGateway(),
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(transport),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onAttachmentFilesSelected(
        Arrays.asList(
            new J2clComposeSurfaceController.AttachmentFileSelection(new Object(), "cancel.png")));
    Assert.assertEquals("Uploading cancel.png (0%).", view.model.getCommandStatusText());

    Assert.assertTrue(controller.onToolbarAction(J2clDailyToolbarAction.ATTACHMENT_CANCEL));

    Assert.assertEquals("attachment-cancel", view.model.getActiveCommandId());
    Assert.assertEquals("Attachment upload queue cancelled.", view.model.getCommandStatusText());
    Assert.assertEquals("", view.model.getCommandErrorText());
  }

  @Test
  public void cancelAttachmentToolbarActionPreservesInsertedAndContinuesIdGenerator() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    FakeAttachmentTransport transport = new FakeAttachmentTransport();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(transport),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onAttachmentFilesSelected(
        Arrays.asList(new J2clComposeSurfaceController.AttachmentFileSelection(new Object(), "kept.png")));
    transport.complete(0, new J2clAttachmentUploadClient.HttpResponse(200, "OK", null));

    Assert.assertTrue(controller.onToolbarAction(J2clDailyToolbarAction.ATTACHMENT_CANCEL));
    Assert.assertEquals("Pending uploads cancelled. Attached files kept.", view.model.getCommandStatusText());
    controller.onAttachmentFilesSelected(
        Arrays.asList(new J2clComposeSurfaceController.AttachmentFileSelection(new Object(), "second.png")));
    transport.complete(1, new J2clAttachmentUploadClient.HttpResponse(200, "OK", null));
    controller.onReplySubmitted("");

    Assert.assertEquals("/attachment/example.com/seedB", transport.requests.get(1).getUrl());
    assertContains(
        gateway.lastSubmitRequest.getDeltaJson(),
        "{\"1\":\"attachment\",\"2\":\"example.com/seedA\"}",
        "\"2\":\"kept.png\"",
        "{\"1\":\"attachment\",\"2\":\"example.com/seedB\"}",
        "\"2\":\"second.png\"");
  }

  @Test
  public void cancelAttachmentToolbarActionPreservesIdGeneratorAfterPendingUploadCancel() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    FakeAttachmentTransport transport = new FakeAttachmentTransport();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(transport),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onAttachmentFilesSelected(
        Arrays.asList(new J2clComposeSurfaceController.AttachmentFileSelection(new Object(), "cancelled.png")));
    Assert.assertEquals("/attachment/example.com/seedA", transport.requests.get(0).getUrl());

    Assert.assertTrue(controller.onToolbarAction(J2clDailyToolbarAction.ATTACHMENT_CANCEL));
    transport.complete(0, new J2clAttachmentUploadClient.HttpResponse(200, "OK", null));
    controller.onAttachmentFilesSelected(
        Arrays.asList(new J2clComposeSurfaceController.AttachmentFileSelection(new Object(), "second.png")));
    transport.complete(1, new J2clAttachmentUploadClient.HttpResponse(200, "OK", null));
    controller.onReplySubmitted("");

    Assert.assertEquals("/attachment/example.com/seedB", transport.requests.get(1).getUrl());
    Assert.assertFalse(gateway.lastSubmitRequest.getDeltaJson().contains("example.com/seedA"));
    assertContains(
        gateway.lastSubmitRequest.getDeltaJson(),
        "{\"1\":\"attachment\",\"2\":\"example.com/seedB\"}",
        "\"2\":\"second.png\"");
  }

  @Test
  public void lateAttachmentCompletionAfterCancelIsIgnored() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    FakeAttachmentTransport transport = new FakeAttachmentTransport();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(transport),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onAttachmentFilesSelected(
        Arrays.asList(
            new J2clComposeSurfaceController.AttachmentFileSelection(new Object(), "late.png")));
    controller.onToolbarAction(J2clDailyToolbarAction.ATTACHMENT_CANCEL);

    transport.complete(0, new J2clAttachmentUploadClient.HttpResponse(200, "OK", null));
    controller.onReplySubmitted("");

    Assert.assertEquals(0, gateway.submitCalls);
    Assert.assertEquals(
        J2clComposeSurfaceController.EMPTY_REPLY_VALIDATION_MESSAGE, view.model.getReplyErrorText());
  }

  @Test
  public void signOutMidAttachmentUploadIgnoresLateCompletion() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    FakeAttachmentTransport transport = new FakeAttachmentTransport();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(transport),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onAttachmentFilesSelected(
        Arrays.asList(
            new J2clComposeSurfaceController.AttachmentFileSelection(new Object(), "signout.png")));
    controller.onSignedOut();
    transport.complete(0, new J2clAttachmentUploadClient.HttpResponse(200, "OK", null));
    controller.onReplySubmitted("");

    Assert.assertEquals(0, gateway.submitCalls);
    Assert.assertFalse(view.model.isReplyAvailable());
  }

  @Test
  public void waveChangeMidAttachmentUploadIgnoresLateCompletionForNewWave() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    FakeAttachmentTransport transport = new FakeAttachmentTransport();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(transport),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onAttachmentFilesSelected(
        Arrays.asList(
            new J2clComposeSurfaceController.AttachmentFileSelection(new Object(), "old.png")));
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+2", "chan-2", 50L, "BCDE", "b+root"));
    transport.complete(0, new J2clAttachmentUploadClient.HttpResponse(200, "OK", null));
    controller.onReplySubmitted("");

    Assert.assertEquals(0, gateway.submitCalls);
    Assert.assertEquals(
        J2clComposeSurfaceController.EMPTY_REPLY_VALIDATION_MESSAGE, view.model.getReplyErrorText());
  }
}
