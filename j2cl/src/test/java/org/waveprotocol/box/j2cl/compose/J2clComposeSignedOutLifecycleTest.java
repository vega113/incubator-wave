package org.waveprotocol.box.j2cl.compose;

import com.google.j2cl.junit.apt.J2clTestInput;
import java.util.ArrayList;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.waveprotocol.box.j2cl.search.J2clSidecarWriteSession;
import org.waveprotocol.box.j2cl.testsupport.FakeFactory;
import org.waveprotocol.box.j2cl.testsupport.FakeGateway;
import org.waveprotocol.box.j2cl.testsupport.FakeView;
import org.waveprotocol.box.j2cl.transport.SidecarSubmitResponse;

/**
 * #1270: split from the monster J2clComposeSurfaceControllerTest. Covers
 * signed-out mid-flight create/reply abandonment and stamp-leak guards.
 */
@J2clTestInput(J2clComposeSignedOutLifecycleTest.class)
public class J2clComposeSignedOutLifecycleTest extends J2clComposeControllerTestSupport {

  @Test
  public void signedOutMidFlightCreateAbandonsPendingCallback() {
    FakeGateway gateway = new FakeGateway();
    gateway.autoResolveBootstrap = false;
    FakeView view = new FakeView();
    List<String> created = new ArrayList<String>();
    J2clComposeSurfaceController controller =
        newController(gateway, view, new FakeFactory(), created, new ArrayList<String>());

    controller.start();
    controller.onCreateSubmitted("Hello");
    controller.onSignedOut();
    gateway.resolveBootstrap();

    Assert.assertEquals(0, gateway.submitCalls);
    Assert.assertTrue(created.isEmpty());
    Assert.assertFalse(view.model.isCreateEnabled());
  }

  @Test
  public void signOutAfterCreateFailureClearsCreateError() {
    FakeGateway gateway = new FakeGateway();
    gateway.submitResponse = new SidecarSubmitResponse(1, "server rejected", 45L);
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        newController(gateway, view, new FakeFactory(), new ArrayList<String>(), new ArrayList<String>());

    controller.start();
    controller.onCreateSubmitted("Hello");

    Assert.assertEquals("server rejected", view.model.getCreateErrorText());
    Assert.assertEquals("", view.model.getCreateStatusText());
    controller.onSignedOut();

    Assert.assertFalse(view.model.isCreateEnabled());
    Assert.assertEquals("", view.model.getCreateErrorText());
    Assert.assertEquals("Sign in to create or reply in the J2CL root shell.", view.model.getCreateStatusText());
  }

  @Test
  public void signedOutMidFlightReplyAbandonsPendingCallbackAndClearsStaleState() {
    FakeGateway gateway = new FakeGateway();
    gateway.autoResolveBootstrap = false;
    FakeView view = new FakeView();
    List<String> refreshed = new ArrayList<String>();
    J2clComposeSurfaceController controller =
        newController(gateway, view, new FakeFactory(), new ArrayList<String>(), refreshed);

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onReplySubmitted("Draft");
    controller.onSignedOut();
    gateway.resolveBootstrap();

    Assert.assertEquals(0, gateway.submitCalls);
    Assert.assertTrue(refreshed.isEmpty());
    Assert.assertFalse(view.model.isReplyAvailable());
    Assert.assertFalse(view.model.isReplySubmitting());
    Assert.assertFalse(view.model.isReplyStaleBasis());
    Assert.assertEquals("", view.model.getReplyErrorText());
  }

  @Test
  public void signedOutStateDisablesComposeWithoutFetchingBootstrap() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        newController(gateway, view, new FakeFactory(), new ArrayList<String>(), new ArrayList<String>());

    controller.start();
    controller.onSignedOut();
    controller.onCreateSubmitted("Hello");
    controller.onReplySubmitted("Reply");

    Assert.assertFalse(view.model.isCreateEnabled());
    Assert.assertFalse(view.model.isReplyAvailable());
    Assert.assertEquals("Sign in to create or reply in the J2CL root shell.", view.model.getCreateStatusText());
    Assert.assertEquals(0, gateway.fetchBootstrapCalls);
    Assert.assertEquals(0, gateway.submitCalls);
  }

  // J-UI-3 (#1081, R-5.1) — codex P2 PRRT_kwDOBwxLXs5-DQdB: a sign-out
  // mid-create bumps createGeneration so the deferred submit/bootstrap
  // callbacks are gated out, which means handleCreateFailure (and its
  // failure hook) never fires. onSignedOut must therefore drop the
  // submit-query stamp itself when there was an in-flight create, or
  // the next successful create scopes its optimistic stub to the
  // wrong rail.
  @Test
  public void onSignedOutMidCreateRunsFailureHookSoStampDoesNotLeak() {
    final int[] failureHookFires = {0};
    FakeGateway gateway = new FakeGateway();
    gateway.autoResolveBootstrap = false; // hold the in-flight create.
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        newController(gateway, view, new FakeFactory(),
            new ArrayList<String>(), new ArrayList<String>());
    controller.setCreateFailureHook(() -> failureHookFires[0]++);
    controller.start();
    controller.onCreateSubmittedWithTitle("In-flight title", "body");
    Assert.assertTrue(
        "create must be in flight before sign-out aborts it",
        view.model.isCreateSubmitting());
    int firesBeforeSignOut = failureHookFires[0];

    controller.onSignedOut();

    Assert.assertEquals(
        "sign-out abort must run the failure hook so the stamp is dropped",
        firesBeforeSignOut + 1,
        failureHookFires[0]);
  }

  // J-UI-3 — when no create is in flight, sign-out must NOT call the
  // failure hook (no stamp to drop).
  @Test
  public void onSignedOutWithoutInflightCreateDoesNotRunFailureHook() {
    final int[] failureHookFires = {0};
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        newController(new FakeGateway(), view, new FakeFactory(),
            new ArrayList<String>(), new ArrayList<String>());
    controller.setCreateFailureHook(() -> failureHookFires[0]++);
    controller.start();

    controller.onSignedOut();

    Assert.assertEquals(0, failureHookFires[0]);
  }
}
