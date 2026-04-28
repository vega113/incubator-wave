package org.waveprotocol.box.j2cl.search;

import com.google.j2cl.junit.apt.J2clTestInput;
import elemental2.dom.DomGlobal;
import elemental2.dom.HTMLElement;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

/**
 * J-UI-7 (#1085, R-4.4) — coverage for the rail-card view's
 * unread-count attribute round-trip and no-op contract.
 *
 * <p>The Lit element ({@code <wavy-search-rail-card>}) owns rail-card
 * read-state styling and the article aria-label, so this test only
 * asserts the Java view's narrow responsibility: write the attribute,
 * report whether the count actually changed, and never churn on a
 * same-value update.
 */
@J2clTestInput(J2clSearchRailCardViewTest.class)
public class J2clSearchRailCardViewTest {
  @Test
  public void initialAttributeStampsUnreadCount() {
    assumeBrowserDom();
    J2clSearchRailCardView view =
        new J2clSearchRailCardView(item("w-1", "Sprint review", 3, 12), waveId -> {});
    HTMLElement el = view.element();
    Assert.assertEquals("3", el.getAttribute("unread-count"));
    Assert.assertEquals("12", el.getAttribute("msg-count"));
    Assert.assertEquals("w-1", el.getAttribute("data-wave-id"));
    Assert.assertEquals("3 unread · 12 messages", view.getStatsText());
  }

  @Test
  public void setUnreadCountWritesAttributeAndReportsChange() {
    assumeBrowserDom();
    J2clSearchRailCardView view =
        new J2clSearchRailCardView(item("w-1", "Sprint review", 3, 12), waveId -> {});
    boolean changed = view.setUnreadCount(0);
    Assert.assertTrue(changed);
    Assert.assertEquals("0", view.element().getAttribute("unread-count"));
  }

  @Test
  public void setUnreadCountToCurrentValueIsNoOp() {
    assumeBrowserDom();
    J2clSearchRailCardView view =
        new J2clSearchRailCardView(item("w-1", "Sprint review", 3, 12), waveId -> {});
    HTMLElement el = view.element();
    String before = el.getAttribute("unread-count");
    boolean changed = view.setUnreadCount(3);
    Assert.assertFalse(changed);
    Assert.assertEquals(before, el.getAttribute("unread-count"));
  }

  @Test
  public void negativeUnreadCountClampsToZero() {
    assumeBrowserDom();
    J2clSearchRailCardView view =
        new J2clSearchRailCardView(item("w-1", "Sprint review", 3, 12), waveId -> {});
    boolean changed = view.setUnreadCount(-1);
    Assert.assertTrue(changed);
    Assert.assertEquals("0", view.element().getAttribute("unread-count"));
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
