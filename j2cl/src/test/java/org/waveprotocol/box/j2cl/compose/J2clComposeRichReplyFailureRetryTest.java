package org.waveprotocol.box.j2cl.compose;

import com.google.j2cl.junit.apt.J2clTestInput;
import java.util.ArrayList;
import org.junit.Assert;
import org.junit.Test;
import org.waveprotocol.box.j2cl.search.J2clSidecarWriteSession;
import org.waveprotocol.box.j2cl.testsupport.FakeAttachmentTransport;
import org.waveprotocol.box.j2cl.testsupport.FakeFactory;
import org.waveprotocol.box.j2cl.testsupport.FakeGateway;
import org.waveprotocol.box.j2cl.testsupport.FakeView;
import org.waveprotocol.box.j2cl.toolbar.J2clDailyToolbarAction;
import org.waveprotocol.box.j2cl.transport.SidecarSubmitResponse;

/**
 * #1270: split from the monster J2clComposeSurfaceControllerTest. Covers
 * rich-annotation preservation across reply failures and retry.
 */
@J2clTestInput(J2clComposeRichReplyFailureRetryTest.class)
public class J2clComposeRichReplyFailureRetryTest extends J2clComposeControllerTestSupport {

  @Test
  public void richToolbarAnnotationSurvivesReplyFailureForRetry() {
    FakeGateway gateway = new FakeGateway();
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
    Assert.assertTrue(controller.onToolbarAction(J2clDailyToolbarAction.BOLD));
    gateway.submitResponse = new SidecarSubmitResponse(0, "server rejected", 0L);
    controller.onReplySubmitted("Bold reply");

    Assert.assertEquals(1, gateway.submitCalls);
    Assert.assertEquals("server rejected", view.model.getReplyErrorText());
    Assert.assertFalse(view.model.isReplySubmitting());
    Assert.assertEquals("", view.model.getActiveCommandId());

    gateway.submitResponse = new SidecarSubmitResponse(1, "", 45L);
    controller.onReplySubmitted("Bold reply");

    Assert.assertEquals(2, gateway.submitCalls);
    assertContains(
        gateway.lastSubmitRequest.getDeltaJson(),
        "{\"1\":{\"3\":[{\"1\":\"fontWeight\",\"3\":\"bold\"}]}}",
        "\"2\":\"Bold reply\"");

    controller.onReplySubmitted("Plain after retry");

    Assert.assertEquals(3, gateway.submitCalls);
    Assert.assertFalse(gateway.lastSubmitRequest.getDeltaJson().contains("fontWeight"));
    assertContains(gateway.lastSubmitRequest.getDeltaJson(), "\"2\":\"Plain after retry\"");
  }

  @Test
  public void replySubmitClosesActiveComposerOnlyAfterSuccess() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        newController(gateway, view, new FakeFactory(), new ArrayList<String>(), new ArrayList<String>());

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));

    controller.onReplySubmitted("Reply");

    Assert.assertEquals(1, gateway.submitCalls);
    Assert.assertEquals(1, view.closeActiveReplyComposerCalls);
    Assert.assertEquals("", view.model.getReplyDraft());
    Assert.assertEquals("", view.model.getReplyErrorText());
  }

  @Test
  public void replySubmitFailureKeepsActiveComposerOpenForRetry() {
    FakeGateway gateway = new FakeGateway();
    gateway.submitResponse = new SidecarSubmitResponse(0, "server rejected", 0L);
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        newController(
            gateway, view, new FakeFactory(), new ArrayList<String>(), new ArrayList<String>());

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));

    controller.onReplySubmitted("Retry me");

    Assert.assertEquals(1, gateway.submitCalls);
    Assert.assertEquals(0, view.closeActiveReplyComposerCalls);
    Assert.assertEquals("Retry me", view.model.getReplyDraft());
    Assert.assertEquals("server rejected", view.model.getReplyErrorText());
  }

  @Test
  public void richToolbarAnnotationSurvivesMultipleReplyFailuresForRetry() {
    FakeGateway gateway = new FakeGateway();
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
    Assert.assertTrue(controller.onToolbarAction(J2clDailyToolbarAction.BOLD));
    gateway.submitResponse = new SidecarSubmitResponse(0, "server rejected", 0L);
    controller.onReplySubmitted("Bold reply");
    controller.onReplySubmitted("Bold reply");

    gateway.submitResponse = new SidecarSubmitResponse(1, "", 45L);
    controller.onReplySubmitted("Bold reply");

    Assert.assertEquals(3, gateway.submitCalls);
    assertContains(
        gateway.lastSubmitRequest.getDeltaJson(),
        "{\"1\":{\"3\":[{\"1\":\"fontWeight\",\"3\":\"bold\"}]}}",
        "\"2\":\"Bold reply\"");
  }

  @Test
  public void richToolbarCanToggleOffAfterReplyFailureBeforeRetry() {
    FakeGateway gateway = new FakeGateway();
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
    Assert.assertTrue(controller.onToolbarAction(J2clDailyToolbarAction.BOLD));
    gateway.submitResponse = new SidecarSubmitResponse(0, "server rejected", 0L);
    controller.onReplySubmitted("Bold reply");

    Assert.assertTrue(controller.onToolbarAction(J2clDailyToolbarAction.BOLD));
    gateway.submitResponse = new SidecarSubmitResponse(1, "", 45L);
    controller.onReplySubmitted("Plain retry");

    Assert.assertEquals(2, gateway.submitCalls);
    Assert.assertFalse(gateway.lastSubmitRequest.getDeltaJson().contains("fontWeight"));
    assertContains(gateway.lastSubmitRequest.getDeltaJson(), "\"2\":\"Plain retry\"");
  }

  @Test
  public void richToolbarCanSwitchFormattingAfterReplyFailureBeforeRetry() {
    FakeGateway gateway = new FakeGateway();
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
    Assert.assertTrue(controller.onToolbarAction(J2clDailyToolbarAction.BOLD));
    gateway.submitResponse = new SidecarSubmitResponse(0, "server rejected", 0L);
    controller.onReplySubmitted("Bold reply");

    Assert.assertTrue(controller.onToolbarAction(J2clDailyToolbarAction.ITALIC));
    gateway.submitResponse = new SidecarSubmitResponse(1, "", 45L);
    controller.onReplySubmitted("Italic retry");

    Assert.assertEquals(2, gateway.submitCalls);
    Assert.assertFalse(gateway.lastSubmitRequest.getDeltaJson().contains("fontWeight"));
    assertContains(
        gateway.lastSubmitRequest.getDeltaJson(),
        "{\"1\":{\"3\":[{\"1\":\"fontStyle\",\"3\":\"italic\"}]}}",
        "\"2\":\"Italic retry\"");
  }

  @Test
  public void waveChangeAfterFailedRichReplyClearsPreservedAnnotation() {
    FakeGateway gateway = new FakeGateway();
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
    Assert.assertTrue(controller.onToolbarAction(J2clDailyToolbarAction.BOLD));
    gateway.submitResponse = new SidecarSubmitResponse(0, "server rejected", 0L);
    controller.onReplySubmitted("Bold reply");

    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+2", "chan-2", 50L, "BCDE", "b+root"));
    gateway.submitResponse = new SidecarSubmitResponse(1, "", 51L);
    controller.onReplySubmitted("Plain new wave");

    Assert.assertEquals(2, gateway.submitCalls);
    Assert.assertFalse(gateway.lastSubmitRequest.getDeltaJson().contains("fontWeight"));
    assertContains(gateway.lastSubmitRequest.getDeltaJson(), "\"2\":\"Plain new wave\"");
  }
}
