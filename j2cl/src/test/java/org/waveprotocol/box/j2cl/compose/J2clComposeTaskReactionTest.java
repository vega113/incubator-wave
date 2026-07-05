package org.waveprotocol.box.j2cl.compose;

import com.google.j2cl.junit.apt.J2clTestInput;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.waveprotocol.box.j2cl.search.J2clSidecarWriteSession;
import org.waveprotocol.box.j2cl.telemetry.RecordingTelemetrySink;
import org.waveprotocol.box.j2cl.testsupport.FakeFactory;
import org.waveprotocol.box.j2cl.testsupport.FakeGateway;
import org.waveprotocol.box.j2cl.testsupport.FakeView;
import org.waveprotocol.box.j2cl.transport.SidecarReactionEntry;

/**
 * #1270: split from the monster J2clComposeSurfaceControllerTest. Covers
 * task-toggle and reaction-toggle deltas, telemetry and session-refresh survival.
 */
@J2clTestInput(J2clComposeTaskReactionTest.class)
public class J2clComposeTaskReactionTest extends J2clComposeControllerTestSupport {

  // F-3.S2 (#1038, R-5.4 step 3) — task-toggle goes through the
  // gateway and emits telemetry with state="completed" or "open".
  @Test
  public void onTaskToggledSubmitsDeltaAndRecordsTelemetry() {
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
    controller.onTaskToggled("b+root", true, TEST_BODY_ITEM_COUNT);
    Assert.assertEquals(beforeSubmits + 1, gateway.submitCalls);
    assertContains(
        gateway.lastSubmitRequest.getDeltaJson(),
        "{\"1\":{\"3\":[{\"1\":\"task/done\",\"3\":\"true\"}]}}",
        "{\"5\":" + TEST_BODY_ITEM_COUNT + "}",
        "{\"1\":{\"2\":[\"task/done\"]}}");
    Assert.assertTrue(
        "compose.task_toggled (completed) event should be recorded",
        telemetry.events().stream()
            .anyMatch(
                e ->
                    "compose.task_toggled".equals(e.getName())
                        && "completed".equals(e.getFields().get("state"))));
  }

  @Test
  public void onTaskToggledOpenStateRecordsOpenTelemetry() {
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
    controller.onTaskToggled("b+root", false, TEST_BODY_ITEM_COUNT);
    Assert.assertTrue(
        "compose.task_toggled (open) event should be recorded",
        telemetry.events().stream()
            .anyMatch(
                e ->
                    "compose.task_toggled".equals(e.getName())
                        && "open".equals(e.getFields().get("state"))));
  }

  @Test
  public void onTaskToggledIgnoresEmptyBlipId() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        newController(gateway, view, new FakeFactory(), new ArrayList<String>(), new ArrayList<String>());
    controller.start();
    openWaveForReply(controller);
    int beforeFetches = gateway.fetchBootstrapCalls;
    controller.onTaskToggled("", true, TEST_BODY_ITEM_COUNT);
    controller.onTaskToggled(null, true, TEST_BODY_ITEM_COUNT);
    Assert.assertEquals(beforeFetches, gateway.fetchBootstrapCalls);
  }

  @Test
  public void onTaskToggledIgnoredWhenNoSelectedWave() {
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
    int beforeFetches = gateway.fetchBootstrapCalls;
    controller.onTaskToggled("b+root", true, TEST_BODY_ITEM_COUNT);
    Assert.assertEquals(beforeFetches, gateway.fetchBootstrapCalls);
  }

  // F-3.S3 (#1038, R-5.5): reaction toggle path. The controller
  // computes adding-vs-removing from its cached snapshot, fetches the
  // bootstrap, and submits a delta against the react+ document.
  @Test
  public void onReactionToggledAddsWhenSnapshotEmpty() {
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
    controller.onReactionToggled("b+root", "👍");
    Assert.assertEquals(beforeSubmits + 1, gateway.submitCalls);
    Assert.assertNotNull(gateway.lastSubmitRequest);
    String deltaJson = gateway.lastSubmitRequest.getDeltaJson();
    Assert.assertTrue(
        "delta must target react+ document, got: " + deltaJson,
        deltaJson.contains("\"1\":\"react+b+root\""));
    Assert.assertTrue(
        "added telemetry recorded",
        telemetry.events().stream()
            .anyMatch(
                e ->
                    "compose.reaction_toggled".equals(e.getName())
                        && "added".equals(e.getFields().get("state"))));
  }

  @Test
  public void onReactionToggledRemovesWhenUserAlreadyReacted() {
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
    java.util.HashMap<String, java.util.List<SidecarReactionEntry>> snapshots =
        new java.util.HashMap<>();
    snapshots.put(
        "b+root",
        java.util.Arrays.asList(
            new SidecarReactionEntry("👍", java.util.Arrays.asList("user@example.com"))));
    controller.setReactionSnapshots(snapshots);
    int beforeSubmits = gateway.submitCalls;
    controller.onReactionToggled("b+root", "👍");
    Assert.assertEquals(beforeSubmits + 1, gateway.submitCalls);
    String deltaJson = gateway.lastSubmitRequest.getDeltaJson();
    // toggle off should emit a delete-element-start for the user.
    Assert.assertTrue(
        "delta must contain delete-element-start for the user element",
        deltaJson.contains("\"7\":{\"1\":\"user\""));
    Assert.assertTrue(
        "removed telemetry recorded",
        telemetry.events().stream()
            .anyMatch(
                e ->
                    "compose.reaction_toggled".equals(e.getName())
                        && "removed".equals(e.getFields().get("state"))));
  }

  @Test
  public void onReactionToggledIgnoresWhenSignedOut() {
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
    openWaveForReply(controller);
    controller.onSignedOut();
    int beforeFetches = gateway.fetchBootstrapCalls;
    controller.onReactionToggled("b+root", "👍");
    Assert.assertEquals(beforeFetches, gateway.fetchBootstrapCalls);
  }

  @Test
  public void onReactionToggledIgnoresEmptyBlipIdAndEmoji() {
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
    openWaveForReply(controller);
    int beforeFetches = gateway.fetchBootstrapCalls;
    controller.onReactionToggled("", "👍");
    controller.onReactionToggled("b+root", "");
    controller.onReactionToggled(null, null);
    Assert.assertEquals(beforeFetches, gateway.fetchBootstrapCalls);
  }

  @Test
  public void onReactionToggledNotifiesAddressListenerOnBootstrap() {
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
    final String[] capturedAddress = new String[] {""};
    controller.setCurrentUserAddressListener(addr -> capturedAddress[0] = addr);
    openWaveForReply(controller);
    controller.onReactionToggled("b+root", "👍");
    Assert.assertEquals("user@example.com", capturedAddress[0]);
  }

  @Test
  public void onTaskMetadataChangedSubmitsDelta() {
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
    openWaveForReply(controller);
    int beforeSubmits = gateway.submitCalls;
    controller.onTaskMetadataChanged(
        "b+root", "alice@example.com", "2026-05-15", TEST_BODY_ITEM_COUNT);
    Assert.assertEquals(beforeSubmits + 1, gateway.submitCalls);
    assertContains(
        gateway.lastSubmitRequest.getDeltaJson(),
        "{\"1\":\"task/assignee\",\"3\":\"alice@example.com\"}",
        "{\"5\":" + TEST_BODY_ITEM_COUNT + "}",
        "{\"1\":{\"2\":[\"task/assignee\",\"task/dueTs\"]}}");
  }

  // F-3.S2 (#1068): regression — a no-op session refresh (same wave id,
  // new J2clSidecarWriteSession instance) used to break the deferred
  // task-toggle write because the post-bootstrap guard compared
  // sessions by reference. The fix compares by selected wave id, so
  // the submit must still go through.
  @Test
  public void onTaskToggledSurvivesNoOpSessionRefresh() {
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
    openWaveForReply(controller);
    int beforeSubmits = gateway.submitCalls;
    controller.onTaskToggled("b+root", true, TEST_BODY_ITEM_COUNT);
    // Bootstrap is still pending — simulate a no-op session refresh on
    // the same wave between the click and the bootstrap response.
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 45L, "EFGH", "b+root"));
    gateway.resolveBootstrap();
    Assert.assertEquals(
        "task-toggle write must survive a no-op session refresh on the same wave",
        beforeSubmits + 1,
        gateway.submitCalls);
    Assert.assertEquals("chan-1", gateway.lastSubmitRequest.getChannelId());
    assertContains(gateway.lastSubmitRequest.getDeltaJson(), "\"1\":45", "\"2\":\"EFGH\"");
  }

  @Test
  public void onTaskMetadataChangedSurvivesNoOpSessionRefresh() {
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
    openWaveForReply(controller);
    int beforeSubmits = gateway.submitCalls;
    controller.onTaskMetadataChanged(
        "b+root", "alice@example.com", "2026-05-15", TEST_BODY_ITEM_COUNT);
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 45L, "EFGH", "b+root"));
    gateway.resolveBootstrap();
    Assert.assertEquals(
        "task-metadata write must survive a no-op session refresh on the same wave",
        beforeSubmits + 1,
        gateway.submitCalls);
    Assert.assertEquals("chan-1", gateway.lastSubmitRequest.getChannelId());
    assertContains(gateway.lastSubmitRequest.getDeltaJson(), "\"1\":45", "\"2\":\"EFGH\"");
  }

  @Test
  public void onTaskToggledDropsWriteOnWaveSwitch() {
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
    openWaveForReply(controller);
    int beforeSubmits = gateway.submitCalls;
    controller.onTaskToggled("b+root", true, TEST_BODY_ITEM_COUNT);
    // Genuine wave switch — the captured session no longer matches the
    // current selected wave; the write must be dropped.
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+2", "chan-2", 1L, "ZZZZ", "b+root"));
    gateway.resolveBootstrap();
    Assert.assertEquals(
        "task-toggle write must NOT submit after a real wave switch",
        beforeSubmits,
        gateway.submitCalls);
  }
}
