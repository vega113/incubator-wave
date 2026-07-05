package org.waveprotocol.box.j2cl.notify;

import com.google.j2cl.junit.apt.J2clTestInput;
import elemental2.dom.DomGlobal;
import elemental2.dom.HTMLElement;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

@J2clTestInput(J2clDomNotificationServiceTest.class)
public class J2clDomNotificationServiceTest {

  private HTMLElement host;

  @After
  public void tearDown() {
    if (host != null && host.parentElement != null) {
      host.parentElement.removeChild(host);
    }
    host = null;
  }

  @Test
  public void mountsRegionWithBothLiveRegions() {
    assumeBrowserDom();
    J2clDomNotificationService service = mountService();

    HTMLElement region = service.getRegionElement();
    Assert.assertEquals("true", region.getAttribute("data-j2cl-notify-region"));
    Assert.assertEquals(2, region.querySelectorAll("[aria-live]").length);
    Assert.assertNotNull(region.querySelector("[aria-live='assertive']"));
    Assert.assertNotNull(region.querySelector("[aria-live='polite']"));
  }

  @Test
  public void showErrorAddsToastAndAnnouncesAssertively() {
    assumeBrowserDom();
    J2clDomNotificationService service = mountService();

    service.showError("Create wave failed.", 0);

    HTMLElement toast =
        (HTMLElement) service.getRegionElement().querySelector(".j2cl-notify-toast");
    Assert.assertNotNull(toast);
    Assert.assertEquals("Create wave failed.", toast.textContent);
    Assert.assertEquals("error", toast.getAttribute("data-j2cl-notify-level"));
    HTMLElement live =
        (HTMLElement) service.getRegionElement().querySelector("[aria-live='assertive']");
    Assert.assertEquals("Create wave failed.", live.textContent);
  }

  @Test
  public void showSuccessUsesPoliteLiveRegionAndSuccessLevel() {
    assumeBrowserDom();
    J2clDomNotificationService service = mountService();

    service.showSuccess("Saved.");

    HTMLElement toast =
        (HTMLElement) service.getRegionElement().querySelector(".j2cl-notify-toast");
    Assert.assertNotNull(toast);
    Assert.assertEquals("success", toast.getAttribute("data-j2cl-notify-level"));
    HTMLElement polite =
        (HTMLElement) service.getRegionElement().querySelector("[aria-live='polite']");
    Assert.assertEquals("Saved.", polite.textContent);
  }

  @Test
  public void emptyMessageAddsNoToast() {
    assumeBrowserDom();
    J2clDomNotificationService service = mountService();

    service.showError("", 0);

    Assert.assertEquals(
        0, service.getRegionElement().querySelectorAll(".j2cl-notify-toast").length);
  }

  @Test
  public void clearRemovesToastsAndLiveText() {
    assumeBrowserDom();
    J2clDomNotificationService service = mountService();
    service.showError("Boom", 0);
    service.showSuccess("Yay");
    Assert.assertTrue(service.getRegionElement().querySelectorAll(".j2cl-notify-toast").length >= 1);

    service.clear();

    Assert.assertEquals(
        0, service.getRegionElement().querySelectorAll(".j2cl-notify-toast").length);
    HTMLElement live =
        (HTMLElement) service.getRegionElement().querySelector("[aria-live='assertive']");
    Assert.assertEquals("", live.textContent);
  }

  @Test
  public void multipleErrorsStack() {
    assumeBrowserDom();
    J2clDomNotificationService service = mountService();

    service.showError("First", 0);
    service.showError("Second", 0);

    Assert.assertEquals(
        2, service.getRegionElement().querySelectorAll(".j2cl-notify-toast").length);
  }

  private static void assumeBrowserDom() {
    Assume.assumeTrue(DomGlobal.document != null && DomGlobal.document.body != null);
  }

  private J2clDomNotificationService mountService() {
    host = (HTMLElement) DomGlobal.document.createElement("div");
    DomGlobal.document.body.appendChild(host);
    return new J2clDomNotificationService(host);
  }
}
