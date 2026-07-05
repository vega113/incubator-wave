package org.waveprotocol.box.j2cl.compose;

import com.google.j2cl.junit.apt.J2clTestInput;
import java.util.ArrayList;
import java.util.Arrays;
import org.junit.Assert;
import org.junit.Test;
import org.waveprotocol.box.j2cl.search.J2clSidecarWriteSession;
import org.waveprotocol.box.j2cl.testsupport.FakeAttachmentTransport;
import org.waveprotocol.box.j2cl.testsupport.FakeGateway;
import org.waveprotocol.box.j2cl.testsupport.FakeView;
import org.waveprotocol.box.j2cl.toolbar.J2clDailyToolbarAction;

/**
 * #1270: split from the monster J2clComposeSurfaceControllerTest. Covers
 * attachment guards: signed-out/no-wave rejection, blocked-while-submitting, paste hints.
 */
@J2clTestInput(J2clComposeAttachmentGuardsTest.class)
public class J2clComposeAttachmentGuardsTest extends J2clComposeControllerTestSupport {

  @Test
  public void signedOutAttachmentSelectionShowsSignInAndSkipsUpload() {
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
    controller.onSignedOut();
    controller.onAttachmentFilesSelected(
        Arrays.asList(new J2clComposeSurfaceController.AttachmentFileSelection(new Object(), "late.png")));

    Assert.assertTrue(transport.requests.isEmpty());
    Assert.assertEquals("", view.model.getActiveCommandId());
    Assert.assertEquals("Sign in before attaching files.", view.model.getCommandErrorText());
  }

  @Test
  public void signedOutPastedImageShowsSignInAndSkipsUpload() {
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
    controller.onSignedOut();
    controller.onPastedImage(new Object());

    Assert.assertTrue(transport.requests.isEmpty());
    Assert.assertEquals("", view.model.getActiveCommandId());
    Assert.assertEquals("Sign in before pasting an image.", view.model.getCommandErrorText());
  }

  @Test
  public void attachmentToolbarCommandDoesNotPretendToInsertWithoutSelection() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    Assert.assertTrue(controller.onToolbarAction(J2clDailyToolbarAction.ATTACHMENT_INSERT));
    Assert.assertEquals("", view.model.getActiveCommandId());
    Assert.assertEquals("", view.model.getCommandStatusText());
    controller.onReplySubmitted("Attach later");

    Assert.assertFalse(gateway.lastSubmitRequest.getDeltaJson().contains("\"1\":\"image\""));
    Assert.assertEquals("", view.model.getActiveCommandId());
    Assert.assertEquals("", view.model.getCommandStatusText());
  }

  @Test
  public void pastedImageRequiresSelectedWave() {
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
    controller.onPastedImage(new Object());

    Assert.assertTrue(transport.requests.isEmpty());
    Assert.assertEquals("", view.model.getActiveCommandId());
    Assert.assertEquals("Open a wave before pasting an image.", view.model.getCommandErrorText());
  }

  @Test
  public void selectedAttachmentRequiresSelectedWaveWithoutLeavingActiveCommand() {
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
    controller.onAttachmentFilesSelected(
        Arrays.asList(new J2clComposeSurfaceController.AttachmentFileSelection(new Object(), "now.png")));

    Assert.assertTrue(transport.requests.isEmpty());
    Assert.assertEquals("", view.model.getActiveCommandId());
    Assert.assertEquals("Open a wave before attaching files.", view.model.getCommandErrorText());
  }

  @Test
  public void attachmentInsertRequiresSelectedWaveWithoutLeavingActiveCommand() {
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            new FakeGateway(),
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(new FakeAttachmentTransport()),
            waveId -> { },
            waveId -> { });

    controller.start();
    Assert.assertTrue(controller.onToolbarAction(J2clDailyToolbarAction.ATTACHMENT_INSERT));

    Assert.assertEquals(0, view.openAttachmentPickerCalls);
    Assert.assertEquals("", view.model.getActiveCommandId());
    Assert.assertEquals("Open a wave before attaching files.", view.model.getCommandErrorText());
  }

  @Test
  public void nonSurfacedAttachmentToolbarIdsFallBackToToolbarUnavailable() {
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            new FakeGateway(),
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(new FakeAttachmentTransport()),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));

    Assert.assertFalse(controller.onToolbarAction(J2clDailyToolbarAction.ATTACHMENT_CAPTION));
    Assert.assertFalse(controller.onToolbarAction(J2clDailyToolbarAction.ATTACHMENT_OPEN));
    Assert.assertFalse(controller.onToolbarAction(J2clDailyToolbarAction.ATTACHMENT_DOWNLOAD));
  }

  @Test
  public void nonSurfacedAttachmentToolbarIdsDoNotClearExistingCommandError() {
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            new FakeGateway(),
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(new FakeAttachmentTransport()),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onPastedImage(null);
    String existingError = view.model.getCommandErrorText();

    Assert.assertFalse(controller.onToolbarAction(J2clDailyToolbarAction.ATTACHMENT_OPEN));

    Assert.assertEquals(existingError, view.model.getCommandErrorText());
  }

  @Test
  public void nullPastedImageShowsAttachmentErrorWithoutStartingUpload() {
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
    controller.onPastedImage(null);

    Assert.assertTrue(transport.requests.isEmpty());
    Assert.assertEquals("attachment-error-state", view.model.getActiveCommandId());
    Assert.assertEquals("Pasted image payload is required.", view.model.getCommandErrorText());
  }

  @Test
  public void attachmentInsertToolbarOpensPickerAndRestoresReplyFocus() {
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            new FakeGateway(),
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(new FakeAttachmentTransport()),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    Assert.assertTrue(controller.onToolbarAction(J2clDailyToolbarAction.ATTACHMENT_INSERT));

    Assert.assertEquals(1, view.openAttachmentPickerCalls);
    Assert.assertEquals(1, view.focusReplyComposerCalls);
    Assert.assertEquals("", view.model.getActiveCommandId());
    Assert.assertEquals("", view.model.getCommandStatusText());
  }

  @Test
  public void emptyAttachmentSelectionStaysQuietAfterPickerCancel() {
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            new FakeGateway(),
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(new FakeAttachmentTransport()),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onToolbarAction(J2clDailyToolbarAction.ATTACHMENT_INSERT);
    controller.onAttachmentFilesSelected(
        new ArrayList<J2clComposeSurfaceController.AttachmentFileSelection>());

    Assert.assertEquals("", view.model.getActiveCommandId());
    Assert.assertEquals("", view.model.getCommandStatusText());
    Assert.assertEquals("", view.model.getCommandErrorText());
  }

  @Test
  public void emptyAttachmentSelectionClearsExistingCommandError() {
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            new FakeGateway(),
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(new FakeAttachmentTransport()),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onPastedImage(null);
    Assert.assertFalse(view.model.getCommandErrorText().isEmpty());

    controller.onAttachmentFilesSelected(
        new ArrayList<J2clComposeSurfaceController.AttachmentFileSelection>());

    Assert.assertEquals("", view.model.getActiveCommandId());
    Assert.assertEquals("", view.model.getCommandStatusText());
    Assert.assertEquals("", view.model.getCommandErrorText());
  }

  @Test
  public void attachmentToolbarInsertIsBlockedWhileReplyIsSubmitting() {
    FakeGateway gateway = new FakeGateway();
    gateway.autoResolveBootstrap = false;
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(new FakeAttachmentTransport()),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onReplySubmitted("Draft");
    Assert.assertTrue(controller.onToolbarAction(J2clDailyToolbarAction.ATTACHMENT_INSERT));

    Assert.assertEquals(0, view.openAttachmentPickerCalls);
    Assert.assertEquals("", view.model.getActiveCommandId());
    Assert.assertEquals(
        "Wait for the current reply to finish before attaching files.",
        view.model.getCommandErrorText());
  }

  @Test
  public void attachmentSizeCanChangeWhileReplyIsSubmitting() {
    FakeGateway gateway = new FakeGateway();
    gateway.autoResolveBootstrap = false;
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(new FakeAttachmentTransport()),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onReplySubmitted("Draft");
    Assert.assertTrue(controller.onToolbarAction(J2clDailyToolbarAction.ATTACHMENT_SIZE_LARGE));

    Assert.assertEquals("attachment-size-large", view.model.getActiveCommandId());
    Assert.assertEquals("Large attachment size selected.", view.model.getCommandStatusText());
    Assert.assertEquals("", view.model.getCommandErrorText());
  }

  @Test
  public void selectedAttachmentIsBlockedWhileReplyIsSubmitting() {
    FakeGateway gateway = new FakeGateway();
    gateway.autoResolveBootstrap = false;
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
    controller.onReplySubmitted("Draft");
    controller.onAttachmentFilesSelected(
        Arrays.asList(new J2clComposeSurfaceController.AttachmentFileSelection(new Object(), "busy.png")));

    Assert.assertTrue(transport.requests.isEmpty());
    Assert.assertEquals("", view.model.getActiveCommandId());
    Assert.assertEquals(
        "Wait for the current reply to finish before attaching files.",
        view.model.getCommandErrorText());
  }

  @Test
  public void pastedImageIsBlockedWhileReplyIsSubmitting() {
    FakeGateway gateway = new FakeGateway();
    gateway.autoResolveBootstrap = false;
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
    controller.onReplySubmitted("Draft");
    controller.onPastedImage(new Object());

    Assert.assertTrue(transport.requests.isEmpty());
    Assert.assertEquals("", view.model.getActiveCommandId());
    Assert.assertEquals(
        "Wait for the current reply to finish before attaching files.",
        view.model.getCommandErrorText());
  }

  @Test
  public void pasteImageToolbarActionDocumentsPasteHintAndRestoresFocus() {
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            new FakeGateway(),
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            testAttachmentControllerFactory(new FakeAttachmentTransport()),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));

    Assert.assertTrue(controller.onToolbarAction(J2clDailyToolbarAction.ATTACHMENT_PASTE_IMAGE));

    Assert.assertEquals("attachment-paste-image", view.model.getActiveCommandId());
    Assert.assertEquals("Paste an image into the reply box.", view.model.getCommandStatusText());
    Assert.assertEquals(1, view.focusReplyComposerCalls);
  }

  @Test
  public void clearFormattingPreservesActiveUploadStatusPriority() {
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
            new J2clComposeSurfaceController.AttachmentFileSelection(new Object(), "busy.png")));

    Assert.assertTrue(controller.onToolbarAction(J2clDailyToolbarAction.CLEAR_FORMATTING));

    Assert.assertEquals("attachment-upload-queue", view.model.getActiveCommandId());
    Assert.assertEquals("Uploading busy.png (0%).", view.model.getCommandStatusText());
  }
}
