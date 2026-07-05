package org.waveprotocol.box.j2cl.compose;

import com.google.j2cl.junit.apt.J2clTestInput;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.waveprotocol.box.j2cl.notify.J2clNotificationService;
import org.waveprotocol.box.j2cl.notify.RecordingNotificationSink;
import org.waveprotocol.box.j2cl.search.J2clSidecarWriteSession;
import org.waveprotocol.box.j2cl.testsupport.FakeFactory;
import org.waveprotocol.box.j2cl.testsupport.FakeGateway;
import org.waveprotocol.box.j2cl.testsupport.FakeView;
import org.waveprotocol.box.j2cl.transport.SidecarSubmitResponse;

/**
 * #1270: split from the monster J2clComposeSurfaceControllerTest. Covers
 * stale-generation draft parking, retry, and bootstrap/reply failure routing.
 */
@J2clTestInput(J2clComposeStaleGenerationRetryTest.class)
public class J2clComposeStaleGenerationRetryTest extends J2clComposeControllerTestSupport {

  @Test
  public void sameWaveBasisRefreshPreservesDraftAndSurfacesStaleSubmitState() {
    FakeGateway gateway = new FakeGateway();
    gateway.autoResolveBootstrap = false;
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        newController(gateway, view, new FakeFactory(), new ArrayList<String>(), new ArrayList<String>());

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onReplySubmitted("Draft");
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-2", 45L, "BCDE", "b+root"));
    gateway.resolveBootstrap();

    Assert.assertEquals("Draft", view.model.getReplyDraft());
    Assert.assertTrue(view.model.isReplyStaleBasis());
    Assert.assertEquals(
        J2clComposeSurfaceController.STALE_REPLY_MESSAGE, view.model.getReplyErrorText());
    Assert.assertEquals(0, gateway.submitCalls);
  }

  @Test
  public void differentWaveSelectionClearsReplyDraft() {
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        newController(new FakeGateway(), view, new FakeFactory(), new ArrayList<String>(), new ArrayList<String>());

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onReplyDraftChanged("Draft");
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+2", "chan-2", 45L, "BCDE", "b+root"));

    Assert.assertEquals("", view.model.getReplyDraft());
  }

  @Test
  public void missingSelectedWaveRejectsReplyWithoutFetchingBootstrap() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        newController(gateway, view, new FakeFactory(), new ArrayList<String>(), new ArrayList<String>());

    controller.start();
    controller.onWriteSessionChanged(new J2clSidecarWriteSession(null, "chan-1", 44L, "ABCD", "b+root"));

    Assert.assertFalse(view.model.isReplyAvailable());
    Assert.assertEquals("", view.model.getReplyTargetLabel());
    Assert.assertEquals("Open a wave before replying.", view.model.getReplyStatusText());
    controller.onReplySubmitted("Draft");

    Assert.assertEquals("Draft", view.model.getReplyDraft());
    Assert.assertFalse(view.model.isReplySubmitting());
    Assert.assertFalse(view.model.isReplyAvailable());
    Assert.assertEquals("", view.model.getReplyTargetLabel());
    Assert.assertEquals("", view.model.getReplyStatusText());
    Assert.assertEquals("Open a wave before sending a reply.", view.model.getReplyErrorText());
    Assert.assertEquals(0, gateway.fetchBootstrapCalls);
  }

  @Test
  public void emptySelectedWaveRejectsReplyWithoutFetchingBootstrap() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        newController(gateway, view, new FakeFactory(), new ArrayList<String>(), new ArrayList<String>());

    controller.start();
    controller.onWriteSessionChanged(new J2clSidecarWriteSession("", "chan-1", 44L, "ABCD", "b+root"));

    Assert.assertFalse(view.model.isReplyAvailable());
    Assert.assertEquals("", view.model.getReplyTargetLabel());
    Assert.assertEquals("Open a wave before replying.", view.model.getReplyStatusText());
    controller.onReplySubmitted("Draft");

    Assert.assertEquals("Draft", view.model.getReplyDraft());
    Assert.assertFalse(view.model.isReplySubmitting());
    Assert.assertFalse(view.model.isReplyAvailable());
    Assert.assertEquals("", view.model.getReplyTargetLabel());
    Assert.assertEquals("", view.model.getReplyStatusText());
    Assert.assertEquals("Open a wave before sending a reply.", view.model.getReplyErrorText());
    Assert.assertEquals(0, gateway.fetchBootstrapCalls);
  }

  @Test
  public void differentWaveSelectionDuringStaleSubmitPreservesDraft() {
    FakeGateway gateway = new FakeGateway();
    gateway.autoResolveBootstrap = false;
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        newController(gateway, view, new FakeFactory(), new ArrayList<String>(), new ArrayList<String>());

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onReplySubmitted("Draft");
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+2", "chan-2", 45L, "BCDE", "b+root"));

    Assert.assertEquals("Draft", view.model.getReplyDraft());
    Assert.assertTrue(view.model.isReplyStaleBasis());
  }

  @Test
  public void laterDifferentWaveSelectionAfterStaleSubmitClearsDraft() {
    FakeGateway gateway = new FakeGateway();
    gateway.autoResolveBootstrap = false;
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        newController(gateway, view, new FakeFactory(), new ArrayList<String>(), new ArrayList<String>());

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onReplySubmitted("Draft");
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+2", "chan-2", 45L, "BCDE", "b+root"));

    Assert.assertEquals("Draft", view.model.getReplyDraft());
    Assert.assertTrue(view.model.isReplyStaleBasis());
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+3", "chan-3", 46L, "CDEF", "b+root"));

    Assert.assertEquals("", view.model.getReplyDraft());
    Assert.assertFalse(view.model.isReplyStaleBasis());
  }

  @Test
  public void nullWriteSessionAfterStaleSubmitPreservesDraftUntilDifferentWaveReconnect() {
    FakeGateway gateway = new FakeGateway();
    gateway.autoResolveBootstrap = false;
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        newController(gateway, view, new FakeFactory(), new ArrayList<String>(), new ArrayList<String>());

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onReplySubmitted("Draft");
    controller.onWriteSessionChanged(null);

    Assert.assertEquals("Draft", view.model.getReplyDraft());
    Assert.assertTrue(view.model.isReplyStaleBasis());
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+2", "chan-2", 45L, "BCDE", "b+root"));

    Assert.assertEquals("", view.model.getReplyDraft());
    Assert.assertFalse(view.model.isReplyStaleBasis());
    Assert.assertEquals("", view.model.getReplyErrorText());
  }

  @Test
  public void nullWriteSessionAfterStaleSubmitPreservesDraftThroughSameWaveReconnect() {
    FakeGateway gateway = new FakeGateway();
    gateway.autoResolveBootstrap = false;
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        newController(gateway, view, new FakeFactory(), new ArrayList<String>(), new ArrayList<String>());

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onReplySubmitted("Draft");
    controller.onWriteSessionChanged(null);
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-2", 45L, "BCDE", "b+root"));

    Assert.assertEquals("Draft", view.model.getReplyDraft());
    Assert.assertTrue(view.model.isReplyStaleBasis());
    Assert.assertEquals(
        J2clComposeSurfaceController.STALE_REPLY_MESSAGE, view.model.getReplyErrorText());
  }

  @Test
  public void nullWriteSessionPreservesFreshDraftThroughSameWaveReconnect() {
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        newController(new FakeGateway(), view, new FakeFactory(), new ArrayList<String>(), new ArrayList<String>());

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onReplyDraftChanged("Fresh draft");
    controller.onWriteSessionChanged(null);
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-2", 45L, "BCDE", "b+root"));

    Assert.assertEquals("Fresh draft", view.model.getReplyDraft());
    Assert.assertFalse(view.model.isReplyStaleBasis());
  }

  @Test
  public void sameWaveRefreshesAfterStaleSubmitKeepDraftAndErrorUntilRetry() {
    FakeGateway gateway = new FakeGateway();
    gateway.autoResolveBootstrap = false;
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        newController(gateway, view, new FakeFactory(), new ArrayList<String>(), new ArrayList<String>());

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onReplySubmitted("Draft");
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-2", 45L, "BCDE", "b+root"));
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-3", 46L, "CDEF", "b+root"));

    Assert.assertEquals("Draft", view.model.getReplyDraft());
    Assert.assertTrue(view.model.isReplyStaleBasis());
    Assert.assertEquals(
        J2clComposeSurfaceController.STALE_REPLY_MESSAGE, view.model.getReplyErrorText());
  }

  @Test
  public void editingStaleDraftThenNavigatingToDifferentWaveClearsEditedDraft() {
    FakeGateway gateway = new FakeGateway();
    gateway.autoResolveBootstrap = false;
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        newController(gateway, view, new FakeFactory(), new ArrayList<String>(), new ArrayList<String>());

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onReplySubmitted("Draft");
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+2", "chan-2", 45L, "BCDE", "b+root"));
    controller.onReplyDraftChanged("Edited draft");
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+3", "chan-3", 46L, "CDEF", "b+root"));

    Assert.assertEquals("", view.model.getReplyDraft());
    Assert.assertFalse(view.model.isReplyStaleBasis());
    Assert.assertEquals("", view.model.getReplyErrorText());
  }

  @Test
  public void returningToOriginalWaveAfterStaleDifferentWaveKeepsDraftForReview() {
    FakeGateway gateway = new FakeGateway();
    gateway.autoResolveBootstrap = false;
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        newController(gateway, view, new FakeFactory(), new ArrayList<String>(), new ArrayList<String>());

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onReplySubmitted("Draft");
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+2", "chan-2", 45L, "BCDE", "b+root"));
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-3", 46L, "CDEF", "b+root"));

    Assert.assertEquals("Draft", view.model.getReplyDraft());
    Assert.assertTrue(view.model.isReplyStaleBasis());
    Assert.assertEquals(J2clComposeSurfaceController.STALE_REPLY_MESSAGE, view.model.getReplyErrorText());
  }

  @Test
  public void staleDraftRetrySuccessClearsStaleState() {
    FakeGateway gateway = new FakeGateway();
    gateway.autoResolveBootstrap = false;
    FakeFactory factory = new FakeFactory();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        newController(gateway, view, factory, new ArrayList<String>(), new ArrayList<String>());

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onReplySubmitted("Draft");
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-2", 45L, "BCDE", "b+root"));
    gateway.autoResolveBootstrap = true;
    controller.onReplyDraftChanged("Edited draft");
    controller.onReplySubmitted("Edited draft");

    Assert.assertEquals("", view.model.getReplyDraft());
    Assert.assertFalse(view.model.isReplyStaleBasis());
    Assert.assertEquals("", view.model.getReplyErrorText());
    Assert.assertEquals("Edited draft", factory.lastReplyText);
  }

  @Test
  public void retryInvalidatedByAnotherSessionChangeReparksStaleDraft() {
    FakeGateway gateway = new FakeGateway();
    gateway.autoResolveBootstrap = false;
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        newController(gateway, view, new FakeFactory(), new ArrayList<String>(), new ArrayList<String>());

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onReplySubmitted("Draft");
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-2", 45L, "BCDE", "b+root"));
    controller.onReplyDraftChanged("Retry draft");
    controller.onReplySubmitted("Retry draft");
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-3", 46L, "CDEF", "b+root"));

    Assert.assertEquals("Retry draft", view.model.getReplyDraft());
    Assert.assertFalse(view.model.isReplySubmitting());
    Assert.assertTrue(view.model.isReplyStaleBasis());
    Assert.assertEquals(
        J2clComposeSurfaceController.STALE_REPLY_MESSAGE, view.model.getReplyErrorText());
    Assert.assertEquals(0, gateway.submitCalls);
  }

  @Test
  public void staleDraftRetryFailureClearsStaleStateAndKeepsDraftEditable() {
    FakeGateway gateway = new FakeGateway();
    gateway.autoResolveBootstrap = false;
    gateway.submitResponse = new SidecarSubmitResponse(1, "server rejected", 45L);
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        newController(gateway, view, new FakeFactory(), new ArrayList<String>(), new ArrayList<String>());

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onReplySubmitted("Draft");
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-2", 45L, "BCDE", "b+root"));
    gateway.autoResolveBootstrap = true;
    controller.onReplyDraftChanged("Edited draft");
    controller.onReplySubmitted("Edited draft");

    Assert.assertEquals("Edited draft", view.model.getReplyDraft());
    Assert.assertFalse(view.model.isReplySubmitting());
    Assert.assertFalse(view.model.isReplyStaleBasis());
    Assert.assertEquals("server rejected", view.model.getReplyErrorText());
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+2", "chan-3", 46L, "CDEF", "b+root"));

    Assert.assertEquals("", view.model.getReplyDraft());
    Assert.assertFalse(view.model.isReplyStaleBasis());
  }

  @Test
  public void staleDraftRetryBootstrapFailureClearsStaleStateAndKeepsDraftEditable() {
    FakeGateway gateway = new FakeGateway();
    gateway.autoResolveBootstrap = false;
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        newController(gateway, view, new FakeFactory(), new ArrayList<String>(), new ArrayList<String>());

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onReplySubmitted("Draft");
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-2", 45L, "BCDE", "b+root"));
    gateway.bootstrapError = "bootstrap unavailable";
    controller.onReplyDraftChanged("Edited draft");
    controller.onReplySubmitted("Edited draft");

    Assert.assertEquals("Edited draft", view.model.getReplyDraft());
    Assert.assertFalse(view.model.isReplySubmitting());
    Assert.assertFalse(view.model.isReplyStaleBasis());
    Assert.assertEquals("bootstrap unavailable", view.model.getReplyErrorText());
    Assert.assertEquals(0, gateway.submitCalls);
  }

  @Test
  public void sameWaveRefreshAfterEditingStaleDraftKeepsDraftWithoutStaleBanner() {
    FakeGateway gateway = new FakeGateway();
    gateway.autoResolveBootstrap = false;
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        newController(gateway, view, new FakeFactory(), new ArrayList<String>(), new ArrayList<String>());

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onReplySubmitted("Draft");
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-2", 45L, "BCDE", "b+root"));
    controller.onReplyDraftChanged("Edited draft");
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-3", 46L, "CDEF", "b+root"));

    Assert.assertEquals("Edited draft", view.model.getReplyDraft());
    Assert.assertFalse(view.model.isReplyStaleBasis());
    Assert.assertEquals("", view.model.getReplyErrorText());
  }

  @Test
  public void signOutWhileReplyStaleClearsStaleMarkersAndError() {
    FakeGateway gateway = new FakeGateway();
    gateway.autoResolveBootstrap = false;
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        newController(gateway, view, new FakeFactory(), new ArrayList<String>(), new ArrayList<String>());

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onReplySubmitted("Draft");
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-2", 45L, "BCDE", "b+root"));
    controller.onSignedOut();

    Assert.assertFalse(view.model.isReplyAvailable());
    Assert.assertFalse(view.model.isReplyStaleBasis());
    Assert.assertEquals("", view.model.getReplyErrorText());
    Assert.assertEquals(
        "Sign in to create or reply in the J2CL root shell.", view.model.getReplyStatusText());
  }

  @Test
  public void bootstrapFailurePreservesDraftAndSurfacesRootSubmitError() {
    FakeGateway gateway = new FakeGateway();
    gateway.bootstrapError = "bootstrap unavailable";
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        newController(gateway, view, new FakeFactory(), new ArrayList<String>(), new ArrayList<String>());

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onReplySubmitted("Draft");

    Assert.assertEquals("Draft", view.model.getReplyDraft());
    Assert.assertEquals("bootstrap unavailable", view.model.getReplyErrorText());
  }

  @Test
  public void successfulReplyClearsDraftAndRefreshesThroughHandoff() {
    FakeGateway gateway = new FakeGateway();
    FakeFactory factory = new FakeFactory();
    FakeView view = new FakeView();
    List<String> refreshed = new ArrayList<String>();
    J2clComposeSurfaceController controller =
        newController(gateway, view, factory, new ArrayList<String>(), refreshed);

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onReplySubmitted("Reply");

    Assert.assertEquals("", view.model.getReplyDraft());
    Assert.assertEquals(Arrays.asList("example.com/w+1"), refreshed);
    Assert.assertEquals("Reply", factory.lastReplyText);
  }

  @Test
  public void replyFailureIsRoutedToNotificationServiceWithExactText() {
    FakeGateway gateway = new FakeGateway();
    gateway.submitError = "Reply rejected by server.";
    FakeView view = new FakeView();
    RecordingNotificationSink sink = new RecordingNotificationSink();
    J2clComposeSurfaceController controller =
        newController(gateway, view, new FakeFactory(), new ArrayList<String>(), new ArrayList<String>());
    controller.setNotificationService(sink);

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onReplySubmitted("Reply");

    // Inline error text is preserved (compat) AND surfaced via the shared service.
    Assert.assertEquals("Reply rejected by server.", view.model.getReplyErrorText());
    Assert.assertEquals("Reply rejected by server.", sink.last().message);
    Assert.assertEquals(J2clNotificationService.Level.ERROR, sink.last().level);
  }

  @Test
  public void noNotificationServiceInjectedDoesNotThrowOnFailure() {
    FakeGateway gateway = new FakeGateway();
    gateway.submitError = "boom";
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        newController(gateway, view, new FakeFactory(), new ArrayList<String>(), new ArrayList<String>());

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onReplySubmitted("Reply");

    // Without a service injected the inline text still works and nothing throws.
    Assert.assertEquals("boom", view.model.getReplyErrorText());
  }
}
