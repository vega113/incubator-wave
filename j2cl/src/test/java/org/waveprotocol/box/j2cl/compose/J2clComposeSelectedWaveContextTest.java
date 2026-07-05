package org.waveprotocol.box.j2cl.compose;

import com.google.j2cl.junit.apt.J2clTestInput;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Assert;
import org.junit.Test;
import org.waveprotocol.box.j2cl.search.J2clSidecarWriteSession;
import org.waveprotocol.box.j2cl.testsupport.FakeFactory;
import org.waveprotocol.box.j2cl.testsupport.FakeGateway;
import org.waveprotocol.box.j2cl.testsupport.FakeView;

/**
 * #1270: split from the monster J2clComposeSurfaceControllerTest. Covers
 * selected-wave participant hydration/clearing and write-session context matching.
 */
@J2clTestInput(J2clComposeSelectedWaveContextTest.class)
public class J2clComposeSelectedWaveContextTest extends J2clComposeControllerTestSupport {

  @Test
  public void selectedWaveParticipantsRenderBeforeWriteSessionReady() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        newController(
            gateway, view, new FakeFactory(), new ArrayList<String>(), new ArrayList<String>());

    controller.start();
    controller.onSelectedWaveComposeContextChanged(
        "example.com/w+1", null, Arrays.asList("alice@example.com", "bob@example.com"));

    Assert.assertTrue(view.model.isReplyAvailable());
    Assert.assertEquals("", view.model.getReplyStatusText());
    Assert.assertEquals(
        Arrays.asList("alice@example.com", "bob@example.com"),
        view.model.getParticipantAddresses());
    controller.onReplySubmitted("Draft");
    Assert.assertEquals(
        J2clComposeSurfaceController.WAITING_FOR_WRITE_SESSION_REPLY_MESSAGE,
        view.model.getReplyErrorText());
    Assert.assertEquals(0, gateway.fetchBootstrapCalls);
  }

  @Test
  public void listenerExposesCurrentParticipantsToFreshlyMountedInlineComposer() {
    // Regression for the @-mention popover failing on the wave-root /
    // toolbar-Reply / per-blip continuation surfaces. When the inline
    // <wavy-composer> mounts via openInlineComposer, the view used to
    // pull participants off the legacy <composer-inline-reply> expando.
    // That expando is empty until the controller's first participants-
    // bearing render(), so a user clicking Reply and immediately typing
    // `@` saw an empty popover. The view now reads from this listener
    // method, which returns the controller's authoritative selection.
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        newController(new FakeGateway(), view, new FakeFactory(), new ArrayList<String>(), new ArrayList<String>());

    controller.start();
    controller.onSelectedWaveComposeContextChanged(
        "example.com/w+1", null, Arrays.asList("alice@example.com", "bob@example.com"));

    Assert.assertEquals(
        Arrays.asList("alice@example.com", "bob@example.com"),
        view.listener.getCurrentParticipantAddresses());
  }

  @Test
  public void selectedWaveParticipantsClearOnDifferentSelectedWave() {
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        newController(new FakeGateway(), view, new FakeFactory(), new ArrayList<String>(), new ArrayList<String>());

    controller.start();
    controller.onSelectedWaveComposeContextChanged(
        "example.com/w+1", null, Arrays.asList("alice@example.com"));
    controller.onSelectedWaveComposeContextChanged(
        "example.com/w+2", null, Collections.<String>emptyList());

    Assert.assertTrue(view.model.getParticipantAddresses().isEmpty());
  }

  @Test
  public void selectedWaveParticipantsClearOnSignOut() {
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        newController(new FakeGateway(), view, new FakeFactory(), new ArrayList<String>(), new ArrayList<String>());

    controller.start();
    controller.onSelectedWaveComposeContextChanged(
        "example.com/w+1", null, Arrays.asList("alice@example.com"));
    controller.onSignedOut();

    Assert.assertTrue(view.model.getParticipantAddresses().isEmpty());
    Assert.assertFalse(view.model.isReplyAvailable());
  }

  @Test
  public void sameWaveEmptyParticipantReconnectPreservesExistingParticipants() {
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        newController(new FakeGateway(), view, new FakeFactory(), new ArrayList<String>(), new ArrayList<String>());

    controller.start();
    controller.onSelectedWaveComposeContextChanged(
        "example.com/w+1", null, Arrays.asList("alice@example.com", "bob@example.com"));
    controller.onSelectedWaveComposeContextChanged(
        "example.com/w+1", null, Collections.<String>emptyList());

    Assert.assertEquals(
        Arrays.asList("alice@example.com", "bob@example.com"),
        view.model.getParticipantAddresses());
    Assert.assertTrue(view.model.isReplyAvailable());
  }

  @Test
  public void selectedWaveParticipantsRemainAvailableWhenWriteSessionHydrates() {
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        newController(new FakeGateway(), view, new FakeFactory(), new ArrayList<String>(), new ArrayList<String>());

    controller.start();
    controller.onSelectedWaveComposeContextChanged(
        "example.com/w+1", null, Arrays.asList("alice@example.com", "bob@example.com"));
    Assert.assertTrue(view.model.isReplyAvailable());
    controller.onSelectedWaveComposeContextChanged(
        "example.com/w+1",
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"),
        Collections.<String>emptyList());

    Assert.assertTrue(view.model.isReplyAvailable());
    Assert.assertEquals("b+root", view.model.getReplyTargetLabel());
    Assert.assertEquals(
        Arrays.asList("alice@example.com", "bob@example.com"),
        view.model.getParticipantAddresses());
  }

  @Test
  public void selectedWaveContextRejectsMismatchedWriteSession() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        newController(gateway, view, new FakeFactory(), new ArrayList<String>(), new ArrayList<String>());

    controller.start();
    controller.onSelectedWaveComposeContextChanged(
        "example.com/w+1",
        new J2clSidecarWriteSession(
            "example.com/w+2",
            "chan-2",
            45L,
            "BCDE",
            "b+root",
            Arrays.asList("carol@example.com")),
        Arrays.asList("alice@example.com"));

    Assert.assertTrue(view.model.isReplyAvailable());
    Assert.assertEquals(
        Arrays.asList("alice@example.com"), view.model.getParticipantAddresses());
    Assert.assertEquals("", view.model.getReplyTargetLabel());
    controller.onReplySubmitted("Draft");

    Assert.assertEquals(
        J2clComposeSurfaceController.WAITING_FOR_WRITE_SESSION_REPLY_MESSAGE,
        view.model.getReplyErrorText());
    Assert.assertEquals(0, gateway.fetchBootstrapCalls);
  }

  @Test
  public void selectedWaveContextChangeFromNoWaveClearsPreviousDraft() {
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        newController(new FakeGateway(), view, new FakeFactory(), new ArrayList<String>(), new ArrayList<String>());

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
    controller.onReplyDraftChanged("Draft from first wave");
    controller.onSelectedWaveComposeContextChanged(
        null, null, Collections.<String>emptyList());

    Assert.assertTrue(view.model.getParticipantAddresses().isEmpty());
    Assert.assertFalse(view.model.isReplyAvailable());

    controller.onSelectedWaveComposeContextChanged(
        "example.com/w+2", null, Arrays.asList("alice@example.com"));

    Assert.assertEquals("", view.model.getReplyDraft());
  }

  @Test
  public void selectedWaveContextReselectionAfterClearClearsPreviousDraft() {
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        newController(new FakeGateway(), view, new FakeFactory(), new ArrayList<String>(), new ArrayList<String>());

    controller.start();
    controller.onSelectedWaveComposeContextChanged(
        "example.com/w+1", null, Arrays.asList("alice@example.com"));
    controller.onReplyDraftChanged("Draft from cleared selection");
    controller.onSelectedWaveComposeContextChanged(
        null, null, Collections.<String>emptyList());
    controller.onSelectedWaveComposeContextChanged(
        "example.com/w+1", null, Arrays.asList("alice@example.com"));

    Assert.assertEquals("", view.model.getReplyDraft());
  }

  @Test
  public void divergentWriteSessionUsesWriteSessionParticipants() {
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        newController(new FakeGateway(), view, new FakeFactory(), new ArrayList<String>(), new ArrayList<String>());

    controller.start();
    controller.onSelectedWaveComposeContextChanged(
        "example.com/w+1", null, Arrays.asList("alice@example.com"));
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession(
            "example.com/w+2",
            "chan-2",
            45L,
            "BCDE",
            "b+root",
            Arrays.asList("carol@example.com")));

    Assert.assertEquals(
        Arrays.asList("carol@example.com"),
        view.model.getParticipantAddresses());
  }
}
