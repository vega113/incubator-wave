package org.waveprotocol.box.j2cl.compose;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import org.junit.Assert;
import org.waveprotocol.box.j2cl.attachment.J2clAttachmentComposerController;
import org.waveprotocol.box.j2cl.attachment.J2clAttachmentIdGenerator;
import org.waveprotocol.box.j2cl.attachment.J2clAttachmentUploadClient;
import org.waveprotocol.box.j2cl.search.J2clSidecarWriteSession;
import org.waveprotocol.box.j2cl.telemetry.J2clClientTelemetry;
import org.waveprotocol.box.j2cl.testsupport.FakeAttachmentTransport;
import org.waveprotocol.box.j2cl.testsupport.FakeFactory;
import org.waveprotocol.box.j2cl.testsupport.FakeGateway;
import org.waveprotocol.box.j2cl.testsupport.FakeView;

/**
 * #1270: shared scaffolding for the split J2clComposeSurfaceController test
 * classes (formerly all in one ~5k-line file). Holds the controller builders and
 * common assertions; the stateless doubles live in the testsupport package.
 * Package-private so the per-flow J2clCompose*Test subclasses can reuse them.
 */
abstract class J2clComposeControllerTestSupport {

  static final int TEST_BODY_ITEM_COUNT = 17;

  static J2clComposeSurfaceController newController(
      FakeGateway gateway,
      FakeView view,
      FakeFactory factory,
      List<String> created,
      List<String> refreshed) {
    return new J2clComposeSurfaceController(
        gateway, view, factory, created::add, refreshed::add);
  }

  static int countOccurrences(String haystack, String needle) {
    int count = 0;
    int from = 0;
    while (true) {
      int idx = haystack.indexOf(needle, from);
      if (idx < 0) return count;
      count++;
      from = idx + needle.length();
    }
  }

  static J2clComposeSurfaceController newControllerWithTelemetry(
      FakeView view, J2clClientTelemetry.Sink telemetrySink) {
    return new J2clComposeSurfaceController(
        new FakeGateway(),
        view,
        J2clComposeSurfaceController.richContentDeltaFactory("seed"),
        waveId -> { },
        waveId -> { },
        telemetrySink);
  }

  static void openWaveForReply(J2clComposeSurfaceController controller) {
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
  }

  static J2clSidecarWriteSession writeSessionWithReplyTargets() {
    return new J2clSidecarWriteSession(
        "example.com/w+1",
        "chan-1",
        44L,
        "ABCD",
        "b+root",
        Arrays.asList("user@example.com"),
        9,
        12,
        replyPositions("b+root", 9, "b+child", 6));
  }

  static java.util.Map<String, Integer> replyPositions(
      String firstBlipId, int firstPosition, String secondBlipId, int secondPosition) {
    java.util.Map<String, Integer> positions = new java.util.LinkedHashMap<String, Integer>();
    positions.put(firstBlipId, Integer.valueOf(firstPosition));
    positions.put(secondBlipId, Integer.valueOf(secondPosition));
    return positions;
  }

  static J2clComposeSurfaceController.AttachmentControllerFactory
      testAttachmentControllerFactory(FakeAttachmentTransport transport) {
    return (waveRef, domain, insertionCallback, stateChangeCallback) ->
        new J2clAttachmentComposerController(
            waveRef,
            new J2clAttachmentUploadClient(transport),
            new J2clAttachmentIdGenerator(domain, "seed"),
            insertionCallback,
            stateChangeCallback);
  }

  static void assertFactoryRejectsDomain(
      J2clComposeSurfaceController.AttachmentControllerFactory factory, String domain) {
    try {
      factory.create(
          "example.com/w+invalid/~/conv+root",
          domain,
          (document, insertion) -> { },
          () -> { });
      Assert.fail("Expected invalid domain to fail.");
    } catch (IllegalArgumentException expected) {
      Assert.assertTrue(expected.getMessage().contains("domain"));
    }
  }

  static final class CountingUploadClientFactory
      implements J2clComposeSurfaceController.AttachmentUploadClientFactory {
    // package-private: read by the split J2clComposeAttachmentUploadTest subclass.
    int createCalls;

    @Override
    public J2clAttachmentUploadClient create() {
      createCalls++;
      return new J2clAttachmentUploadClient(new FakeAttachmentTransport());
    }
  }

  static J2clAttachmentUploadClient uploadClient(
      J2clAttachmentComposerController controller) throws Exception {
    Field field = J2clAttachmentComposerController.class.getDeclaredField("uploadClient");
    field.setAccessible(true);
    return (J2clAttachmentUploadClient) field.get(controller);
  }

  static void assertContains(String value, String... expectedSubstrings) {
    for (String expectedSubstring : expectedSubstrings) {
      Assert.assertTrue(
          "Expected to find <" + expectedSubstring + "> in <" + value + ">",
          value.contains(expectedSubstring));
    }
  }
}
