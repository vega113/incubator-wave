package org.waveprotocol.box.j2cl.common;

/**
 * #1269: the single source of truth for the DOM contract between the J2CL Java
 * surfaces and the host page / Lit layer — custom-event names and the
 * {@code data-j2cl-*} attributes.
 *
 * <p>Previously these literals were scattered (and duplicated on the Lit side),
 * so a typo only failed at runtime in the compiled JS and a rename meant hunting
 * every call site. Adding or renaming a nav event / data attribute now touches
 * this file plus the call sites; the Lit vocabulary in {@code j2cl/lit/src/}
 * must stay in sync with the values here.
 *
 * <p>Values are the exact wire strings; do not change them without updating the
 * Lit side and any test that dispatches the literal.
 */
public final class J2clUiTokens {

  private J2clUiTokens() {}

  // ---- Wave navigation-row events (bubbles + composed from <wavy-wave-nav-row>) ----
  public static final String EVENT_NAV_RECENT = "wave-nav-recent-requested";
  public static final String EVENT_NAV_NEXT_UNREAD = "wave-nav-next-unread-requested";
  public static final String EVENT_NAV_PREVIOUS = "wave-nav-previous-requested";
  public static final String EVENT_NAV_NEXT = "wave-nav-next-requested";
  public static final String EVENT_NAV_END = "wave-nav-end-requested";
  public static final String EVENT_NAV_PREV_MENTION = "wave-nav-prev-mention-requested";
  public static final String EVENT_NAV_NEXT_MENTION = "wave-nav-next-mention-requested";
  public static final String EVENT_NAV_ARCHIVE_TOGGLE = "wave-nav-archive-toggle-requested";
  public static final String EVENT_NAV_PIN_TOGGLE = "wave-nav-pin-toggle-requested";
  public static final String EVENT_NAV_VERSION_HISTORY = "wave-nav-version-history-requested";

  /** All nav-row event names, in nav-row order. Useful for bulk binding / audit. */
  public static final String[] EVENTS_NAV = {
    EVENT_NAV_RECENT,
    EVENT_NAV_NEXT_UNREAD,
    EVENT_NAV_PREVIOUS,
    EVENT_NAV_NEXT,
    EVENT_NAV_END,
    EVENT_NAV_PREV_MENTION,
    EVENT_NAV_NEXT_MENTION,
    EVENT_NAV_ARCHIVE_TOGGLE,
    EVENT_NAV_PIN_TOGGLE,
    EVENT_NAV_VERSION_HISTORY
  };

  // ---- Depth navigation events (from <wavy-depth-nav-bar>) ----
  public static final String EVENT_DEPTH_DRILL_IN = "wavy-depth-drill-in";
  public static final String EVENT_DEPTH_UP = "wavy-depth-up";
  public static final String EVENT_DEPTH_ROOT = "wavy-depth-root";
  public static final String EVENT_DEPTH_JUMP_TO_CRUMB = "wavy-depth-jump-to-crumb";

  /** Depth-nav telemetry event set (the up/root/jump-to-crumb chrome clicks). */
  public static final String[] EVENTS_DEPTH_CHROME = {
    EVENT_DEPTH_UP, EVENT_DEPTH_ROOT, EVENT_DEPTH_JUMP_TO_CRUMB
  };

  // ---- Shell / compose body-level events ----
  public static final String EVENT_NEW_WAVE_REQUESTED = "wavy-new-wave-requested";
  public static final String EVENT_NEW_WITH_PARTICIPANTS = "wave-new-with-participants-requested";
  public static final String EVENT_ADD_PARTICIPANT = "wave-add-participant-requested";
  public static final String EVENT_PUBLICITY_TOGGLE = "wave-publicity-toggle-requested";
  public static final String EVENT_ROOT_LOCK_TOGGLE = "wave-root-lock-toggle-requested";
  public static final String EVENT_BACK_TO_INBOX_CLICKED = "wavy-back-to-inbox-clicked";
  public static final String EVENT_SELECTED_WAVE_REFRESH = "wavy-selected-wave-refresh-requested";

  // ---- Blip / read-surface events ----
  public static final String EVENT_BLIP_TASK_TOGGLED = "wave-blip-task-toggled";

  // ---- Lifecycle / page events ----
  public static final String EVENT_PAGE_HIDE = "pagehide";
  public static final String EVENT_SCROLL = "scroll";

  // ---- Custom element tags ----
  public static final String CUSTOM_ELEMENT_DEPTH_NAV_BAR = "wavy-depth-nav-bar";

  // ---- data-j2cl-* host attributes ----
  public static final String DATA_ATTR_INLINE_RICH_COMPOSER = "data-j2cl-inline-rich-composer";
  public static final String DATA_ATTR_READ_SURFACE_PREVIEW = "data-j2cl-read-surface-preview";
  public static final String DATA_ATTR_ROOT_RETURN_TARGET = "data-j2cl-root-return-target";
  public static final String DATA_ATTR_ROOT_SIGNIN = "data-j2cl-root-signin";
  public static final String DATA_ATTR_ROOT_SIGNOUT = "data-j2cl-root-signout";
}
