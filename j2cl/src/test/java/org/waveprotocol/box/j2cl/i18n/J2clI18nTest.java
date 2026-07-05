package org.waveprotocol.box.j2cl.i18n;

import com.google.j2cl.junit.apt.J2clTestInput;
import elemental2.dom.DomGlobal;
import jsinterop.annotations.JsFunction;
import jsinterop.base.Js;
import jsinterop.base.JsPropertyMap;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

@J2clTestInput(J2clI18nTest.class)
public class J2clI18nTest {

  @JsFunction
  private interface StubTranslate {
    String translate(String key, String fallback);
  }

  @Before
  public void clearBridge() {
    removeBridge();
  }

  @After
  public void tearDown() {
    removeBridge();
  }

  // ---- Tests that work on both the JVM and the browser (no bridge installed;
  // a null window resolves to the English fallback). ----

  @Test
  public void returnsFallbackWhenBridgeAbsent() {
    Assert.assertEquals("English fallback", J2clI18n.t("some.key", "English fallback"));
  }

  @Test
  public void nullOrEmptyKeyReturnsFallback() {
    Assert.assertEquals("fallback", J2clI18n.t(null, "fallback"));
    Assert.assertEquals("fallback", J2clI18n.t("", "fallback"));
  }

  @Test
  public void nullFallbackNeverReturnsNull() {
    Assert.assertEquals("", J2clI18n.t("missing.key", null));
  }

  @Test
  public void formatSubstitutesSinglePlaceholder() {
    Assert.assertEquals(
        "Showing search results for in:inbox.",
        J2clI18n.format(
            "rootStatus.searchResults",
            "Showing search results for {query}.",
            "{query}",
            "in:inbox"));
  }

  @Test
  public void formatWithNullValueSubstitutesEmpty() {
    Assert.assertEquals(
        "5 unread.", J2clI18n.format("selectedWave.unread", "{count} unread.", "{count}", "5"));
    Assert.assertEquals(
        " unread.", J2clI18n.format("selectedWave.unread", "{count} unread.", "{count}", null));
  }

  // ---- Tests that require a live bridge on window (browser only). ----

  @Test
  public void delegatesToInstalledBridge() {
    assumeBrowserDom();
    installBridge((key, fallback) -> "translated:" + key);
    Assert.assertEquals("translated:some.key", J2clI18n.t("some.key", "English fallback"));
  }

  @Test
  public void reactsToBridgeReturningDifferentValues() {
    assumeBrowserDom();
    // Simulates a locale switch: the same key resolves to a new string once the
    // catalog behind the bridge changes.
    installBridge((key, fallback) -> "de:" + key);
    Assert.assertEquals("de:rootStatus.ready", J2clI18n.t("rootStatus.ready", "Workspace is ready."));
    installBridge((key, fallback) -> fallback);
    Assert.assertEquals("Workspace is ready.", J2clI18n.t("rootStatus.ready", "Workspace is ready."));
  }

  @Test
  public void returnsFallbackWhenBridgeReturnsNull() {
    assumeBrowserDom();
    installBridge((key, fallback) -> null);
    Assert.assertEquals("English fallback", J2clI18n.t("some.key", "English fallback"));
  }

  @Test
  public void returnsFallbackWhenBridgeThrows() {
    assumeBrowserDom();
    installBridge(
        (key, fallback) -> {
          throw new RuntimeException("boom");
        });
    Assert.assertEquals("English fallback", J2clI18n.t("some.key", "English fallback"));
  }

  private static void assumeBrowserDom() {
    Assume.assumeTrue(DomGlobal.window != null);
  }

  private static void installBridge(StubTranslate translate) {
    JsPropertyMap<Object> bridge = JsPropertyMap.of();
    bridge.set("t", translate);
    Js.asPropertyMap(DomGlobal.window).set("__j2clI18n", bridge);
  }

  private static void removeBridge() {
    if (DomGlobal.window == null) {
      return;
    }
    Js.asPropertyMap(DomGlobal.window).set("__j2clI18n", null);
  }
}
