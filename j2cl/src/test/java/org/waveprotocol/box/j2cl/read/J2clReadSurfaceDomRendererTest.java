package org.waveprotocol.box.j2cl.read;

import com.google.j2cl.junit.apt.J2clTestInput;
import elemental2.dom.DomGlobal;
import elemental2.dom.HTMLButtonElement;
import elemental2.dom.HTMLDivElement;
import elemental2.dom.HTMLElement;
import elemental2.dom.KeyboardEvent;
import elemental2.dom.KeyboardEventInit;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

@J2clTestInput(J2clReadSurfaceDomRendererTest.class)
public class J2clReadSurfaceDomRendererTest {
  @Test
  public void enhanceExistingSurfaceWiresServerRenderedBlipsAndInlineThreads() {
    assumeBrowserDom();
    HTMLDivElement host = createHost();
    host.innerHTML =
        "<div class=\"wave-content\" data-wave-id=\"example.com/w+1\">"
            + "<div class=\"conversation\" data-conv-id=\"c+root\">"
            + "<div class=\"thread\" data-thread-id=\"t+root\">"
            + "<div class=\"blip\" data-blip-id=\"b+root\">Root</div>"
            + "<div class=\"inline-thread\" data-thread-id=\"t+inline\">"
            + "<div class=\"blip\" data-blip-id=\"b+reply\">Reply</div>"
            + "</div></div></div></div>";

    Assert.assertTrue(new J2clReadSurfaceDomRenderer(host).enhanceExistingSurface());

    HTMLElement surface = (HTMLElement) host.querySelector("[data-j2cl-read-surface='true']");
    Assert.assertNotNull(surface);
    Assert.assertEquals(
        2, host.querySelectorAll("[data-j2cl-read-blip='true']").length);

    HTMLButtonElement toggle =
        (HTMLButtonElement) host.querySelector(".j2cl-read-thread-toggle");
    Assert.assertNotNull(toggle);
    Assert.assertEquals("true", toggle.getAttribute("aria-expanded"));

    toggle.click();

    HTMLElement inlineThread =
        (HTMLElement) host.querySelector(".inline-thread[data-thread-id='t+inline']");
    Assert.assertEquals("true", inlineThread.getAttribute("data-j2cl-thread-collapsed"));
    Assert.assertEquals("false", toggle.getAttribute("aria-expanded"));
  }

  @Test
  public void enhanceExistingSurfaceIsIdempotentForThreadToggles() {
    assumeBrowserDom();
    HTMLDivElement host = createHost();
    host.innerHTML =
        "<div class=\"wave-content\">"
            + "<div class=\"inline-thread\" data-thread-id=\"t+inline\">"
            + "<div class=\"blip\" data-blip-id=\"b+reply\">Reply</div>"
            + "</div></div>";
    J2clReadSurfaceDomRenderer renderer = new J2clReadSurfaceDomRenderer(host);

    Assert.assertTrue(renderer.enhanceExistingSurface());
    Assert.assertTrue(renderer.enhanceExistingSurface());

    Assert.assertEquals(1, host.querySelectorAll(".j2cl-read-thread-toggle").length);
    Assert.assertEquals(1, host.querySelectorAll("[data-j2cl-read-blip-bound='true']").length);
  }

  @Test
  public void reEnhancementPreservesFocusedRovingTabStop() {
    assumeBrowserDom();
    HTMLDivElement host = createThreadedHost();
    J2clReadSurfaceDomRenderer renderer = new J2clReadSurfaceDomRenderer(host);

    Assert.assertTrue(renderer.enhanceExistingSurface());
    HTMLElement root = blip(host, "b+root");
    HTMLElement reply = blip(host, "b+reply");
    HTMLElement after = blip(host, "b+after");

    reply.focus();
    Assert.assertTrue(renderer.enhanceExistingSurface());

    Assert.assertEquals("-1", root.getAttribute("tabindex"));
    Assert.assertEquals("0", reply.getAttribute("tabindex"));
    Assert.assertEquals("true", reply.getAttribute("aria-current"));
    Assert.assertEquals("-1", after.getAttribute("tabindex"));
    Assert.assertEquals(1, host.querySelectorAll("[tabindex='0']").length);
  }

  @Test
  public void keyboardNavigationSkipsCollapsedThreadBlips() {
    assumeBrowserDom();
    HTMLDivElement host = createThreadedHost();
    J2clReadSurfaceDomRenderer renderer = new J2clReadSurfaceDomRenderer(host);

    Assert.assertTrue(renderer.enhanceExistingSurface());
    HTMLButtonElement toggle =
        (HTMLButtonElement) host.querySelector(".j2cl-read-thread-toggle");
    HTMLElement root = blip(host, "b+root");
    HTMLElement reply = blip(host, "b+reply");
    HTMLElement after = blip(host, "b+after");

    toggle.click();
    root.focus();
    dispatchKey(root, "ArrowDown");

    Assert.assertEquals("0", after.getAttribute("tabindex"));
    Assert.assertEquals("true", after.getAttribute("aria-current"));
    Assert.assertEquals("-1", reply.getAttribute("tabindex"));
  }

  @Test
  public void collapsingFocusedThreadMovesToNearestFollowingVisibleBlip() {
    assumeBrowserDom();
    HTMLDivElement host = createThreadedHost();
    J2clReadSurfaceDomRenderer renderer = new J2clReadSurfaceDomRenderer(host);

    Assert.assertTrue(renderer.enhanceExistingSurface());
    HTMLButtonElement toggle =
        (HTMLButtonElement) host.querySelector(".j2cl-read-thread-toggle");
    HTMLElement root = blip(host, "b+root");
    HTMLElement reply = blip(host, "b+reply");
    HTMLElement after = blip(host, "b+after");

    reply.focus();
    toggle.click();

    Assert.assertEquals("-1", root.getAttribute("tabindex"));
    Assert.assertEquals("-1", reply.getAttribute("tabindex"));
    Assert.assertEquals("0", after.getAttribute("tabindex"));
    Assert.assertEquals("true", after.getAttribute("aria-current"));
  }

  @Test
  public void collapsingFocusedLastThreadFallsBackToPreviousVisibleBlip() {
    assumeBrowserDom();
    HTMLDivElement host = createThreadedHostWithoutFollowingBlip();
    J2clReadSurfaceDomRenderer renderer = new J2clReadSurfaceDomRenderer(host);

    Assert.assertTrue(renderer.enhanceExistingSurface());
    HTMLButtonElement toggle =
        (HTMLButtonElement) host.querySelector(".j2cl-read-thread-toggle");
    HTMLElement root = blip(host, "b+root");
    HTMLElement reply = blip(host, "b+reply");

    reply.focus();
    toggle.click();

    Assert.assertEquals("0", root.getAttribute("tabindex"));
    Assert.assertEquals("true", root.getAttribute("aria-current"));
    Assert.assertEquals("-1", reply.getAttribute("tabindex"));
  }

  @Test
  public void renderLiveBlipsCreatesSemanticReadSurface() {
    assumeBrowserDom();
    HTMLDivElement host = createHost();

    boolean rendered =
        new J2clReadSurfaceDomRenderer(host)
            .render(
                Arrays.asList(
                    new J2clReadBlip("b+root", "Root text"),
                    new J2clReadBlip("b+reply", "Reply text")),
                Collections.<String>emptyList());

    Assert.assertTrue(rendered);
    Assert.assertNotNull(host.querySelector("[data-j2cl-read-surface='true']"));
    Assert.assertEquals(2, host.querySelectorAll("[data-j2cl-read-blip='true']").length);
    Assert.assertEquals("b+root", firstBlip(host).getAttribute("data-blip-id"));
    Assert.assertEquals("0", firstBlip(host).getAttribute("tabindex"));
  }

  @Test
  public void renderFallbackEntriesSynthesizesStableEntryIds() {
    assumeBrowserDom();
    HTMLDivElement host = createHost();

    boolean rendered =
        new J2clReadSurfaceDomRenderer(host)
            .render(
                Collections.<J2clReadBlip>emptyList(),
                Arrays.asList("First fallback", "Second fallback"));

    Assert.assertTrue(rendered);
    Assert.assertEquals(2, host.querySelectorAll("[data-j2cl-read-blip='true']").length);
    Assert.assertEquals("entry-1", firstBlip(host).getAttribute("data-blip-id"));
  }

  private static HTMLDivElement createHost() {
    HTMLDivElement host = (HTMLDivElement) DomGlobal.document.createElement("div");
    DomGlobal.document.body.appendChild(host);
    return host;
  }

  private static HTMLDivElement createThreadedHost() {
    HTMLDivElement host = createHost();
    host.innerHTML =
        "<div class=\"wave-content\">"
            + "<div class=\"thread\" data-thread-id=\"t+root\">"
            + "<div class=\"blip\" data-blip-id=\"b+root\">Root</div>"
            + "<div class=\"inline-thread\" data-thread-id=\"t+inline\">"
            + "<div class=\"blip\" data-blip-id=\"b+reply\">Reply</div>"
            + "</div>"
            + "<div class=\"blip\" data-blip-id=\"b+after\">After</div>"
            + "</div></div>";
    return host;
  }

  private static HTMLDivElement createThreadedHostWithoutFollowingBlip() {
    HTMLDivElement host = createHost();
    host.innerHTML =
        "<div class=\"wave-content\">"
            + "<div class=\"thread\" data-thread-id=\"t+root\">"
            + "<div class=\"blip\" data-blip-id=\"b+root\">Root</div>"
            + "<div class=\"inline-thread\" data-thread-id=\"t+inline\">"
            + "<div class=\"blip\" data-blip-id=\"b+reply\">Reply</div>"
            + "</div>"
            + "</div></div>";
    return host;
  }

  private static void assumeBrowserDom() {
    Assume.assumeTrue(DomGlobal.document != null && DomGlobal.document.body != null);
  }

  private static HTMLElement firstBlip(HTMLDivElement host) {
    return (HTMLElement) host.querySelector("[data-j2cl-read-blip='true']");
  }

  private static HTMLElement blip(HTMLDivElement host, String blipId) {
    return (HTMLElement) host.querySelector("[data-blip-id='" + blipId + "']");
  }

  private static void dispatchKey(HTMLElement target, String key) {
    KeyboardEventInit init = KeyboardEventInit.create();
    init.setKey(key);
    target.dispatchEvent(new KeyboardEvent("keydown", init));
  }
}
