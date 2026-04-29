// G-PORT-7 (#1116): dialog-stack helper for the Esc shell shortcut.
// Walks the document for "any closeable surface", picks the topmost,
// and closes it. Returns true when something was closed so the Esc
// dispatcher knows to STOP rather than also dropping blip focus.
//
// "Topmost" priority order:
//   1. Modal dialogs (wavy-confirm-dialog, wavy-link-modal,
//      wavy-version-history) — these eat all other input on screen so
//      they MUST close first.
//   2. Anchored popovers (reaction-picker-popover,
//      reaction-authors-popover, task-metadata-popover,
//      mention-suggestion-popover, wavy-search-help) — secondary.
//   3. The wavy-profile-overlay surface (a soft modal — sits over
//      the wave but does not trap input as hard as the dialogs).
//
// Within a tier, ties resolve by document order: later-mounted
// surfaces close first, matching the natural "stack" semantics the
// user perceives.

/**
 * Selectors per tier. Each candidate must satisfy:
 *   (a) the element is currently in the DOM, and
 *   (b) the element exposes one of:
 *         - `.open` boolean property is true; or
 *         - the host has the `open` HTML attribute.
 * Closing logic:
 *   (a) prefer `host.close()` if the surface defines one (matches the
 *       <dialog> close-method convention);
 *   (b) otherwise set `host.open = false` (Lit reflects that to the
 *       attribute; popovers re-render closed).
 */
const TIERS = [
  // Tier 1 — modal dialogs.
  [
    "wavy-confirm-dialog",
    "wavy-link-modal",
    "wavy-version-history"
  ],
  // Tier 2 — anchored popovers.
  [
    "reaction-picker-popover",
    "reaction-authors-popover",
    "task-metadata-popover",
    "mention-suggestion-popover",
    "wavy-search-help"
  ],
  // Tier 3 — soft modal.
  [
    "wavy-profile-overlay"
  ]
];

function isOpen(host) {
  if (!host) return false;
  if (typeof host.open === "boolean") return host.open === true;
  if (host.hasAttribute && host.hasAttribute("open")) return true;
  return false;
}

function closeHost(host) {
  if (!host) return false;
  try {
    if (typeof host.close === "function") {
      host.close();
      return true;
    }
    if (typeof host.open === "boolean") {
      host.open = false;
      return true;
    }
    if (host.removeAttribute) {
      host.removeAttribute("open");
      return true;
    }
  } catch (e) {
    // Best-effort: a custom element may throw during close if it is
    // not yet fully upgraded. Return false so the caller can fall
    // through to the blip-focus drop path.
    return false;
  }
  return false;
}

/**
 * Collect all shadow roots reachable from `root`, depth-first. Tier-2
 * popovers (task-metadata-popover, mention-suggestion-popover) are
 * rendered inside Lit component shadow trees, so plain querySelectorAll
 * on the document misses them.
 */
function collectShadowRoots(root, result = []) {
  for (const el of root.querySelectorAll("*")) {
    if (el.shadowRoot) {
      result.push(el.shadowRoot);
      collectShadowRoots(el.shadowRoot, result);
    }
  }
  return result;
}

/**
 * Find every open closeable surface in tier order. Returns the first
 * tier that has at least one open surface, preserving document order
 * across all selectors in the tier so the most recently mounted
 * surface closes first.
 *
 * Queries both light DOM and shadow roots so tier-2 popovers rendered
 * inside component shadow trees (e.g. task-metadata-popover inside
 * wavy-task-affordance) are reachable.
 */
function findTopmostOpen(root = document) {
  const roots = [root, ...collectShadowRoots(root)];
  for (const tier of TIERS) {
    const selector = tier.join(", ");
    const open = [];
    for (const r of roots) {
      const nodes = Array.from(r.querySelectorAll(selector));
      open.push(...nodes.filter((n) => isOpen(n)));
    }
    if (open.length > 0) {
      // Last collected = topmost within the winning tier.
      return open[open.length - 1];
    }
  }
  return null;
}

/**
 * Close the topmost open dialog/popover. Returns true when something
 * closed so the Esc dispatcher can STOP (one action per keypress).
 */
export function closeTopmostDialog(root = document) {
  const target = findTopmostOpen(root);
  if (!target) return false;
  return closeHost(target);
}

export const _internalForTesting = { TIERS, isOpen, closeHost, findTopmostOpen, collectShadowRoots };
