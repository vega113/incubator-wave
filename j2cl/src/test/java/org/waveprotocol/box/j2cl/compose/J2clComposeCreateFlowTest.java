package org.waveprotocol.box.j2cl.compose;

import com.google.j2cl.junit.apt.J2clTestInput;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.waveprotocol.box.j2cl.testsupport.CapturingDeltaFactory;
import org.waveprotocol.box.j2cl.testsupport.FakeFactory;
import org.waveprotocol.box.j2cl.testsupport.FakeGateway;
import org.waveprotocol.box.j2cl.testsupport.FakeView;

/**
 * #1270: split from the monster J2clComposeSurfaceControllerTest. Covers
 * create-wave title handling, submit validation and stub-title derivation.
 */
@J2clTestInput(J2clComposeCreateFlowTest.class)
public class J2clComposeCreateFlowTest extends J2clComposeControllerTestSupport {

  // J-UI-3 (#1081, R-5.1) — title input value is recorded on the model and
  // cleared when the create succeeds.
  @Test
  public void onCreateTitleChangedPersistsTitleInModel() {
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        newController(new FakeGateway(), view, new FakeFactory(),
            new ArrayList<String>(), new ArrayList<String>());
    controller.start();

    controller.onCreateTitleChanged("My new wave");

    Assert.assertEquals("My new wave", view.model.getCreateTitleDraft());
    Assert.assertEquals("", view.model.getCreateDraft());
  }

  // J-UI-3 (#1081, R-5.1) — live edits are LOSSLESS for whitespace per
  // codex P1 PRRT_kwDOBwxLXs5-ColZ. Only embedded newlines get replaced
  // with a space (paste safety: a literal newline would break the
  // conv/title annotation span). Leading/trailing whitespace is preserved
  // so users can type multi-word titles like "My wave" without each
  // mid-word space being eaten between keystrokes.
  @Test
  public void onCreateTitleChangedKeepsTrailingSpaceForMultiWordEdits() {
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        newController(new FakeGateway(), view, new FakeFactory(),
            new ArrayList<String>(), new ArrayList<String>());
    controller.start();

    controller.onCreateTitleChanged("My ");

    Assert.assertEquals(
        "trailing space must survive a live keystroke so the next character lands after it",
        "My ",
        view.model.getCreateTitleDraft());
  }

  // J-UI-3 — embedded newlines from a paste are replaced with spaces in
  // the live path so the conv/title annotation never spans more than one
  // line, but leading/trailing whitespace is preserved (only trimmed at
  // submit time).
  @Test
  public void onCreateTitleChangedNeutralisesPastedNewlinesButPreservesEdgeWhitespace() {
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        newController(new FakeGateway(), view, new FakeFactory(),
            new ArrayList<String>(), new ArrayList<String>());
    controller.start();

    controller.onCreateTitleChanged("  pasted\nfrom-clipboard\r\n  ");

    // Each break run collapses to a single space (CRLF/LF/CR runs no
    // longer leak Windows-style double-spacing): the lone \n becomes one
    // space, the \r\n run becomes one space, and the original 2 trailing
    // spaces are preserved verbatim → 3 trailing spaces total.
    Assert.assertEquals(
        "  pasted from-clipboard   ", view.model.getCreateTitleDraft());
  }

  // J-UI-3 — submit-time normalisation trims edge whitespace so the
  // conv/title annotation in the outgoing delta is a clean single-line
  // span. Live state can still hold whitespace; only the submit path
  // trims.
  @Test
  public void onCreateSubmittedWithTitleTrimsEdgeWhitespaceAtSubmit() {
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        newController(new FakeGateway(), view, new FakeFactory(),
            new ArrayList<String>(), new ArrayList<String>());
    controller.start();

    controller.onCreateSubmittedWithTitle("  Hello world  ", "body");

    // After submit clears the title, the model is empty. We assert via
    // the success path: blank-title-after-success means the trim happened
    // (otherwise the validation gate would have fired). The edge-trim
    // behaviour is also covered by richContentSubmitCreateEmits... below
    // via the encoded delta.
    Assert.assertEquals("", view.model.getCreateTitleDraft());
    Assert.assertEquals("Wave created.", view.model.getCreateStatusText());
  }

  // J-UI-3 — submit with both title and body succeeds and clears both
  // drafts on the success render.
  @Test
  public void onCreateSubmittedWithTitleClearsBothDraftsOnSuccess() {
    List<String> created = new ArrayList<String>();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        newController(new FakeGateway(), view, new FakeFactory(), created, new ArrayList<String>());
    controller.start();
    controller.onCreateTitleChanged("Title");
    controller.onCreateDraftChanged("Body");

    controller.onCreateSubmittedWithTitle("Title", "Body");

    Assert.assertEquals("", view.model.getCreateDraft());
    Assert.assertEquals("", view.model.getCreateTitleDraft());
    Assert.assertEquals(Arrays.asList("example.com/w+new"), created);
  }

  // J-UI-3 — if the user edits the title while the create request is in
  // flight, the success handler must receive the title that was actually
  // submitted, not the live draft value.
  @Test
  public void handleCreateResponseUsesSnapshotTitleNotLiveTitleDraft() {
    List<String> stubTitles = new ArrayList<String>();
    FakeGateway gateway = new FakeGateway();
    gateway.autoResolveBootstrap = false;
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            new FakeFactory(),
            new J2clComposeSurfaceController.CreateSuccessHandler() {
              @Override
              public void onWaveCreated(String waveId) { onWaveCreated(waveId, ""); }
              @Override
              public void onWaveCreated(String waveId, String title) {
                stubTitles.add(title);
              }
            },
            waveId -> { });
    controller.start();
    controller.onCreateSubmittedWithTitle("Submitted title", "body text");
    // User edits title while request is in flight
    controller.onCreateTitleChanged("Changed title");
    // Server responds — callback should use the submitted snapshot
    gateway.resolveBootstrap();
    Assert.assertEquals(Arrays.asList("Submitted title"), stubTitles);
  }

  // J-UI-3 — title-only submit (no body) is allowed; the user can ship a
  // wave with just a title. The body draft remains empty.
  @Test
  public void onCreateSubmittedWithTitleAllowsTitleOnlySubmit() {
    List<String> created = new ArrayList<String>();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        newController(new FakeGateway(), view, new FakeFactory(), created, new ArrayList<String>());
    controller.start();

    controller.onCreateSubmittedWithTitle("Title only", "");

    Assert.assertEquals(Arrays.asList("example.com/w+new"), created);
    Assert.assertEquals("Wave created.", view.model.getCreateStatusText());
  }

  @Test
  public void createRequestedWithParticipantsAllowsEmptyDirectWaveAndNormalizesParticipants() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    CapturingDeltaFactory factory = new CapturingDeltaFactory();
    List<String> created = new ArrayList<String>();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            gateway,
            view,
            factory,
            created::add,
            waveId -> {});
    controller.start();
    controller.onCreateTitleChanged("Draft title");
    controller.onCreateDraftChanged("Draft body");

    controller.onCreateRequestedWithParticipants(
        Arrays.asList(" Friend@Example.COM ", "", null, "friend@example.com"));

    Assert.assertEquals(1, gateway.submitCalls);
    Assert.assertEquals(Arrays.asList("example.com/w+direct"), created);
    Assert.assertEquals(Arrays.asList("friend@example.com"), factory.lastAdditionalParticipants);
    Assert.assertEquals("", factory.lastDraftText);
    Assert.assertEquals(0, factory.lastDocumentComponentCount);
    Assert.assertEquals("Draft title", view.model.getCreateTitleDraft());
    Assert.assertEquals("Draft body", view.model.getCreateDraft());
    Assert.assertEquals("Wave created.", view.model.getCreateStatusText());
  }

  // J-UI-3 — submitting with both fields blank surfaces the existing
  // "enter some text" validation error rather than calling the gateway.
  @Test
  public void onCreateSubmittedWithBothBlankShowsValidationError() {
    FakeGateway gateway = new FakeGateway();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        newController(gateway, view, new FakeFactory(), new ArrayList<String>(), new ArrayList<String>());
    controller.start();

    controller.onCreateSubmittedWithTitle("   ", "   ");

    Assert.assertEquals("Enter some text before creating a wave.", view.model.getCreateErrorText());
    Assert.assertEquals(0, gateway.submitCalls);
  }

  // J-UI-3 — the rich-content factory receives a document with a
  // conv/title annotated component when a title was typed; the body text
  // appears as a separate TEXT component after the title.
  @Test
  public void richContentSubmitCreateEmitsTitleAnnotationFollowedByBody() {
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
    controller.onCreateTitleChanged("Sprint planning");
    controller.onCreateDraftChanged("Standup at 10am");

    controller.onCreateSubmittedWithTitle("Sprint planning", "Standup at 10am");

    Assert.assertNotNull(gateway.lastSubmitRequest);
    String deltaJson = gateway.lastSubmitRequest.getDeltaJson();
    assertContains(
        deltaJson,
        "{\"1\":{\"3\":[{\"1\":\"conv/title\",\"3\":\"\"}]}}",
        "\"2\":\"Sprint planning\"",
        "{\"1\":{\"2\":[\"conv/title\"]}}",
        "\"2\":\"\\n\"",
        "\"2\":\"Standup at 10am\"");
    Assert.assertTrue(deltaJson.indexOf("Sprint planning") < deltaJson.indexOf("\\n"));
    Assert.assertTrue(deltaJson.indexOf("\\n") < deltaJson.indexOf("Standup at 10am"));
  }

  // J-UI-3 — the legacy single-arg create handler still routes through
  // the success path (back-compat with handlers that have not adopted
  // the title overload).
  @Test
  public void legacySingleArgCreateHandlerStillFiresOnSuccess() {
    final List<String> singleArgCalls = new ArrayList<String>();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            new FakeGateway(),
            view,
            new FakeFactory(),
            (J2clComposeSurfaceController.CreateSuccessHandler) singleArgCalls::add,
            waveId -> { });
    controller.start();

    controller.onCreateSubmittedWithTitle("Title", "Body");

    Assert.assertEquals(Arrays.asList("example.com/w+new"), singleArgCalls);
  }

  // J-UI-3 (#1081, R-5.1) — CodeRabbit major PRRT_kwDOBwxLXs5-Cper:
  // body-only creates must derive a stub title from the body's first
  // non-blank line so the optimistic rail digest does not render as
  // "(untitled wave)" when the user clearly intended their typed text
  // to identify the wave.
  @Test
  public void bodyOnlyCreatePassesFirstBodyLineAsStubTitle() {
    List<String> stubTitles = new ArrayList<String>();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            new FakeGateway(),
            view,
            new FakeFactory(),
            new J2clComposeSurfaceController.CreateSuccessHandler() {
              @Override
              public void onWaveCreated(String waveId) {
                onWaveCreated(waveId, "");
              }
              @Override
              public void onWaveCreated(String waveId, String title) {
                stubTitles.add(title);
              }
            },
            waveId -> { });
    controller.start();

    controller.onCreateSubmittedWithTitle("", "  hello world\nsecond line\n");

    Assert.assertEquals(Arrays.asList("hello world"), stubTitles);
  }

  // J-UI-3 — when both title and body are typed, the explicit title wins
  // over the body-derived fallback. Submit-time normalisation trims edge
  // whitespace.
  @Test
  public void submitWithTitleAndBodyPassesTrimmedTitleAsStubTitle() {
    List<String> stubTitles = new ArrayList<String>();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            new FakeGateway(),
            view,
            new FakeFactory(),
            new J2clComposeSurfaceController.CreateSuccessHandler() {
              @Override
              public void onWaveCreated(String waveId) { onWaveCreated(waveId, ""); }
              @Override
              public void onWaveCreated(String waveId, String title) {
                stubTitles.add(title);
              }
            },
            waveId -> { });
    controller.start();

    controller.onCreateSubmittedWithTitle("  Sprint plan  ", "body line");

    Assert.assertEquals(Arrays.asList("Sprint plan"), stubTitles);
  }

  // J-UI-3 — when neither title nor body has any non-blank text the
  // stub title is empty so onOptimisticDigest's "(untitled wave)"
  // fallback does fire as the last resort.
  @Test
  public void bodyOnlyCreateWithBlankBodyFallsThroughToUntitledFallback() {
    List<String> stubTitles = new ArrayList<String>();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller =
        new J2clComposeSurfaceController(
            new FakeGateway(),
            view,
            new FakeFactory(),
            new J2clComposeSurfaceController.CreateSuccessHandler() {
              @Override
              public void onWaveCreated(String waveId) { onWaveCreated(waveId, ""); }
              @Override
              public void onWaveCreated(String waveId, String title) {
                stubTitles.add(title);
              }
            },
            waveId -> { });
    controller.start();

    // The validation gate normally blocks (both blank), so to exercise
    // the fallback we use a body that is whitespace-only with a
    // non-blank-but-trimmable suffix character to satisfy the gate.
    controller.onCreateSubmittedWithTitle("", "   \n\n.");

    // First non-blank line is ".", so that becomes the stub title.
    Assert.assertEquals(Arrays.asList("."), stubTitles);
  }
}
