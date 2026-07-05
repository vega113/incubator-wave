package org.waveprotocol.box.j2cl.search;

import com.google.j2cl.junit.apt.J2clTestInput;
import elemental2.dom.DomGlobal;
import elemental2.dom.Element;
import elemental2.dom.HTMLElement;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

@J2clTestInput(J2clBlipFocusControllerTest.class)
public class J2clBlipFocusControllerTest {

  private HTMLElement contentList;
  private HTMLElement textarea;

  @After
  public void tearDown() {
    if (contentList != null && contentList.parentElement != null) {
      contentList.parentElement.removeChild(contentList);
    }
    if (textarea != null && textarea.parentElement != null) {
      textarea.parentElement.removeChild(textarea);
    }
    contentList = null;
    textarea = null;
  }

  @Test
  public void focusMostRecentFocusesHighestBlipTime() {
    assumeBrowserDom();
    List<String> read = new ArrayList<String>();
    J2clBlipFocusController focus = controllerWithBlips(read, "b1:100", "b2:300", "b3:200");

    focus.focusMostRecent();

    Assert.assertEquals("b2", activeBlipId());
    Assert.assertEquals("b2", focus.lastFocusedBlipId());
    Assert.assertEquals(1, read.size());
  }

  @Test
  public void focusAdjacentMovesAndClamps() {
    assumeBrowserDom();
    J2clBlipFocusController focus = controllerWithBlips(new ArrayList<String>(), "b1:1", "b2:2", "b3:3");

    focus.focusBlip(blip("b1"));
    focus.focusAdjacent(1);
    Assert.assertEquals("b2", activeBlipId());
    focus.focusAdjacent(1);
    Assert.assertEquals("b3", activeBlipId());
    focus.focusAdjacent(1); // clamp at last
    Assert.assertEquals("b3", activeBlipId());
    focus.focusAdjacent(-1);
    Assert.assertEquals("b2", activeBlipId());
  }

  @Test
  public void focusNextMatchingSkipsToAttributedBlip() {
    assumeBrowserDom();
    J2clBlipFocusController focus = controllerWithBlips(new ArrayList<String>(), "b1:1", "b2:2", "b3:3");
    blip("b3").setAttribute("unread", "");

    focus.focusBlip(blip("b1"));
    focus.focusNextMatching("unread", 1);

    Assert.assertEquals("b3", activeBlipId());
  }

  @Test
  public void saveThenRestoreReFocusesAfterFocusLost() {
    assumeBrowserDom();
    J2clBlipFocusController focus = controllerWithBlips(new ArrayList<String>(), "b1:1", "b2:2");

    focus.focusBlip(blip("b2"));
    focus.saveFocus();
    // Simulate a re-render dropping focus to the body.
    blip("b2").blur();
    Assert.assertTrue(isFocusOnBody());

    Assert.assertTrue(focus.restoreFocus());
    Assert.assertEquals("b2", activeBlipId());
  }

  @Test
  public void restoreIsNoOpWhenFocusNotLost() {
    assumeBrowserDom();
    J2clBlipFocusController focus = controllerWithBlips(new ArrayList<String>(), "b1:1", "b2:2");

    focus.focusBlip(blip("b1"));
    focus.saveFocus();
    // b1 still has focus — restore must not steal or move it.
    Assert.assertFalse(focus.restoreFocus());
    Assert.assertEquals("b1", activeBlipId());
  }

  @Test
  public void requestFocusAfterRenderFocusesNamedBlipWhenLost() {
    assumeBrowserDom();
    J2clBlipFocusController focus = controllerWithBlips(new ArrayList<String>(), "b1:1", "b2:2", "b3:3");

    focus.focusBlip(blip("b1"));
    blip("b1").blur();

    focus.requestFocusAfterRender("b3");

    Assert.assertEquals("b3", activeBlipId());
  }

  @Test
  public void editingContextSuppressesSaveAndRestore() {
    assumeBrowserDom();
    J2clBlipFocusController focus = controllerWithBlips(new ArrayList<String>(), "b1:1", "b2:2");
    focus.focusBlip(blip("b2"));
    focus.saveFocus(); // saved b2

    // User moves into a textarea (editing surface).
    textarea = (HTMLElement) DomGlobal.document.createElement("textarea");
    DomGlobal.document.body.appendChild(textarea);
    textarea.focus();
    Assert.assertFalse(isFocusOnBody());

    // saveFocus must not overwrite with a blip; restore/afterRender must not steal.
    focus.saveFocus();
    Assert.assertFalse(focus.restoreFocus());
    focus.requestFocusAfterRender("b1");
    Assert.assertEquals("TEXTAREA", DomGlobal.document.activeElement.tagName);
  }

  private static void assumeBrowserDom() {
    Assume.assumeTrue(DomGlobal.document != null && DomGlobal.document.body != null);
  }

  /** Each spec is "id:time"; the created blip gets tabindex so it can receive focus. */
  private J2clBlipFocusController controllerWithBlips(List<String> readSink, String... specs) {
    contentList = (HTMLElement) DomGlobal.document.createElement("div");
    DomGlobal.document.body.appendChild(contentList);
    for (String spec : specs) {
      String[] parts = spec.split(":");
      HTMLElement blip = (HTMLElement) DomGlobal.document.createElement("wave-blip");
      blip.setAttribute("data-blip-id", parts[0]);
      blip.setAttribute("data-blip-time", parts[1]);
      blip.setAttribute("tabindex", "-1");
      contentList.appendChild(blip);
    }
    return new J2clBlipFocusController(contentList, b -> readSink.add(b.getAttribute("data-blip-id")));
  }

  private HTMLElement blip(String id) {
    return (HTMLElement) contentList.querySelector("wave-blip[data-blip-id='" + id + "']");
  }

  private static String activeBlipId() {
    Element active = DomGlobal.document.activeElement;
    return active == null ? "" : active.getAttribute("data-blip-id");
  }

  private static boolean isFocusOnBody() {
    Element active = DomGlobal.document.activeElement;
    return active == null || active == DomGlobal.document.body;
  }
}
