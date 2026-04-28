package org.waveprotocol.box.j2cl.search;

import com.google.j2cl.junit.apt.J2clTestInput;
import elemental2.dom.DomGlobal;
import elemental2.dom.HTMLElement;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

/**
 * J-UI-7 (#1085, R-4.4) — coverage for the legacy DOM digest view's
 * read-state attribute toggle and accessible-name refresh. The view has
 * no Lit host to derive styling from, so it owns its own
 * {@code data-read} attribute and refreshes the root {@code aria-label}
 * inside {@link J2clDigestView#setUnreadCount(int)}.
 */
@J2clTestInput(J2clDigestViewTest.class)
public class J2clDigestViewTest {
  @Test
  public void initialUnreadCountStampsAriaLabelAndOmitsDataRead() {
    assumeBrowserDom();
    J2clDigestView view = new J2clDigestView(item("w-1", "Sprint review", 3, 12), waveId -> {});
    HTMLElement root = view.element();
    Assert.assertFalse(
        "Initial unread > 0 must not stamp data-read", root.hasAttribute("data-read"));
    Assert.assertEquals(
        "Wave: Sprint review. 3 unread.", root.getAttribute("aria-label"));
  }

  @Test
  public void initialZeroUnreadStampsDataReadAndAllReadAriaLabel() {
    assumeBrowserDom();
    J2clDigestView view = new J2clDigestView(item("w-1", "Sprint review", 0, 12), waveId -> {});
    HTMLElement root = view.element();
    Assert.assertEquals("true", root.getAttribute("data-read"));
    Assert.assertEquals(
        "Wave: Sprint review. Read.", root.getAttribute("aria-label"));
  }

  @Test
  public void setUnreadCountToZeroAddsDataReadAndRefreshesAriaLabel() {
    assumeBrowserDom();
    J2clDigestView view = new J2clDigestView(item("w-1", "Sprint review", 3, 12), waveId -> {});
    boolean changed = view.setUnreadCount(0);
    Assert.assertTrue("Transition 3 -> 0 must report changed", changed);
    HTMLElement root = view.element();
    Assert.assertEquals("true", root.getAttribute("data-read"));
    Assert.assertEquals("Wave: Sprint review. Read.", root.getAttribute("aria-label"));
    Assert.assertEquals("12 messages", view.getStatsText());
  }

  @Test
  public void setUnreadCountToNonZeroRemovesDataReadAndRefreshesAriaLabel() {
    assumeBrowserDom();
    J2clDigestView view = new J2clDigestView(item("w-1", "Sprint review", 0, 12), waveId -> {});
    boolean changed = view.setUnreadCount(2);
    Assert.assertTrue("Transition 0 -> 2 must report changed", changed);
    HTMLElement root = view.element();
    Assert.assertFalse(root.hasAttribute("data-read"));
    Assert.assertEquals(
        "Wave: Sprint review. 2 unread.", root.getAttribute("aria-label"));
    Assert.assertEquals("2 unread · 12 messages", view.getStatsText());
  }

  @Test
  public void setUnreadCountToCurrentValueIsNoOpAndKeepsAttributesStable() {
    assumeBrowserDom();
    J2clDigestView view = new J2clDigestView(item("w-1", "Sprint review", 2, 12), waveId -> {});
    HTMLElement root = view.element();
    String labelBefore = root.getAttribute("aria-label");
    boolean changed = view.setUnreadCount(2);
    Assert.assertFalse("Same-value set must not report changed", changed);
    Assert.assertEquals(labelBefore, root.getAttribute("aria-label"));
    Assert.assertFalse(root.hasAttribute("data-read"));
  }

  @Test
  public void emptyTitleStillProducesSensibleAriaLabel() {
    assumeBrowserDom();
    J2clDigestView view = new J2clDigestView(item("w-1", "", 0, 1), waveId -> {});
    HTMLElement root = view.element();
    Assert.assertEquals("Wave. Read.", root.getAttribute("aria-label"));
  }

  @Test
  public void negativeUnreadCountClampsToReadState() {
    assumeBrowserDom();
    J2clDigestView view = new J2clDigestView(item("w-1", "Sprint review", 4, 5), waveId -> {});
    boolean changed = view.setUnreadCount(-1);
    Assert.assertTrue(changed);
    HTMLElement root = view.element();
    Assert.assertEquals("true", root.getAttribute("data-read"));
    Assert.assertEquals(
        "Wave: Sprint review. Read.", root.getAttribute("aria-label"));
  }

  private static J2clSearchDigestItem item(
      String waveId, String title, int unread, int blipCount) {
    return new J2clSearchDigestItem(
        waveId,
        title,
        /* snippet= */ "snippet",
        /* author= */ "alice@example.com",
        unread,
        blipCount,
        /* lastModified= */ 1_700_000_000_000L,
        /* pinned= */ false);
  }

  private static void assumeBrowserDom() {
    Assume.assumeTrue(
        "DOM-only test; skipped on JVM runs", DomGlobal.document != null);
  }
}
