package org.waveprotocol.box.j2cl.compose;

import com.google.j2cl.junit.apt.J2clTestInput;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.waveprotocol.box.j2cl.telemetry.RecordingTelemetrySink;
import org.waveprotocol.box.j2cl.testsupport.FakeGateway;
import org.waveprotocol.box.j2cl.testsupport.FakeView;

/**
 * #1270: split from the monster J2clComposeSurfaceControllerTest. Covers
 * dropped-file delegation and delete-blip tombstone guards.
 */
@J2clTestInput(J2clComposeDropAndDeleteBlipTest.class)
public class J2clComposeDropAndDeleteBlipTest extends J2clComposeControllerTestSupport {

  // F-3.S4 (#1038, R-5.6 step 1): drag-drop telemetry path. The
  // controller routes onDroppedFiles through the same upload plumbing
  // as the H.19 paperclip path, but emits a separate
  // compose.attachment_dropped telemetry event with the file kind.
  @Test
  public void onDroppedFilesEmitsDroppedTelemetryAndDelegatesToAttachmentSelection() {
    RecordingTelemetrySink telemetry = new RecordingTelemetrySink();
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            waveId -> { },
            waveId -> { },
            telemetry);
    controller.start();
    openWaveForReply(controller);
    java.util.List<J2clComposeSurfaceController.AttachmentFileSelection> selections =
        java.util.Arrays.asList(
            new J2clComposeSurfaceController.AttachmentFileSelection("payload-1", "photo.png"));
    controller.onDroppedFiles(selections);
    Assert.assertTrue(
        "compose.attachment_dropped telemetry must record kind=image for .png",
        telemetry.events().stream()
            .anyMatch(
                e ->
                    "compose.attachment_dropped".equals(e.getName())
                        && "image".equals(e.getFields().get("kind"))
                        && "queued".equals(e.getFields().get("outcome"))
                        && "1".equals(e.getFields().get("count"))));
  }

  @Test
  public void onDroppedFilesRecordsEmptyOutcomeForEmptyDrop() {
    RecordingTelemetrySink telemetry = new RecordingTelemetrySink();
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            waveId -> { },
            waveId -> { },
            telemetry);
    controller.start();
    openWaveForReply(controller);
    controller.onDroppedFiles(Collections.<J2clComposeSurfaceController.AttachmentFileSelection>emptyList());
    Assert.assertTrue(
        "empty-drop telemetry recorded",
        telemetry.events().stream()
            .anyMatch(
                e ->
                    "compose.attachment_dropped".equals(e.getName())
                        && "empty".equals(e.getFields().get("outcome"))));
  }

  // review-1077 Bug 8: drops blocked by the acceptance gates inside
  // onAttachmentFilesSelected (signed-out / no-wave / reply-submitting)
  // must report a dedicated rejected-* outcome rather than the
  // optimistic `queued` outcome the controller previously emitted.
  @Test
  public void onDroppedFilesRecordsRejectedOutcomeWhenNoSelectedWave() {
    RecordingTelemetrySink telemetry = new RecordingTelemetrySink();
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            waveId -> { },
            waveId -> { },
            telemetry);
    controller.start();
    // No openWaveForReply() — no selected wave for this drop.
    java.util.List<J2clComposeSurfaceController.AttachmentFileSelection> selections =
        java.util.Arrays.asList(
            new J2clComposeSurfaceController.AttachmentFileSelection("p1", "doc.pdf"));
    controller.onDroppedFiles(selections);
    Assert.assertTrue(
        "drop without an active wave must record rejected-no-wave (not queued)",
        telemetry.events().stream()
            .anyMatch(
                e ->
                    "compose.attachment_dropped".equals(e.getName())
                        && "rejected-no-wave".equals(e.getFields().get("outcome"))));
    Assert.assertFalse(
        "blocked drop must NOT record outcome=queued",
        telemetry.events().stream()
            .anyMatch(
                e ->
                    "compose.attachment_dropped".equals(e.getName())
                        && "queued".equals(e.getFields().get("outcome"))));
  }

  @Test
  public void onDroppedFilesRecordsRejectedOutcomeWhenSignedOut() {
    RecordingTelemetrySink telemetry = new RecordingTelemetrySink();
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            waveId -> { },
            waveId -> { },
            telemetry);
    controller.start();
    openWaveForReply(controller);
    controller.onSignedOut();
    java.util.List<J2clComposeSurfaceController.AttachmentFileSelection> selections =
        java.util.Arrays.asList(
            new J2clComposeSurfaceController.AttachmentFileSelection("p1", "doc.pdf"));
    controller.onDroppedFiles(selections);
    Assert.assertTrue(
        "drop while signed out must record rejected-signed-out outcome",
        telemetry.events().stream()
            .anyMatch(
                e ->
                    "compose.attachment_dropped".equals(e.getName())
                        && "rejected-signed-out".equals(e.getFields().get("outcome"))));
  }

  // F-3.S4 (#1038, R-5.6 F.6): blip-delete gateway wiring. The
  // controller fetches the bootstrap, calls
  // DeltaFactory.createBlipDeleteRequest, and submits the result.
  @Test
  public void onDeleteBlipRequestedSubmitsTombstoneDelta() {
    RecordingTelemetrySink telemetry = new RecordingTelemetrySink();
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            waveId -> { },
            waveId -> { },
            telemetry);
    controller.start();
    openWaveForReply(controller);
    int beforeSubmits = gateway.submitCalls;
    controller.onDeleteBlipRequested("b+target", "example.com/w+1", TEST_BODY_ITEM_COUNT);
    Assert.assertEquals(beforeSubmits + 1, gateway.submitCalls);
    String deltaJson = gateway.lastSubmitRequest.getDeltaJson();
    Assert.assertTrue(
        "delta must target the deleted blip document",
        deltaJson.contains("\"1\":\"b+target\""));
    Assert.assertTrue(
        "delta must carry tombstone/deleted=true annotation",
        deltaJson.contains("tombstone/deleted")
            && deltaJson.contains("\"3\":\"true\""));
    assertContains(
        deltaJson,
        "{\"5\":" + TEST_BODY_ITEM_COUNT + "}",
        "{\"1\":{\"2\":[\"tombstone/deleted\"]}}");
    Assert.assertTrue(
        "success telemetry recorded",
        telemetry.events().stream()
            .anyMatch(
                e ->
                    "compose.blip_deleted".equals(e.getName())
                        && "success".equals(e.getFields().get("outcome"))));
  }

  @Test
  public void onDeleteBlipRequestedIgnoresWhenSignedOut() {
    RecordingTelemetrySink telemetry = new RecordingTelemetrySink();
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            waveId -> { },
            waveId -> { },
            telemetry);
    controller.start();
    openWaveForReply(controller);
    controller.onSignedOut();
    int beforeFetches = gateway.fetchBootstrapCalls;
    controller.onDeleteBlipRequested("b+target", "example.com/w+1", TEST_BODY_ITEM_COUNT);
    Assert.assertEquals(beforeFetches, gateway.fetchBootstrapCalls);
    Assert.assertTrue(
        "signed-out telemetry recorded",
        telemetry.events().stream()
            .anyMatch(
                e ->
                    "compose.blip_deleted".equals(e.getName())
                        && "signed-out".equals(e.getFields().get("outcome"))));
  }

  @Test
  public void onDeleteBlipRequestedIgnoresBlankBlipId() {
    RecordingTelemetrySink telemetry = new RecordingTelemetrySink();
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            waveId -> { },
            waveId -> { },
            telemetry);
    controller.start();
    openWaveForReply(controller);
    int beforeFetches = gateway.fetchBootstrapCalls;
    controller.onDeleteBlipRequested("", "example.com/w+1", TEST_BODY_ITEM_COUNT);
    controller.onDeleteBlipRequested("   ", "example.com/w+1", TEST_BODY_ITEM_COUNT);
    controller.onDeleteBlipRequested(null, "example.com/w+1", TEST_BODY_ITEM_COUNT);
    Assert.assertEquals(beforeFetches, gateway.fetchBootstrapCalls);
    Assert.assertTrue(
        "missing-blip telemetry recorded for empty inputs",
        telemetry.events().stream()
            .anyMatch(
                e ->
                    "compose.blip_deleted".equals(e.getName())
                        && "missing-blip".equals(e.getFields().get("outcome"))));
  }

  @Test
  public void onDeleteBlipRequestedIgnoresWhenNoSelectedWave() {
    RecordingTelemetrySink telemetry = new RecordingTelemetrySink();
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            waveId -> { },
            waveId -> { },
            telemetry);
    controller.start();
    int beforeFetches = gateway.fetchBootstrapCalls;
    controller.onDeleteBlipRequested("b+target", "", TEST_BODY_ITEM_COUNT);
    Assert.assertEquals(beforeFetches, gateway.fetchBootstrapCalls);
    Assert.assertTrue(
        "no-selected-wave telemetry recorded",
        telemetry.events().stream()
            .anyMatch(
                e ->
                    "compose.blip_deleted".equals(e.getName())
                        && "no-selected-wave".equals(e.getFields().get("outcome"))));
  }

  // F-3.S4 (#1038): if the user navigates away from the wave between
  // dispatching the confirm dialog and answering it, the controller
  // must reject the delete and emit a `wave-changed` telemetry event.
  @Test
  public void onDeleteBlipRequestedIgnoresWhenWaveChanged() {
    RecordingTelemetrySink telemetry = new RecordingTelemetrySink();
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            waveId -> { },
            waveId -> { },
            telemetry);
    controller.start();
    openWaveForReply(controller); // selected wave = example.com/w+1
    int beforeFetches = gateway.fetchBootstrapCalls;
    controller.onDeleteBlipRequested(
        "b+target", "example.com/w+other", TEST_BODY_ITEM_COUNT);
    Assert.assertEquals(beforeFetches, gateway.fetchBootstrapCalls);
    Assert.assertTrue(
        "wave-changed telemetry recorded",
        telemetry.events().stream()
            .anyMatch(
                e ->
                    "compose.blip_deleted".equals(e.getName())
                        && "wave-changed".equals(e.getFields().get("outcome"))));
  }
}
