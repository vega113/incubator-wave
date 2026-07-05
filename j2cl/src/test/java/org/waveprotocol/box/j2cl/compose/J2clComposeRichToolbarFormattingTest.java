package org.waveprotocol.box.j2cl.compose;

import com.google.j2cl.junit.apt.J2clTestInput;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.waveprotocol.box.j2cl.attachment.J2clAttachmentUploadClient;
import org.waveprotocol.box.j2cl.search.J2clSidecarWriteSession;
import org.waveprotocol.box.j2cl.telemetry.J2clClientTelemetry;
import org.waveprotocol.box.j2cl.telemetry.RecordingTelemetrySink;
import org.waveprotocol.box.j2cl.testsupport.FakeAttachmentTransport;
import org.waveprotocol.box.j2cl.testsupport.FakeGateway;
import org.waveprotocol.box.j2cl.testsupport.FakeView;
import org.waveprotocol.box.j2cl.toolbar.J2clDailyToolbarAction;

/**
 * #1270: split from the monster J2clComposeSurfaceControllerTest. Covers
 * rich-text toolbar formatting commands, telemetry and unsupported-action fallbacks.
 */
@J2clTestInput(J2clComposeRichToolbarFormattingTest.class)
public class J2clComposeRichToolbarFormattingTest extends J2clComposeControllerTestSupport {

  @Test
  public void toolbarBoldCommandEmitsStructuredRichReplyAnnotation() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    List<String> refreshed = new ArrayList<String>();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            waveId -> { },
            refreshed::add);

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    Assert.assertTrue(controller.onToolbarAction(J2clDailyToolbarAction.BOLD));
    Assert.assertEquals("bold", view.model.getActiveCommandId());
    Assert.assertEquals("Bold applied to the current draft.", view.model.getCommandStatusText());
    controller.onReplySubmitted("Bold reply");

    Assert.assertEquals(Arrays.asList("example.com/w+1"), refreshed);
    assertContains(
        gateway.lastSubmitRequest.getDeltaJson(),
        "{\"1\":{\"3\":[{\"1\":\"fontWeight\",\"3\":\"bold\"}]}}",
        "\"2\":\"Bold reply\"",
        "{\"1\":{\"2\":[\"fontWeight\"]}}");
    Assert.assertEquals("", view.model.getActiveCommandId());
    Assert.assertEquals("", view.model.getCommandStatusText());
  }

  @Test
  public void richEditCommandAppliedEmitsTelemetry() {
    RecordingTelemetrySink telemetry = new RecordingTelemetrySink();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller = newControllerWithTelemetry(view, telemetry);

    controller.start();
    openWaveForReply(controller);
    Assert.assertTrue(controller.onToolbarAction(J2clDailyToolbarAction.BOLD));

    J2clClientTelemetry.Event event = telemetry.lastEvent();
    Assert.assertEquals("richEdit.command.applied", event.getName());
    Assert.assertEquals("bold", event.getFields().get("commandId"));
    Assert.assertEquals("applied", event.getFields().get("result"));
  }

  @Test
  public void richEditCommandClearedEmitsTelemetry() {
    RecordingTelemetrySink telemetry = new RecordingTelemetrySink();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller = newControllerWithTelemetry(view, telemetry);

    controller.start();
    openWaveForReply(controller);
    controller.onToolbarAction(J2clDailyToolbarAction.BOLD);
    Assert.assertTrue(controller.onToolbarAction(J2clDailyToolbarAction.BOLD));

    J2clClientTelemetry.Event event = telemetry.lastEvent();
    Assert.assertEquals("richEdit.command.applied", event.getName());
    Assert.assertEquals("bold", event.getFields().get("commandId"));
    Assert.assertEquals("cleared", event.getFields().get("result"));
  }

  @Test
  public void clearFormattingAcceptedEmitsTelemetry() {
    RecordingTelemetrySink telemetry = new RecordingTelemetrySink();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller = newControllerWithTelemetry(view, telemetry);

    controller.start();
    openWaveForReply(controller);
    Assert.assertTrue(controller.onToolbarAction(J2clDailyToolbarAction.CLEAR_FORMATTING));

    Assert.assertEquals("richEdit.command.applied", telemetry.lastEvent().getName());
    Assert.assertEquals("clear-formatting", telemetry.lastEvent().getFields().get("commandId"));
    Assert.assertEquals("cleared", telemetry.lastEvent().getFields().get("result"));
  }

  @Test
  public void richEditTelemetryDoesNotEmitForRejectedActions() {
    RecordingTelemetrySink telemetry = new RecordingTelemetrySink();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller = newControllerWithTelemetry(view, telemetry);

    controller.start();
    controller.onToolbarAction(J2clDailyToolbarAction.BOLD);
    controller.onToolbarAction(J2clDailyToolbarAction.ATTACHMENT_INSERT);

    Assert.assertTrue(telemetry.events().isEmpty());
  }

  @Test
  public void focusCreateSurfaceRecordsOpenedTelemetryAndUsesShortcutBodyFocus() {
    RecordingTelemetrySink telemetry = new RecordingTelemetrySink();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller = newControllerWithTelemetry(view, telemetry);

    controller.start();
    controller.focusCreateSurface();

    Assert.assertEquals(1, view.focusCreateSurfaceCalls);
    Assert.assertEquals(0, view.focusCreateComposerCalls);
    J2clClientTelemetry.Event buttonEvent = telemetry.lastEvent();
    Assert.assertEquals("compose.opened", buttonEvent.getName());
    Assert.assertEquals("create", buttonEvent.getFields().get("mode"));
    Assert.assertEquals("button", buttonEvent.getFields().get("trigger"));

    controller.focusCreateSurface("shortcut");

    Assert.assertEquals(1, view.focusCreateSurfaceCalls);
    Assert.assertEquals(1, view.focusCreateComposerCalls);
    J2clClientTelemetry.Event shortcutEvent = telemetry.lastEvent();
    Assert.assertEquals("compose.opened", shortcutEvent.getName());
    Assert.assertEquals("create", shortcutEvent.getFields().get("mode"));
    Assert.assertEquals("shortcut", shortcutEvent.getFields().get("trigger"));
  }

  @Test
  public void focusCreateSurfaceDoesNotRecordTelemetryWhenCreateUnavailable() {
    RecordingTelemetrySink signedOutTelemetry = new RecordingTelemetrySink();
    FakeView signedOutView = new FakeView();
    J2clComposeSurfaceController signedOutController =
        newControllerWithTelemetry(signedOutView, signedOutTelemetry);

    signedOutController.start();
    signedOutController.onSignedOut();
    signedOutController.focusCreateSurface();
    signedOutController.focusCreateSurface("shortcut");

    Assert.assertEquals(0, signedOutView.focusCreateSurfaceCalls);
    Assert.assertEquals(0, signedOutView.focusCreateComposerCalls);
    Assert.assertTrue(signedOutTelemetry.events().isEmpty());

    FakeGateway pendingGateway = new FakeGateway();
    pendingGateway.autoResolveBootstrap = false;
    RecordingTelemetrySink pendingTelemetry = new RecordingTelemetrySink();
    FakeView pendingView = new FakeView();
    J2clComposeSurfaceController pendingController =
        new J2clComposeSurfaceController(
            pendingGateway,
            pendingView,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            waveId -> { },
            waveId -> { },
            pendingTelemetry);

    pendingController.start();
    pendingController.onCreateSubmittedWithTitle("Title", "Body");
    Assert.assertTrue(pendingView.model.isCreateSubmitting());
    pendingController.focusCreateSurface();
    pendingController.focusCreateSurface("shortcut");

    Assert.assertEquals(0, pendingView.focusCreateSurfaceCalls);
    Assert.assertEquals(0, pendingView.focusCreateComposerCalls);
    Assert.assertTrue(pendingTelemetry.events().isEmpty());
  }

  @Test
  public void throwingTelemetrySinkDoesNotBreakRichEditCommand() {
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        newControllerWithTelemetry(
            view,
            event -> {
              throw new RuntimeException("telemetry boom");
            });

    controller.start();
    openWaveForReply(controller);
    Assert.assertTrue(controller.onToolbarAction(J2clDailyToolbarAction.BOLD));
    Assert.assertEquals("bold", view.model.getActiveCommandId());
  }

  @Test
  public void replySubmitUsesFormattingSnapshotFromSubmitClick() {
    FakeGateway gateway = new FakeGateway();
    gateway.autoResolveBootstrap = false;
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
    Assert.assertTrue(controller.onToolbarAction(J2clDailyToolbarAction.BOLD));
    controller.onReplySubmitted("Snapshot reply");
    Assert.assertTrue(controller.onToolbarAction(J2clDailyToolbarAction.ITALIC));

    gateway.resolveBootstrap();

    assertContains(
        gateway.lastSubmitRequest.getDeltaJson(),
        "{\"1\":{\"3\":[{\"1\":\"fontWeight\",\"3\":\"bold\"}]}}",
        "\"2\":\"Snapshot reply\"");
    Assert.assertFalse(gateway.lastSubmitRequest.getDeltaJson().contains("fontStyle"));
  }

  @Test
  public void replyToolbarFormattingDoesNotAffectCreateWaveSubmit() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    List<String> created = new ArrayList<String>();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            created::add,
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    Assert.assertTrue(controller.onToolbarAction(J2clDailyToolbarAction.BOLD));
    controller.onCreateSubmitted("New wave");

    Assert.assertEquals(1, gateway.submitCalls);
    Assert.assertEquals(Arrays.asList("example.com/w+seedA"), created);
    Assert.assertFalse(gateway.lastSubmitRequest.getDeltaJson().contains("fontWeight"));
    assertContains(gateway.lastSubmitRequest.getDeltaJson(), "\"2\":\"New wave\"");
  }

  @Test
  public void toolbarRichCommandTogglesOffBeforeSubmit() {
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
    Assert.assertTrue(controller.onToolbarAction(J2clDailyToolbarAction.BOLD));
    Assert.assertTrue(controller.onToolbarAction(J2clDailyToolbarAction.BOLD));
    Assert.assertEquals("", view.model.getActiveCommandId());
    controller.onReplySubmitted("Plain reply");

    Assert.assertEquals("", view.model.getActiveCommandId());
    Assert.assertFalse(gateway.lastSubmitRequest.getDeltaJson().contains("fontWeight"));
  }

  @Test
  public void toolbarRichCommandToggleOffPreservesActiveUploadStatus() {
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
            new J2clComposeSurfaceController.AttachmentFileSelection(new Object(), "uploading.png")));
    controller.onToolbarAction(J2clDailyToolbarAction.BOLD);

    // Toggle Bold off — the active upload must not be silently overwritten by "Bold cleared."
    Assert.assertTrue(controller.onToolbarAction(J2clDailyToolbarAction.BOLD));

    Assert.assertEquals("attachment-upload-queue", view.model.getActiveCommandId());
    Assert.assertEquals("Uploading uploading.png (0%).", view.model.getCommandStatusText());
    Assert.assertEquals("", view.model.getCommandErrorText());
  }

  @Test
  public void toolbarRichCommandToggleOffPreservesUploadErrorStatus() {
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
            new J2clComposeSurfaceController.AttachmentFileSelection(new Object(), "fail.png")));
    transport.complete(0, new J2clAttachmentUploadClient.HttpResponse(500, "error", null));
    controller.onToolbarAction(J2clDailyToolbarAction.BOLD);

    // Toggle Bold off — the attachment error must not be silently cleared.
    Assert.assertTrue(controller.onToolbarAction(J2clDailyToolbarAction.BOLD));

    Assert.assertEquals("attachment-error-state", view.model.getActiveCommandId());
    Assert.assertTrue(view.model.getCommandErrorText().contains("fail.png"));
    Assert.assertEquals("", view.model.getCommandStatusText());
  }

  @Test
  public void clearFormattingCommandRemovesStructuredInlineAnnotation() {
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
    controller.onToolbarAction(J2clDailyToolbarAction.BOLD);
    Assert.assertTrue(controller.onToolbarAction(J2clDailyToolbarAction.CLEAR_FORMATTING));
    Assert.assertEquals("", view.model.getActiveCommandId());
    Assert.assertEquals("Formatting cleared.", view.model.getCommandStatusText());
    controller.onReplySubmitted("Plain reply");

    Assert.assertFalse(gateway.lastSubmitRequest.getDeltaJson().contains("fontWeight"));
    Assert.assertEquals("", view.model.getActiveCommandId());
    Assert.assertEquals("", view.model.getCommandStatusText());
  }

  @Test
  public void unsupportedRichToolbarActionFallsBackToToolbarUnavailable() {
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            new FakeGateway(),
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));

    Assert.assertFalse(controller.onToolbarAction(J2clDailyToolbarAction.HEADING_H1));
    Assert.assertEquals("", view.model.getActiveCommandId());
    Assert.assertEquals("", view.model.getCommandStatusText());
    Assert.assertEquals("", view.model.getCommandErrorText());
  }

  @Test
  public void unsupportedToolbarActionFallsBackEvenBeforeWaveIsOpen() {
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            new FakeGateway(),
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            waveId -> { },
            waveId -> { });

    controller.start();

    Assert.assertFalse(controller.onToolbarAction(J2clDailyToolbarAction.HEADING_H1));
    Assert.assertEquals("", view.model.getActiveCommandId());
    Assert.assertEquals("", view.model.getCommandErrorText());
  }

  @Test
  public void unsupportedToolbarActionDoesNotClearExistingCommandError() {
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
    controller.onPastedImage(new Object());
    transport.complete(0, new J2clAttachmentUploadClient.HttpResponse(202, "accepted", null));
    String existingError = view.model.getCommandErrorText();

    Assert.assertFalse(controller.onToolbarAction(J2clDailyToolbarAction.HEADING_H1));

    Assert.assertEquals(existingError, view.model.getCommandErrorText());
  }

  @Test
  public void inlineRichToolbarActionRequiresSelectedWave() {
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            new FakeGateway(),
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            waveId -> { },
            waveId -> { });

    controller.start();
    Assert.assertTrue(controller.onToolbarAction(J2clDailyToolbarAction.BOLD));

    Assert.assertEquals("", view.model.getActiveCommandId());
    Assert.assertEquals("", view.model.getCommandStatusText());
    Assert.assertEquals(
        "Open a wave before using rich-edit toolbar actions.", view.model.getCommandErrorText());
  }

  @Test
  public void signedOutToolbarActionShowsNeutralToolbarCopy() {
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            new FakeGateway(),
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            waveId -> { },
            waveId -> { });

    controller.start();
    controller.onSignedOut();
    Assert.assertTrue(controller.onToolbarAction(J2clDailyToolbarAction.ATTACHMENT_INSERT));

    Assert.assertEquals("Sign in before using toolbar actions.", view.model.getCommandErrorText());
  }
}
