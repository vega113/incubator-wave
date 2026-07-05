package org.waveprotocol.box.j2cl.compose;

import com.google.j2cl.junit.apt.J2clTestInput;
import org.junit.Assert;
import org.junit.Test;
import org.waveprotocol.box.j2cl.telemetry.RecordingTelemetrySink;
import org.waveprotocol.box.j2cl.testsupport.FakeGateway;
import org.waveprotocol.box.j2cl.testsupport.FakeView;

/**
 * #1270: split from the monster J2clComposeSurfaceControllerTest. Covers
 * mention pick serialisation, chip binding and telemetry.
 */
@J2clTestInput(J2clComposeMentionsTest.class)
public class J2clComposeMentionsTest extends J2clComposeControllerTestSupport {

  // F-3.S2 (#1038, R-5.3) — telemetry-only assertions for the mention
  // pick / abandon paths. The controller does not change model state
  // (chip lives on the lit composer DOM); it just records telemetry.
  @Test
  public void onMentionPickedRecordsTelemetry() {
    RecordingTelemetrySink telemetry = new RecordingTelemetrySink();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller = newControllerWithTelemetry(view, telemetry);
    controller.start();
    controller.onMentionPicked("alice@example.com", "Alice Adams");
    Assert.assertTrue(
        "compose.mention_picked event should be recorded",
        telemetry.events().stream().anyMatch(e -> "compose.mention_picked".equals(e.getName())));
  }

  // F-3.S2 (#1038, R-5.3, PR #1066 review thread PRRT_kwDOBwxLXs592RVM)
  // — picking a mention then submitting a reply must serialise a
  // mention/user annotation referencing the participant address
  // alongside the surrounding text. Without this the outgoing delta
  // is just the literal `@DisplayName` substring with no annotation.
  @Test
  public void mentionPickIsSerialisedAsMentionUserAnnotationOnReplySubmit() {
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
    openWaveForReply(controller);
    controller.onMentionPicked("alice@example.com", "Alice Adams");
    controller.onReplySubmitted("Hi @Alice Adams welcome");

    String delta = gateway.lastSubmitRequest.getDeltaJson();
    assertContains(
        delta,
        "\"2\":\"Hi \"",
        "{\"1\":{\"3\":[{\"1\":\"mention/user\",\"3\":\"alice@example.com\"}]}}",
        "\"2\":\"@Alice Adams\"",
        "{\"1\":{\"2\":[\"mention/user\"]}}",
        "\"2\":\" welcome\"");
  }

  @Test
  public void multiplePickedMentionsAreSerialisedInOrder() {
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
    openWaveForReply(controller);
    controller.onMentionPicked("alice@example.com", "Alice Adams");
    controller.onMentionPicked("bob@example.com", "Bob Brown");
    controller.onReplySubmitted("@Alice Adams and @Bob Brown");

    String delta = gateway.lastSubmitRequest.getDeltaJson();
    assertContains(
        delta,
        "\"alice@example.com\"",
        "\"bob@example.com\"",
        "\"2\":\"@Alice Adams\"",
        "\"2\":\"@Bob Brown\"",
        "\"2\":\" and \"");
  }

  @Test
  public void mentionPicksClearedAfterSuccessfulSubmit() {
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
    openWaveForReply(controller);
    controller.onMentionPicked("alice@example.com", "Alice Adams");
    controller.onReplySubmitted("Hi @Alice Adams");
    Assert.assertTrue(
        "first submit must include the mention/user annotation",
        gateway.lastSubmitRequest.getDeltaJson().contains("alice@example.com"));
    // Second submit without picking again should NOT carry the
    // annotation: pendingMentions is cleared on success.
    controller.onReplySubmitted("Plain followup");
    String secondDelta = gateway.lastSubmitRequest.getDeltaJson();
    Assert.assertFalse(
        "follow-up reply must not carry a stale mention/user annotation",
        secondDelta.contains("mention/user"));
    assertContains(secondDelta, "\"2\":\"Plain followup\"");
  }

  @Test
  public void mentionPickWithDeletedChipFallsBackToPlainText() {
    // User picks a mention, then deletes the chip on the lit side.
    // The lit composer's atomic-delete handler removes the chip span;
    // the controller's pendingMentions still has the entry, but the
    // chip text no longer occurs in the draft. The submit must fall
    // back to a plain-text component instead of failing.
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
    openWaveForReply(controller);
    controller.onMentionPicked("alice@example.com", "Alice Adams");
    controller.onReplySubmitted("plain text only");
    String delta = gateway.lastSubmitRequest.getDeltaJson();
    Assert.assertFalse(
        "no chip text in draft means no annotation should be emitted",
        delta.contains("mention/user"));
    assertContains(delta, "\"2\":\"plain text only\"");
  }

  // PR #1066 review thread PRRT_kwDOBwxLXs593gTR — two picks with the
  // same chipText (e.g. duplicate display names that resolve to
  // distinct addresses) must both serialise as separate mention
  // annotations, each pointing at its own address. The previous
  // first-text-occurrence match collapsed both onto the leading
  // chip and the second mention dropped its annotation.
  @Test
  public void duplicateDisplayNameMentionsBindByChipOffsetNotFirstMatch() {
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
    openWaveForReply(controller);
    // Two picks share the chipText "@Alice" but have distinct
    // addresses; chipTextOffset reflects the chip insertion point in
    // the body's plain text at pick time.
    // Chip 1 sits at offset 0 ("@Alice" + " and "); chip 2 sits at
    // offset 11 (right after " and "). Both share the chipText
    // "@Alice" but resolve to distinct addresses.
    controller.onMentionPicked("alice@a.example.com", "Alice", 0);
    controller.onMentionPicked("alice@b.example.com", "Alice", 11);
    controller.onReplySubmitted("@Alice and @Alice");

    String delta = gateway.lastSubmitRequest.getDeltaJson();
    // Both addresses must round-trip as mention/user annotations; the
    // surrounding plain-text " and " run remains plain text.
    assertContains(
        delta,
        "{\"1\":{\"3\":[{\"1\":\"mention/user\",\"3\":\"alice@a.example.com\"}]}}",
        "{\"1\":{\"3\":[{\"1\":\"mention/user\",\"3\":\"alice@b.example.com\"}]}}",
        "\"2\":\" and \"");
  }

  // PR #1066 review thread PRRT_kwDOBwxLXs593gTR — when the user
  // types `@Alice` plain text first and then picks a real `@Alice`
  // chip after, the picked chip's offset must steer the binding to
  // the second occurrence. Otherwise the plain literal swallows the
  // annotation and the real chip is submitted as plain text.
  @Test
  public void mentionPickAfterPlainAtNameBindsToChipNotPlainOccurrence() {
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
    openWaveForReply(controller);
    // Draft has two `@Alice` substrings; only the second is a picked
    // chip (offset 11 — past the literal occurrence at 0). The first
    // `@Alice` must remain plain text in the outgoing delta.
    controller.onMentionPicked("alice@example.com", "Alice", 11);
    controller.onReplySubmitted("@Alice and @Alice");

    String delta = gateway.lastSubmitRequest.getDeltaJson();
    // The text run before the chip carries the literal `@Alice` plus
    // the connector word; the annotation wraps only the chip
    // occurrence. We assert the text run starts with `@Alice and `
    // (the literal `@Alice` is part of plain-text payload, not an
    // annotation).
    assertContains(
        delta,
        "\"2\":\"@Alice and \"",
        "{\"1\":{\"3\":[{\"1\":\"mention/user\",\"3\":\"alice@example.com\"}]}}",
        "\"2\":\"@Alice\"",
        "{\"1\":{\"2\":[\"mention/user\"]}}");
    // Only ONE mention/user annotation start in the delta — the plain
    // literal must NOT have been bound.
    Assert.assertEquals(
        "exactly one mention/user annotation must be emitted",
        1,
        countOccurrences(delta, "\"1\":\"mention/user\""));
  }

  @Test
  public void onMentionAbandonedRecordsTelemetry() {
    RecordingTelemetrySink telemetry = new RecordingTelemetrySink();
    FakeView view = new FakeView();
    J2clComposeSurfaceController controller = newControllerWithTelemetry(view, telemetry);
    controller.start();
    controller.onMentionAbandoned();
    Assert.assertTrue(
        "compose.mention_abandoned event should be recorded",
        telemetry.events().stream().anyMatch(e -> "compose.mention_abandoned".equals(e.getName())));
  }
}
