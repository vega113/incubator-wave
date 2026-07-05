// #1277: expose the Lit i18n catalog to the J2CL (Java -> JS) sidecar bundle.
//
// The J2CL sidecar is a separate bundle on the same page and cannot import
// these ES modules directly, so we publish a tiny stable surface on the global
// object. The Java-side `J2clI18n` bridge calls `window.__j2clI18n.t(key,
// fallback)` for every dynamic status/error string. The catalog (en.js/de.js
// + `t()`) stays the single source of truth — no string table is duplicated
// into Java, and locale resolution/subscription lives here in JS.
import { t } from "./t.js";
import { getLocale, setLocale, subscribe } from "./locale.js";

export const J2CL_I18N_GLOBAL = "__j2clI18n";

/**
 * Install the i18n bridge on `target` (defaults to `window`). Idempotent.
 * @param {object} [target] host object to attach the bridge to.
 */
export function installI18nBridge(target) {
  const host = target ?? (typeof window !== "undefined" ? window : undefined);
  if (!host) return undefined;
  const bridge = { t, getLocale, setLocale, subscribe };
  host[J2CL_I18N_GLOBAL] = bridge;
  return bridge;
}
