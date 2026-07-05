package org.waveprotocol.box.j2cl.compose;

import com.google.j2cl.junit.apt.J2clTestInput;
import java.util.Arrays;
import org.junit.Assert;
import org.junit.Test;
import org.waveprotocol.box.j2cl.search.J2clSidecarWriteSession;
import org.waveprotocol.box.j2cl.telemetry.RecordingTelemetrySink;
import org.waveprotocol.box.j2cl.testsupport.FakeGateway;
import org.waveprotocol.box.j2cl.testsupport.FakeView;
import org.waveprotocol.box.j2cl.transport.SidecarSubmitResponse;

/**
 * #1270: split from the monster J2clComposeSurfaceControllerTest. Covers
 * wave-header actions: add-participant, publicity/lock toggles and failure telemetry.
 */
@J2clTestInput(J2clComposeWaveHeaderActionsTest.class)
public class J2clComposeWaveHeaderActionsTest extends J2clComposeControllerTestSupport {

  @Test
  public void onAddParticipantsRequestedSubmitsDeltaAndRecordsTelemetry() {
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

    controller.onAddParticipantsRequested(
        "example.com/w+1", Arrays.asList("Alice@Example.COM", "bob@example.com"));

    Assert.assertEquals(1, gateway.submitCalls);
    assertContains(
        gateway.lastSubmitRequest.getDeltaJson(),
        "{\"1\":\"alice@example.com\"}",
        "{\"1\":\"bob@example.com\"}");
    Assert.assertTrue(
        "success telemetry recorded",
        telemetry.events().stream()
            .anyMatch(
                e ->
                    "compose.participants_added".equals(e.getName())
                        && "success".equals(e.getFields().get("outcome"))));
  }

  @Test
  public void onPublicityToggleRequestedSubmitsSharedDomainParticipantDelta() {
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
    // creator must be the bootstrap user so canTogglePublicity passes
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession(
            "example.com/w+1", "chan-1", 44L, "ABCD", "b+root",
            Arrays.asList("user@example.com")));

    controller.onPublicityToggleRequested("example.com/w+1", true);
    assertContains(gateway.lastSubmitRequest.getDeltaJson(), "{\"1\":\"@example.com\"}");

    controller.onPublicityToggleRequested("example.com/w+1", false);
    assertContains(gateway.lastSubmitRequest.getDeltaJson(), "{\"2\":\"@example.com\"}");
  }

  @Test
  public void onLockStateToggleRequestedSubmitsLockDelta() {
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

    controller.onLockStateToggleRequested("example.com/w+1", "unlocked", "root");

    Assert.assertEquals(1, gateway.submitCalls);
    assertContains(
        gateway.lastSubmitRequest.getDeltaJson(),
        "\"1\":\"m/lock\"",
        "\"3\":{\"1\":\"lock\",\"2\":[{\"1\":\"mode\",\"2\":\"root\"}]}");
    Assert.assertTrue(
        "success telemetry recorded",
        telemetry.events().stream()
            .anyMatch(
                e ->
                    "compose.lock_toggled".equals(e.getName())
                        && "success".equals(e.getFields().get("outcome"))));
  }

  @Test
  public void waveHeaderActionsDropWriteWhenWaveChangesBeforeBootstrapReturns() {
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
    controller.onAddParticipantsRequested("example.com/w+1", Arrays.asList("alice@example.com"));
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+2", "chan-2", 1L, "ZZZZ", "b+root"));

    gateway.resolveBootstrap();

    Assert.assertEquals(0, gateway.submitCalls);
  }

  @Test
  public void waveHeaderActionUsesCapturedSessionWhenSameWaveRefreshesBeforeBootstrapReturns() {
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
    controller.onLockStateToggleRequested("example.com/w+1", "unlocked", "root");
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 45L, "EFGH", "b+root"));

    gateway.resolveBootstrap();

    Assert.assertEquals(1, gateway.submitCalls);
    assertContains(gateway.lastSubmitRequest.getDeltaJson(), "\"1\":44", "\"2\":\"ABCD\"");
    Assert.assertFalse(
        "header action must not mix the stale intent with refreshed base metadata",
        gateway.lastSubmitRequest.getDeltaJson().contains("\"2\":\"EFGH\""));
  }

  @Test
  public void publicityToggleBlockedForNonCreator() {
    FakeGateway gateway = new FakeGateway();
    RecordingTelemetrySink telemetry = new RecordingTelemetrySink();
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
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession(
            "example.com/w+1",
            "chan-1",
            44L,
            "ABCD",
            "b+root",
            // alice is the creator; user@example.com is a non-creator participant
            Arrays.asList("alice@example.com", "user@example.com", "bob@example.com")));

    controller.onPublicityToggleRequested("example.com/w+1", true);

    Assert.assertEquals(0, gateway.submitCalls);
    Assert.assertTrue(
        telemetry.events().stream()
            .anyMatch(
                e ->
                    "compose.publicity_toggled".equals(e.getName())
                        && "failure-build".equals(e.getFields().get("outcome"))));
  }

  @Test
  public void publicityToggleBlockedWhenMakingDirectMessagePublic() {
    FakeGateway gateway = new FakeGateway();
    RecordingTelemetrySink telemetry = new RecordingTelemetrySink();
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
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession(
            "example.com/w+1",
            "chan-1",
            44L,
            "ABCD",
            "b+root",
            // user@example.com is the creator; alice is the one other participant (2-person DM)
            Arrays.asList("user@example.com", "alice@example.com")));

    controller.onPublicityToggleRequested("example.com/w+1", true);

    Assert.assertEquals(0, gateway.submitCalls);
    Assert.assertTrue(
        telemetry.events().stream()
            .anyMatch(
                e ->
                    "compose.publicity_toggled".equals(e.getName())
                        && "failure-build".equals(e.getFields().get("outcome"))));
  }

  @Test
  public void waveHeaderActionSubmitErrorRecordsFailureTelemetry() {
    RecordingTelemetrySink telemetry = new RecordingTelemetrySink();
    FakeGateway gateway = new FakeGateway();
    gateway.submitResponse = new SidecarSubmitResponse(1, "server rejected", 45L);
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
    // user@example.com is the creator so canTogglePublicity passes; the
    // gateway error response then triggers the failure-submit path.
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession(
            "example.com/w+1", "chan-1", 44L, "ABCD", "b+root",
            Arrays.asList("user@example.com")));

    controller.onPublicityToggleRequested("example.com/w+1", true);

    Assert.assertTrue(
        "failure telemetry recorded",
        telemetry.events().stream()
            .anyMatch(
                e ->
                    "compose.publicity_toggled".equals(e.getName())
                        && "failure-submit".equals(e.getFields().get("outcome"))));
  }
}
