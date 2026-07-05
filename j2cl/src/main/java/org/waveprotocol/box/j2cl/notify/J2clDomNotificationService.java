package org.waveprotocol.box.j2cl.notify;

import elemental2.dom.DomGlobal;
import elemental2.dom.HTMLElement;
import java.util.ArrayList;
import java.util.List;

/**
 * #1271: default DOM-backed {@link J2clNotificationService} for the
 * {@code ?view=j2cl-root} shell.
 *
 * <p>Mounts a notification region under a host element containing (a) two
 * visually-hidden ARIA live regions — assertive for errors, polite for
 * status/success — so screen readers announce every failure path, and (b) a
 * visible toast stack. Error toasts auto-dismiss after a longer window (or stay
 * until cleared when {@code timeoutMs <= 0}); success/transient toasts are
 * calmer and shorter. This is deliberately Java+DOM only; a Lit
 * {@code wave-notification} bridge is a follow-up under #964.
 */
public final class J2clDomNotificationService implements J2clNotificationService {

  static final String REGION_CLASS = "j2cl-notify-region";
  static final String TOAST_STACK_CLASS = "j2cl-notify-toasts";
  static final String TOAST_CLASS = "j2cl-notify-toast";

  private final HTMLElement region;
  private final HTMLElement liveAssertive;
  private final HTMLElement livePolite;
  private final HTMLElement toastStack;
  private final List<Double> pendingTimers = new ArrayList<Double>();

  public J2clDomNotificationService(HTMLElement host) {
    region = (HTMLElement) DomGlobal.document.createElement("div");
    region.className = REGION_CLASS;
    region.setAttribute("data-j2cl-notify-region", "true");

    liveAssertive = createLiveRegion("assertive");
    livePolite = createLiveRegion("polite");
    toastStack = (HTMLElement) DomGlobal.document.createElement("div");
    toastStack.className = TOAST_STACK_CLASS;

    region.appendChild(liveAssertive);
    region.appendChild(livePolite);
    region.appendChild(toastStack);
    if (host != null) {
      host.appendChild(region);
    }
  }

  private static HTMLElement createLiveRegion(String politeness) {
    HTMLElement live = (HTMLElement) DomGlobal.document.createElement("div");
    live.setAttribute("aria-live", politeness);
    live.setAttribute("aria-atomic", "true");
    live.setAttribute("role", "assertive".equals(politeness) ? "alert" : "status");
    live.className = "j2cl-notify-live";
    // Visually hidden but available to assistive technology.
    live.setAttribute(
        "style",
        "position:absolute;width:1px;height:1px;overflow:hidden;"
            + "clip:rect(0 0 0 0);white-space:nowrap;");
    return live;
  }

  /** Test/host seam: the mounted region element. */
  public HTMLElement getRegionElement() {
    return region;
  }

  @Override
  public void showError(String message, int timeoutMs) {
    announce(liveAssertive, message);
    addToast(message, Level.ERROR, timeoutMs);
  }

  @Override
  public void showSuccess(String message) {
    announce(livePolite, message);
    addToast(message, Level.SUCCESS, DEFAULT_TRANSIENT_TIMEOUT_MS);
  }

  @Override
  public void showTransient(String message, Level level) {
    announce(level == Level.ERROR ? liveAssertive : livePolite, message);
    addToast(message, level == null ? Level.INFO : level, DEFAULT_TRANSIENT_TIMEOUT_MS);
  }

  @Override
  public void clear() {
    for (Double handle : pendingTimers) {
      DomGlobal.clearTimeout(handle);
    }
    pendingTimers.clear();
    toastStack.innerHTML = "";
    liveAssertive.textContent = "";
    livePolite.textContent = "";
  }

  private static void announce(HTMLElement liveRegion, String message) {
    // Re-assert the text so identical consecutive messages are still announced.
    liveRegion.textContent = "";
    liveRegion.textContent = message == null ? "" : message;
  }

  private void addToast(String message, Level level, int timeoutMs) {
    if (message == null || message.isEmpty()) {
      return;
    }
    HTMLElement toast = (HTMLElement) DomGlobal.document.createElement("div");
    toast.className = TOAST_CLASS + " " + TOAST_CLASS + "-" + level.name().toLowerCase();
    toast.setAttribute("data-j2cl-notify-level", level.name().toLowerCase());
    // The live regions own AT announcement; the toast is a visual affordance.
    toast.setAttribute("role", "presentation");
    toast.textContent = message;
    toastStack.appendChild(toast);
    if (timeoutMs > 0) {
      double handle =
          DomGlobal.setTimeout(
              ignored -> {
                if (toast.parentNode != null) {
                  toast.parentNode.removeChild(toast);
                }
              },
              timeoutMs);
      pendingTimers.add(handle);
    }
  }
}
