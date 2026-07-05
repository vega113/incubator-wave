package org.waveprotocol.box.j2cl.common;

import elemental2.dom.Element;
import elemental2.dom.HTMLElement;
import java.util.Map;
import jsinterop.base.Js;
import org.waveprotocol.box.j2cl.transport.SidecarTransportCodec;

/**
 * #1272: the blessed defensive seam for every JS &lt;-&gt; Java boundary in the
 * J2CL UI — property-map reads, DOM lookups, and JSON parsing.
 *
 * <p>Direct interop (e.g. {@code Js.asPropertyMap(payload).get("foo")},
 * {@code host.querySelector(...)}, {@code parseJsonObject(...)}) turns a single
 * bad server response or missing DOM node into a Java NPE that only surfaces as
 * a cryptic minified JS console error. These helpers make the shape assumptions
 * explicit: {@code safeGet*} / {@code queryOptionalElement} / {@code
 * safeParseJsonObject} degrade gracefully (fallback / {@code null}); {@code
 * requireNonNull} / {@code requireElement} fail loudly with a
 * {@link J2clInteropException} that preserves human context so the caller can
 * report it (telemetry / notification) instead of crashing the whole surface.
 */
public final class J2clJsInteropUtils {

  private J2clJsInteropUtils() {}

  /** Reads a string at {@code key} from a parsed-JSON map or a JS object, else {@code fallback}. */
  public static String safeGetString(Object source, String key, String fallback) {
    Object value = rawGet(source, key);
    return value instanceof String ? (String) value : fallback;
  }

  /** Reads a boolean (accepts {@code "true"}/{@code "false"} strings) else {@code fallback}. */
  public static boolean safeGetBoolean(Object source, String key, boolean fallback) {
    Object value = rawGet(source, key);
    if (value instanceof Boolean) {
      return ((Boolean) value).booleanValue();
    }
    if ("true".equals(value)) {
      return true;
    }
    if ("false".equals(value)) {
      return false;
    }
    return fallback;
  }

  /** Reads an int (accepts numeric strings) else {@code fallback}. */
  public static int safeGetInt(Object source, String key, int fallback) {
    Object value = rawGet(source, key);
    if (value instanceof Number) {
      return ((Number) value).intValue();
    }
    if (value instanceof String) {
      try {
        return Integer.parseInt(((String) value).trim());
      } catch (NumberFormatException ignored) {
        return fallback;
      }
    }
    return fallback;
  }

  /** Reads a long (accepts numeric strings) else {@code fallback}. */
  public static long safeGetLong(Object source, String key, long fallback) {
    Object value = rawGet(source, key);
    if (value instanceof Number) {
      return ((Number) value).longValue();
    }
    if (value instanceof String) {
      try {
        return Long.parseLong(((String) value).trim());
      } catch (NumberFormatException ignored) {
        return fallback;
      }
    }
    return fallback;
  }

  /** Fails loudly with a context-preserving {@link J2clInteropException} when {@code value} is null. */
  public static <T> T requireNonNull(T value, String what) {
    if (value == null) {
      throw new J2clInteropException((what == null ? "value" : what) + " was null");
    }
    return value;
  }

  /**
   * Resolves a required descendant element, throwing a context-preserving
   * {@link J2clInteropException} when the host is null or the selector misses.
   */
  public static HTMLElement requireElement(Element host, String selector, String context) {
    if (host == null) {
      throw new J2clInteropException(
          "null host resolving '" + selector + "'" + contextSuffix(context));
    }
    Element found = host.querySelector(selector);
    if (found == null) {
      throw new J2clInteropException(
          "missing required element '" + selector + "'" + contextSuffix(context));
    }
    return (HTMLElement) found;
  }

  /** Resolves an optional descendant element, returning {@code null} when absent. */
  public static HTMLElement queryOptionalElement(Element host, String selector) {
    if (host == null) {
      return null;
    }
    Element found = host.querySelector(selector);
    return found == null ? null : (HTMLElement) found;
  }

  /**
   * Parses a JSON object, returning {@code null} (not throwing) on malformed
   * input — the graceful-degradation seam for bad bootstrap / fragment
   * envelopes. Callers should report the {@code null} and fall back rather than
   * crash the surface.
   */
  public static Map<String, Object> safeParseJsonObject(String text) {
    if (text == null || text.isEmpty()) {
      return null;
    }
    try {
      return SidecarTransportCodec.parseJsonObject(text);
    } catch (RuntimeException | Error e) {
      return null;
    }
  }

  private static Object rawGet(Object source, String key) {
    if (source == null || key == null) {
      return null;
    }
    if (source instanceof Map) {
      return ((Map<?, ?>) source).get(key);
    }
    // A live JS object / property map.
    try {
      return Js.asPropertyMap(source).get(key);
    } catch (RuntimeException | Error e) {
      return null;
    }
  }

  private static String contextSuffix(String context) {
    return context == null || context.isEmpty() ? "" : " (" + context + ")";
  }
}
