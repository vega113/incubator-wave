package org.waveprotocol.box.j2cl.testsupport;

import org.waveprotocol.box.j2cl.search.J2clPlainTextDeltaFactory;
import org.waveprotocol.box.j2cl.search.J2clSidecarWriteSession;
import org.waveprotocol.box.j2cl.transport.SidecarSubmitRequest;

/**
 * #1270: shared compose test double, moved out of J2clComposeSurfaceControllerTest.
 * Public so the split per-flow test classes in the {@code compose} package can use it.
 */
public final class FakeFactory extends J2clPlainTextDeltaFactory {
  public String lastReplyText;

  public FakeFactory() {
    super("seed");
  }

  @Override
  public CreateWaveRequest createWaveRequest(String address, String text) {
    return new CreateWaveRequest(
        "example.com/w+new",
        new SidecarSubmitRequest("example.com/w+new/~/conv+root", "{\"create\":true}", null));
  }

  @Override
  public SidecarSubmitRequest createReplyRequest(
      String address, J2clSidecarWriteSession session, String text) {
    lastReplyText = text;
    return new SidecarSubmitRequest(
        session.getSelectedWaveId() + "/~/conv+root", "{\"reply\":true}", session.getChannelId());
  }
}
