package org.waveprotocol.box.j2cl.compose;

import com.google.j2cl.junit.apt.J2clTestInput;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.waveprotocol.box.j2cl.search.J2clSidecarWriteSession;
import org.waveprotocol.box.j2cl.transport.SidecarSubmitRequest;

/**
 * #1270: split out of the J2clComposeSurfaceControllerTest monster class — the
 * rich-text component / annotation reply-submit path (J-UI-5, #1083). Uses the
 * shared package doubles in {@link ComposeTestDoubles}.
 */
@J2clTestInput(J2clComposeRichComponentTest.class)
public class J2clComposeRichComponentTest {

  // J-UI-5 (#1083, R-5.1 + R-5.7): the inline rich-text composer
  // forwards a per-fragment component list on reply submit. The
  // controller must build the J2clComposerDocument from those
  // components (preserving per-fragment formatting) instead of
  // collapsing the whole draft to a single annotation.
  @Test
  public void onReplySubmittedWithComponentsBuildsAnnotatedDeltaFromComponents() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            waveId -> {},
            waveId -> {});
    controller.start();
    openWaveForReply(controller);

    List<J2clComposeSurfaceController.SubmittedComponent> components = new ArrayList<>();
    components.add(J2clComposeSurfaceController.SubmittedComponent.text("plain "));
    components.add(
        J2clComposeSurfaceController.SubmittedComponent.annotated(
            "strong-bit", "fontWeight", "bold"));
    components.add(J2clComposeSurfaceController.SubmittedComponent.text(" tail"));

    controller.onReplySubmittedWithComponents(components);

    String delta = gateway.lastSubmitRequest.getDeltaJson();
    assertContains(delta, "\"2\":\"plain \"");
    assertContains(delta, "{\"1\":{\"3\":[{\"1\":\"fontWeight\",\"3\":\"bold\"}]}}");
    assertContains(delta, "\"2\":\"strong-bit\"");
    assertContains(delta, "{\"1\":{\"2\":[\"fontWeight\"]}}");
    assertContains(delta, "\"2\":\" tail\"");
  }

  // J-UI-5 (#1083): a successful component-driven submit clears the
  // pending list so a subsequent plain-text submit (e.g. via the
  // legacy textarea) does not re-emit the bold annotation.
  @Test
  public void componentSubmitSuccessClearsPendingComponentsBeforeNextSubmit() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            waveId -> {},
            waveId -> {});
    controller.start();
    openWaveForReply(controller);

    List<J2clComposeSurfaceController.SubmittedComponent> firstSubmit = new ArrayList<>();
    firstSubmit.add(
        J2clComposeSurfaceController.SubmittedComponent.annotated("hi", "fontWeight", "bold"));
    controller.onReplySubmittedWithComponents(firstSubmit);
    assertContains(gateway.lastSubmitRequest.getDeltaJson(), "fontWeight");

    // Mimic a wave reselect to clear the reply draft, then submit
    // again as plain text — should NOT carry fontWeight.
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 46L, "WXYZ", "b+root"));
    controller.onReplySubmitted("plain again");

    Assert.assertFalse(
        "Plain-text submit must not re-emit prior fontWeight annotation",
        gateway.lastSubmitRequest.getDeltaJson().contains("fontWeight"));
  }

  // J-UI-5 (#1083, codex review #1095 thread PRRT_kwDOBwxLXs5-C84a):
  // a SubmittedComponent carrying two annotations (e.g. fontStyle +
  // fontWeight) must serialise as a single chars op bracketed by both
  // annotation start/end pairs in well-nested order, so combined
  // bold+italic round-trips.
  @Test
  public void onReplySubmittedWithComponentsEmitsNestedAnnotationsForCombinedRun() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            waveId -> {},
            waveId -> {});
    controller.start();
    openWaveForReply(controller);

    List<J2clComposeSurfaceController.SubmittedComponent.Annotation> ann = new ArrayList<>();
    ann.add(
        new J2clComposeSurfaceController.SubmittedComponent.Annotation(
            "fontStyle", "italic"));
    ann.add(
        new J2clComposeSurfaceController.SubmittedComponent.Annotation(
            "fontWeight", "bold"));
    List<J2clComposeSurfaceController.SubmittedComponent> components = new ArrayList<>();
    components.add(
        J2clComposeSurfaceController.SubmittedComponent.annotatedMulti("combined", ann));

    controller.onReplySubmittedWithComponents(components);

    String delta = gateway.lastSubmitRequest.getDeltaJson();
    // Both annotation starts AND both annotation ends, with chars in
    // between. The exact order of starts is fontStyle then fontWeight
    // (declaration order); ends are reversed.
    int italicStart = delta.indexOf("\"1\":\"fontStyle\",\"3\":\"italic\"");
    int boldStart = delta.indexOf("\"1\":\"fontWeight\",\"3\":\"bold\"");
    int chars = delta.indexOf("\"2\":\"combined\"");
    int boldEnd = delta.indexOf("[\"fontWeight\"]");
    int italicEnd = delta.indexOf("[\"fontStyle\"]");
    Assert.assertTrue("italic start present", italicStart >= 0);
    Assert.assertTrue("bold start present", boldStart >= 0);
    Assert.assertTrue("chars present", chars >= 0);
    Assert.assertTrue("bold end present", boldEnd >= 0);
    Assert.assertTrue("italic end present", italicEnd >= 0);
    Assert.assertTrue("italic opens before bold (declaration order)", italicStart < boldStart);
    Assert.assertTrue("bold opens before chars", boldStart < chars);
    Assert.assertTrue("chars before bold close", chars < boldEnd);
    Assert.assertTrue("bold closes before italic (well-nested)", boldEnd < italicEnd);
  }

  // J-UI-5 (#1083, codex review #1095 threads PRRT_kwDOBwxLXs5-F-8o
  // + PRRT_kwDOBwxLXs5-NyZ7): multi-annotation runs that re-use a
  // SPACE-COMBINABLE key (today: `textDecoration`) merge their
  // values into one space-separated token list — both decorations
  // survive submit/reload (CSS allows
  // `text-decoration: underline line-through`). Only ONE
  // `annotation_start` / `annotation_end` pair reaches the delta
  // because the builder collapsed the duplicates upstream.
  @Test
  public void onReplySubmittedWithComponentsMergesSpaceCombinableAnnotations() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            waveId -> {},
            waveId -> {});
    controller.start();
    openWaveForReply(controller);

    List<J2clComposeSurfaceController.SubmittedComponent.Annotation> ann = new ArrayList<>();
    ann.add(
        new J2clComposeSurfaceController.SubmittedComponent.Annotation(
            "textDecoration", "underline"));
    ann.add(
        new J2clComposeSurfaceController.SubmittedComponent.Annotation(
            "textDecoration", "line-through"));
    List<J2clComposeSurfaceController.SubmittedComponent> components = new ArrayList<>();
    components.add(
        J2clComposeSurfaceController.SubmittedComponent.annotatedMulti("decorated", ann));

    controller.onReplySubmittedWithComponents(components);

    String delta = gateway.lastSubmitRequest.getDeltaJson();
    int mergedStart = delta.indexOf("\"1\":\"textDecoration\",\"3\":\"underline line-through\"");
    int decorationCloses = delta.split("\\[\"textDecoration\"\\]", -1).length - 1;
    int decorationStarts =
        delta.split("\"1\":\"textDecoration\",\"3\":", -1).length - 1;
    // Both decoration values reach the delta as a space-separated
    // token list on a single annotation_start; the close pair is
    // emitted exactly once because the builder collapsed the
    // duplicates upstream.
    Assert.assertTrue(
        "merged textDecoration value preserves both tokens",
        mergedStart >= 0);
    Assert.assertEquals(
        "exactly one textDecoration start emitted",
        1,
        decorationStarts);
    Assert.assertEquals(
        "exactly one textDecoration close emitted",
        1,
        decorationCloses);
  }

  // Non-combinable duplicate keys (e.g. two `fontWeight` entries) keep
  // the last-wins fallback — CSS does not allow two simultaneous
  // font-weight values on the same span, so collapsing is the
  // correct semantics.
  @Test
  public void onReplySubmittedWithComponentsLastWinsForNonCombinableKeys() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            waveId -> {},
            waveId -> {});
    controller.start();
    openWaveForReply(controller);

    List<J2clComposeSurfaceController.SubmittedComponent.Annotation> ann = new ArrayList<>();
    ann.add(
        new J2clComposeSurfaceController.SubmittedComponent.Annotation(
            "fontWeight", "normal"));
    ann.add(
        new J2clComposeSurfaceController.SubmittedComponent.Annotation(
            "fontWeight", "bold"));
    List<J2clComposeSurfaceController.SubmittedComponent> components = new ArrayList<>();
    components.add(
        J2clComposeSurfaceController.SubmittedComponent.annotatedMulti("weighted", ann));

    controller.onReplySubmittedWithComponents(components);

    String delta = gateway.lastSubmitRequest.getDeltaJson();
    Assert.assertEquals(
        "no duplicate fontWeight=normal start emitted",
        -1,
        delta.indexOf("\"1\":\"fontWeight\",\"3\":\"normal\""));
    Assert.assertTrue(
        "last-wins fontWeight=bold reaches the delta",
        delta.indexOf("\"1\":\"fontWeight\",\"3\":\"bold\"") >= 0);
  }

  // J-UI-5 (#1083): an annotated component whose text is whitespace-only
  // (a common user flow: bolding a word together with its trailing
  // space) must not throw; the controller downgrades it to a plain
  // text run rather than letting J2clComposerDocument.Builder reject
  // the empty-trim text and tear down the reply path.
  @Test
  public void onReplySubmittedWithComponentsDowngradesWhitespaceAnnotatedToText() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            J2clComposeSurfaceController.richContentDeltaFactory("seed"),
            waveId -> {},
            waveId -> {});
    controller.start();
    openWaveForReply(controller);

    List<J2clComposeSurfaceController.SubmittedComponent> components = new ArrayList<>();
    components.add(J2clComposeSurfaceController.SubmittedComponent.text("hi"));
    components.add(
        J2clComposeSurfaceController.SubmittedComponent.annotated(
            " ", "fontWeight", "bold"));
    components.add(J2clComposeSurfaceController.SubmittedComponent.text("there"));

    controller.onReplySubmittedWithComponents(components);

    // The space run is emitted as plain text — no annotation start
    // wrapping a whitespace-only payload (which the builder would
    // reject as "Missing annotated text.").
    String delta = gateway.lastSubmitRequest.getDeltaJson();
    assertContains(delta, "\"2\":\"hi\"");
    assertContains(delta, "\"2\":\" \"");
    assertContains(delta, "\"2\":\"there\"");
  }

  private static void openWaveForReply(J2clComposeSurfaceController controller) {
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root"));
  }

  private static void assertContains(String value, String... expectedSubstrings) {
    for (String expectedSubstring : expectedSubstrings) {
      Assert.assertTrue(
          "Expected to find <" + expectedSubstring + "> in <" + value + ">",
          value.contains(expectedSubstring));
    }
  }
}
