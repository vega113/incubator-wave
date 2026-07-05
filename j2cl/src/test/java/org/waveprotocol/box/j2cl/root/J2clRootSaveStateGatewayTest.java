package org.waveprotocol.box.j2cl.root;

import com.google.j2cl.junit.apt.J2clTestInput;
import java.util.ArrayList;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.waveprotocol.box.j2cl.compose.J2clComposeSurfaceController;
import org.waveprotocol.box.j2cl.search.J2clSearchPanelController;
import org.waveprotocol.box.j2cl.transport.SidecarSessionBootstrap;
import org.waveprotocol.box.j2cl.transport.SidecarSubmitRequest;
import org.waveprotocol.box.j2cl.transport.SidecarSubmitResponse;

@J2clTestInput(J2clRootSaveStateGatewayTest.class)
public class J2clRootSaveStateGatewayTest {

  @Test
  public void submitFlipsToSavingThenSavedOnSuccess() {
    FakeGateway delegate = new FakeGateway();
    List<String> transitions = new ArrayList<String>();
    J2clRootSaveStateGateway gateway = new J2clRootSaveStateGateway(delegate, transitions::add);

    gateway.submit(null, null, response -> {}, error -> {});
    Assert.assertEquals("saving", lastOf(transitions));

    delegate.settleSuccess(0);
    Assert.assertEquals("saved", lastOf(transitions));
  }

  @Test
  public void submitFlipsToSavedOnError() {
    FakeGateway delegate = new FakeGateway();
    List<String> transitions = new ArrayList<String>();
    J2clRootSaveStateGateway gateway = new J2clRootSaveStateGateway(delegate, transitions::add);

    gateway.submit(null, null, response -> {}, error -> {});
    Assert.assertEquals("saving", lastOf(transitions));

    delegate.settleError(0, "boom");
    Assert.assertEquals("saved", lastOf(transitions));
  }

  @Test
  public void concurrentSubmitsStaySavingUntilAllSettle() {
    FakeGateway delegate = new FakeGateway();
    List<String> transitions = new ArrayList<String>();
    J2clRootSaveStateGateway gateway = new J2clRootSaveStateGateway(delegate, transitions::add);

    gateway.submit(null, null, response -> {}, error -> {});
    gateway.submit(null, null, response -> {}, error -> {});
    // Only one "saving" transition should have fired (on the first in-flight op).
    Assert.assertEquals(1, count(transitions, "saving"));

    delegate.settleSuccess(0);
    // Still one op in flight — no "saved" yet.
    Assert.assertEquals(0, count(transitions, "saved"));

    delegate.settleSuccess(1);
    Assert.assertEquals("saved", lastOf(transitions));
    Assert.assertEquals(1, count(transitions, "saved"));
  }

  @Test
  public void duplicateCallbackDoesNotUnderflowRefCount() {
    FakeGateway delegate = new FakeGateway();
    List<String> transitions = new ArrayList<String>();
    J2clRootSaveStateGateway gateway = new J2clRootSaveStateGateway(delegate, transitions::add);

    gateway.submit(null, null, response -> {}, error -> {});
    delegate.settleSuccess(0);
    // A misbehaving delegate that fires the callback again must not push the
    // count negative and must not emit a second "saved".
    delegate.settleError(0, "late");

    Assert.assertEquals(1, count(transitions, "saved"));

    // A fresh submit still works correctly.
    gateway.submit(null, null, response -> {}, error -> {});
    Assert.assertEquals(2, count(transitions, "saving"));
  }

  @Test
  public void synchronousDelegateThrowReleasesRefCountAndRethrows() {
    ThrowingGateway delegate = new ThrowingGateway();
    List<String> transitions = new ArrayList<String>();
    J2clRootSaveStateGateway gateway = new J2clRootSaveStateGateway(delegate, transitions::add);

    try {
      gateway.submit(null, null, response -> {}, error -> {});
      Assert.fail("Expected the synchronous delegate failure to propagate.");
    } catch (RuntimeException expected) {
      Assert.assertEquals("submit exploded", expected.getMessage());
    }

    // The chip must not latch on "saving": the ref-count was released.
    Assert.assertEquals("saved", lastOf(transitions));

    // And a subsequent healthy submit still transitions correctly.
    FakeGateway healthy = new FakeGateway();
    List<String> healthyTransitions = new ArrayList<String>();
    J2clRootSaveStateGateway healthyGateway =
        new J2clRootSaveStateGateway(healthy, healthyTransitions::add);
    healthyGateway.submit(null, null, response -> {}, error -> {});
    Assert.assertEquals("saving", lastOf(healthyTransitions));
    healthy.settleSuccess(0);
    Assert.assertEquals("saved", lastOf(healthyTransitions));
  }

  @Test
  public void forwardsSuccessResponseAndErrorToCaller() {
    FakeGateway delegate = new FakeGateway();
    List<String> transitions = new ArrayList<String>();
    J2clRootSaveStateGateway gateway = new J2clRootSaveStateGateway(delegate, transitions::add);
    final int[] successes = {0};
    final List<String> errors = new ArrayList<String>();

    gateway.submit(null, null, response -> successes[0]++, errors::add);
    delegate.settleSuccess(0);
    Assert.assertEquals(1, successes[0]);

    gateway.submit(null, null, response -> successes[0]++, errors::add);
    delegate.settleError(1, "nope");
    Assert.assertEquals(1, errors.size());
    Assert.assertEquals("nope", errors.get(0));
  }

  @Test
  public void fetchRootSessionBootstrapIsDelegatedWithoutSaveTransition() {
    FakeGateway delegate = new FakeGateway();
    List<String> transitions = new ArrayList<String>();
    J2clRootSaveStateGateway gateway = new J2clRootSaveStateGateway(delegate, transitions::add);

    gateway.fetchRootSessionBootstrap(bootstrap -> {}, error -> {});

    Assert.assertEquals(1, delegate.bootstrapCount);
    Assert.assertTrue(transitions.isEmpty());
  }

  @Test
  public void constructorRejectsNullArguments() {
    List<String> transitions = new ArrayList<String>();
    try {
      new J2clRootSaveStateGateway(null, transitions::add);
      Assert.fail("Expected null delegate to be rejected.");
    } catch (IllegalArgumentException expected) {
      Assert.assertEquals("delegate is required", expected.getMessage());
    }
    try {
      new J2clRootSaveStateGateway(new FakeGateway(), null);
      Assert.fail("Expected null sink to be rejected.");
    } catch (IllegalArgumentException expected) {
      Assert.assertEquals("saveStateSink is required", expected.getMessage());
    }
  }

  private static String lastOf(List<String> values) {
    return values.get(values.size() - 1);
  }

  private static int count(List<String> values, String target) {
    int total = 0;
    for (String value : values) {
      if (target.equals(value)) {
        total++;
      }
    }
    return total;
  }

  private static final class FakeGateway implements J2clComposeSurfaceController.Gateway {
    private int bootstrapCount;
    private final List<J2clSearchPanelController.SuccessCallback<SidecarSubmitResponse>> successes =
        new ArrayList<J2clSearchPanelController.SuccessCallback<SidecarSubmitResponse>>();
    private final List<J2clSearchPanelController.ErrorCallback> errors =
        new ArrayList<J2clSearchPanelController.ErrorCallback>();

    @Override
    public void fetchRootSessionBootstrap(
        J2clSearchPanelController.SuccessCallback<SidecarSessionBootstrap> onSuccess,
        J2clSearchPanelController.ErrorCallback onError) {
      bootstrapCount++;
    }

    @Override
    public void submit(
        SidecarSessionBootstrap bootstrap,
        SidecarSubmitRequest request,
        J2clSearchPanelController.SuccessCallback<SidecarSubmitResponse> onSuccess,
        J2clSearchPanelController.ErrorCallback onError) {
      successes.add(onSuccess);
      errors.add(onError);
    }

    private void settleSuccess(int index) {
      successes.get(index).accept(null);
    }

    private void settleError(int index, String message) {
      errors.get(index).accept(message);
    }
  }

  /** Delegate whose {@code submit} throws synchronously, before wiring callbacks. */
  private static final class ThrowingGateway implements J2clComposeSurfaceController.Gateway {
    @Override
    public void fetchRootSessionBootstrap(
        J2clSearchPanelController.SuccessCallback<SidecarSessionBootstrap> onSuccess,
        J2clSearchPanelController.ErrorCallback onError) {}

    @Override
    public void submit(
        SidecarSessionBootstrap bootstrap,
        SidecarSubmitRequest request,
        J2clSearchPanelController.SuccessCallback<SidecarSubmitResponse> onSuccess,
        J2clSearchPanelController.ErrorCallback onError) {
      throw new RuntimeException("submit exploded");
    }
  }
}
