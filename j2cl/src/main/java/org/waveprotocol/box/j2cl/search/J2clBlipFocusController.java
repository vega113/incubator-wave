package org.waveprotocol.box.j2cl.search;

import elemental2.core.JsDate;
import elemental2.dom.DomGlobal;
import elemental2.dom.Element;
import elemental2.dom.Element.FocusOptionsType;
import elemental2.dom.HTMLElement;
import elemental2.dom.NodeList;
import elemental2.dom.ScrollIntoViewOptions;
import java.util.ArrayList;
import java.util.List;

/**
 * #1273: single owner of imperative roving-focus for the selected-wave read
 * surface.
 *
 * <p>Previously the {@code focusMostRecent / focusAdjacent / focusNextMatching /
 * focusLast / focusBlip} logic (plus the tabindex/aria/{@code focused}/scroll/
 * mark-read dance) lived inline in {@link J2clSelectedWaveView}. Centralizing it
 * here gives one place that performs focus mutations and adds
 * {@link #saveFocus()} / {@link #restoreFocus()} / {@link #requestFocusAfterRender}
 * so keyboard focus survives fragment expansion, optimistic inserts, and live
 * re-renders instead of falling back to {@code <body>}.
 *
 * <p>The nav behaviour is a verbatim move of the previous view logic, so the
 * existing keyboard AC (Tab/arrow/n/p/N/P/end/recent) is unchanged. Restore is
 * conservative: it only re-focuses when focus was genuinely lost (activeElement
 * is null/&lt;body&gt;) and never steals focus from an active editing surface.
 */
public final class J2clBlipFocusController {

  /** Sink used to mark a blip read once it receives roving focus. */
  @FunctionalInterface
  public interface MarkReadSink {
    void markFocusedBlipReadNow(HTMLElement blip);
  }

  private final HTMLElement contentList;
  private final MarkReadSink markReadSink;
  private String lastFocusedBlipId = "";

  public J2clBlipFocusController(HTMLElement contentList, MarkReadSink markReadSink) {
    this.contentList = contentList;
    this.markReadSink = markReadSink;
  }

  public void focusMostRecent() {
    List<HTMLElement> blips = renderedBlips();
    if (blips.isEmpty()) {
      return;
    }
    HTMLElement newest = blips.get(blips.size() - 1);
    long newestTime = parseBlipTime(newest.getAttribute("data-blip-time"));
    for (HTMLElement blip : blips) {
      long time = parseBlipTime(blip.getAttribute("data-blip-time"));
      if (time > newestTime) {
        newest = blip;
        newestTime = time;
      }
    }
    focusBlip(newest);
  }

  public void focusLast() {
    List<HTMLElement> blips = renderedBlips();
    if (!blips.isEmpty()) {
      focusBlip(blips.get(blips.size() - 1));
    }
  }

  public void focusAdjacent(int direction) {
    List<HTMLElement> blips = renderedBlips();
    if (blips.isEmpty()) {
      return;
    }
    int current = focusedBlipIndex(blips);
    int next = current < 0 ? (direction > 0 ? 0 : blips.size() - 1) : current + direction;
    if (next < 0) {
      next = 0;
    }
    if (next >= blips.size()) {
      next = blips.size() - 1;
    }
    focusBlip(blips.get(next));
  }

  /**
   * Focuses the next rendered blip carrying {@code attributeName}. Returns
   * false when no rendered blip matches — e.g. when the only unread blips sit
   * in unloaded viewport-window placeholder regions — so callers can fall back
   * to {@link #scrollToUnloadedUnread}.
   */
  public boolean focusNextMatching(String attributeName, int direction) {
    List<HTMLElement> blips = renderedBlips();
    if (blips.isEmpty()) {
      return false;
    }
    int current = focusedBlipIndex(blips);
    int start = current < 0 ? (direction > 0 ? -1 : blips.size()) : current;
    for (int offset = 1; offset <= blips.size(); offset++) {
      int index = positiveModulo(start + (direction * offset), blips.size());
      HTMLElement candidate = blips.get(index);
      if (candidate.hasAttribute(attributeName)) {
        focusBlip(candidate);
        return true;
      }
    }
    return false;
  }

  /**
   * Windowed-viewport fallback for "jump to next unread": when the next unread
   * blip is not rendered (its region is an unloaded placeholder), scroll that
   * placeholder into view so the renderer's visible-placeholder machinery
   * fetches its fragment. Returns the blip id whose placeholder was scrolled
   * into view, or the empty string when no unread blip has a placeholder; the
   * caller re-focuses the blip once it renders.
   */
  public String scrollToUnloadedUnread(List<String> unreadBlipIds) {
    if (unreadBlipIds == null || unreadBlipIds.isEmpty()) {
      return "";
    }
    for (String blipId : unreadBlipIds) {
      if (blipId == null || blipId.isEmpty() || findBlipById(blipId) != null) {
        continue;
      }
      HTMLElement placeholder =
          (HTMLElement)
              contentList.querySelector(
                  "[data-placeholder-blip-id='" + blipId + "']");
      if (placeholder == null) {
        continue;
      }
      ScrollIntoViewOptions scrollOptions = ScrollIntoViewOptions.create();
      scrollOptions.setBlock("center");
      scrollOptions.setInline("nearest");
      placeholder.scrollIntoView(scrollOptions);
      return blipId;
    }
    return "";
  }

  /**
   * Focuses (and marks read) the rendered blip with {@code blipId}. Returns
   * false when the blip is not rendered yet.
   */
  public boolean focusBlipById(String blipId) {
    if (blipId == null || blipId.isEmpty()) {
      return false;
    }
    HTMLElement target = findBlipById(blipId);
    if (target == null) {
      return false;
    }
    focusBlip(target);
    return true;
  }

  /**
   * GWT parity initial-open focus: focus the last unread blip, or the last
   * rendered blip when all are read, without marking read (GWT marks read via
   * dwell, not focus). Returns false when there is nothing to focus yet.
   */
  public boolean focusInitialUnreadOrLast() {
    List<HTMLElement> blips = renderedBlips();
    if (blips.isEmpty()) {
      return false;
    }
    HTMLElement target = null;
    for (int i = blips.size() - 1; i >= 0; i--) {
      HTMLElement blip = blips.get(i);
      if (blip.hasAttribute("unread")) {
        target = blip;
        break;
      }
    }
    if (target == null) {
      target = blips.get(blips.size() - 1);
    }
    focusBlip(target, /* markRead= */ false);
    return true;
  }

  public void focusBlip(HTMLElement target) {
    focusBlip(target, /* markRead= */ true);
  }

  public void focusBlip(HTMLElement target, boolean markRead) {
    if (target == null) {
      return;
    }
    List<HTMLElement> blips = renderedBlips();
    for (HTMLElement blip : blips) {
      blip.setAttribute("tabindex", blip == target ? "0" : "-1");
      if (blip == target) {
        blip.setAttribute("focused", "");
        blip.setAttribute("data-blip-focused", "true");
        blip.setAttribute("aria-current", "true");
        blip.classList.add("j2cl-read-blip-focused");
      } else {
        blip.removeAttribute("focused");
        blip.removeAttribute("data-blip-focused");
        blip.removeAttribute("aria-current");
        blip.classList.remove("j2cl-read-blip-focused");
      }
    }
    FocusOptionsType focusOptions = FocusOptionsType.create();
    focusOptions.setPreventScroll(true);
    target.focus(focusOptions);
    lastFocusedBlipId = blipIdOf(target);
    if (markRead) {
      markReadSink.markFocusedBlipReadNow(target);
    }
    ScrollIntoViewOptions scrollOptions = ScrollIntoViewOptions.create();
    scrollOptions.setBlock("center");
    scrollOptions.setInline("nearest");
    target.scrollIntoView(scrollOptions);
  }

  /**
   * Records the currently roving-focused blip id so it can be restored across a
   * re-render. No-op when focus is in an editing surface or not on a blip.
   */
  public void saveFocus() {
    if (isEditingContextActive()) {
      return;
    }
    List<HTMLElement> blips = renderedBlips();
    int index = focusedBlipIndex(blips);
    if (index >= 0) {
      lastFocusedBlipId = blipIdOf(blips.get(index));
    }
  }

  /**
   * Re-applies roving focus to the last saved blip when focus was genuinely lost
   * (activeElement null/&lt;body&gt;) after a DOM mutation. Does not mark-read
   * again and never steals focus from an active editing surface or an element
   * that already has it. Returns true when focus was restored.
   */
  public boolean restoreFocus() {
    if (lastFocusedBlipId.isEmpty() || !isFocusLost()) {
      return false;
    }
    HTMLElement target = findBlipById(lastFocusedBlipId);
    if (target == null) {
      return false;
    }
    focusBlip(target, /* markRead= */ false);
    return true;
  }

  /**
   * Called after the projector / fragment loader mutates the blip DOM: focuses
   * {@code blipId} when present (e.g. a freshly inserted reply), otherwise falls
   * back to {@link #restoreFocus()}. Never steals focus while editing.
   */
  public void requestFocusAfterRender(String blipId) {
    if (isEditingContextActive()) {
      return;
    }
    if (blipId != null && !blipId.isEmpty()) {
      HTMLElement target = findBlipById(blipId);
      if (target != null && isFocusLost()) {
        focusBlip(target, /* markRead= */ false);
        return;
      }
    }
    restoreFocus();
  }

  /** Package-visible for tests: the id last given roving focus. */
  String lastFocusedBlipId() {
    return lastFocusedBlipId;
  }

  private static long parseBlipTime(String value) {
    if (value == null || value.isEmpty()) {
      return Long.MIN_VALUE;
    }
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException ignored) {
      double epochMs = new JsDate(value).getTime();
      return Double.isNaN(epochMs) ? Long.MIN_VALUE : (long) epochMs;
    }
  }

  private static int positiveModulo(int value, int modulo) {
    int result = value % modulo;
    return result < 0 ? result + modulo : result;
  }

  private int focusedBlipIndex(List<HTMLElement> blips) {
    Element active = DomGlobal.document.activeElement;
    if (active != null) {
      for (int index = 0; index < blips.size(); index++) {
        HTMLElement blip = blips.get(index);
        if (blip == active || blip.contains(active)) {
          return index;
        }
      }
    }
    for (int index = 0; index < blips.size(); index++) {
      HTMLElement blip = blips.get(index);
      if (blip.hasAttribute("focused")
          || "true".equals(blip.getAttribute("data-blip-focused"))
          || blip.classList.contains("j2cl-read-blip-focused")) {
        return index;
      }
    }
    return -1;
  }

  private List<HTMLElement> renderedBlips() {
    NodeList<Element> nodes =
        contentList.querySelectorAll("wave-blip[data-blip-id], div.blip[data-blip-id]");
    List<HTMLElement> blips = new ArrayList<HTMLElement>();
    for (int index = 0; index < nodes.length; index++) {
      HTMLElement blip = (HTMLElement) nodes.item(index);
      if (blip != null) {
        blips.add(blip);
      }
    }
    return blips;
  }

  private HTMLElement findBlipById(String blipId) {
    for (HTMLElement blip : renderedBlips()) {
      if (blipId.equals(blipIdOf(blip))) {
        return blip;
      }
    }
    return null;
  }

  private static String blipIdOf(HTMLElement blip) {
    String id = blip.getAttribute("data-blip-id");
    return id == null ? "" : id;
  }

  /** True when focus has been lost to the document body or nothing. */
  private static boolean isFocusLost() {
    Element active = DomGlobal.document.activeElement;
    return active == null || active == DomGlobal.document.body;
  }

  /** True when the user is typing in a composer / contenteditable / input. */
  private static boolean isEditingContextActive() {
    Element active = DomGlobal.document.activeElement;
    if (active == null) {
      return false;
    }
    String tag = active.tagName == null ? "" : active.tagName.toLowerCase();
    if ("textarea".equals(tag) || "input".equals(tag)) {
      return true;
    }
    if (tag.indexOf("composer") >= 0) {
      return true;
    }
    String editable = active.getAttribute("contenteditable");
    if ("true".equals(editable) || "".equals(editable) || "plaintext-only".equals(editable)) {
      return true;
    }
    return active.closest(
            "[contenteditable='true'], [contenteditable=''], textarea, input,"
                + " wavy-composer, composer-inline-reply")
        != null;
  }
}
