package org.waveprotocol.box.j2cl.i18n;

import elemental2.dom.DomGlobal;
import jsinterop.annotations.JsFunction;
import jsinterop.base.Js;

/**
 * #1277: one-way bridge from the J2CL Java surfaces to the Lit i18n catalog.
 *
 * <p>The Lit shell publishes {@code window.__j2clI18n.t(key, fallback)} (see
 * {@code j2cl/lit/src/i18n/j2cl-bridge.js}); this facade forwards every dynamic
 * status/error string through it so translations live only in the JS catalogs
 * (en.js/de.js), never duplicated into Java.
 *
 * <p>Resolution is defensive: if the Lit bundle has not registered the bridge
 * yet (or at all — e.g. a J2CL unit test with no Lit shell), {@link #t} returns
 * the supplied English fallback, so a string can never render as {@code null}
 * and existing English-asserting tests keep passing.
 */
public final class J2clI18n {

  private J2clI18n() {}

  @JsFunction
  private interface TranslateFn {
    String translate(String key, String fallback);
  }

  /**
   * Resolves {@code key} through the Lit catalog, falling back to {@code
   * fallback} (the mandatory English literal) when the bridge is unavailable or
   * the key is missing.
   */
  public static String t(String key, String fallback) {
    String safeFallback = fallback == null ? "" : fallback;
    if (key == null || key.isEmpty()) {
      return safeFallback;
    }
    TranslateFn translate = resolveTranslateFn();
    if (translate == null) {
      return safeFallback;
    }
    try {
      String result = translate.translate(key, safeFallback);
      return result == null ? safeFallback : result;
    } catch (RuntimeException | Error e) {
      // Mirror the other JS-interop guards in this repo: a JS-thrown value can
      // translate to a Java Error (not just RuntimeException); either way the
      // chip/status must fall back to English rather than break rendering.
      return safeFallback;
    }
  }

  /**
   * Convenience for messages with a single {@code {param}} placeholder: resolves
   * {@code key} (whose catalog value should contain {@code placeholder}) and
   * substitutes {@code value}.
   */
  public static String format(String key, String fallback, String placeholder, String value) {
    String template = t(key, fallback);
    if (placeholder == null || placeholder.isEmpty()) {
      return template;
    }
    return template.replace(placeholder, value == null ? "" : value);
  }

  private static TranslateFn resolveTranslateFn() {
    Object window = DomGlobal.window;
    if (window == null) {
      return null;
    }
    Object bridge = Js.asPropertyMap(window).get("__j2clI18n");
    if (bridge == null) {
      return null;
    }
    Object translate = Js.asPropertyMap(bridge).get("t");
    if (translate == null) {
      return null;
    }
    return Js.uncheckedCast(translate);
  }
}
