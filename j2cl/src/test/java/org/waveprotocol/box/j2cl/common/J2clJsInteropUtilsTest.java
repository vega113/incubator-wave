package org.waveprotocol.box.j2cl.common;

import com.google.j2cl.junit.apt.J2clTestInput;
import elemental2.dom.DomGlobal;
import elemental2.dom.HTMLElement;
import java.util.HashMap;
import java.util.Map;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

@J2clTestInput(J2clJsInteropUtilsTest.class)
public class J2clJsInteropUtilsTest {

  private HTMLElement host;

  @After
  public void tearDown() {
    if (host != null && host.parentElement != null) {
      host.parentElement.removeChild(host);
    }
    host = null;
    J2clJsInteropUtils.setFailureListener(null);
  }

  @Test
  public void failureListenerObservesSwallowedParseFailure() {
    final String[] captured = new String[1];
    J2clJsInteropUtils.setFailureListener((context, cause) -> captured[0] = context);

    Assert.assertNull(J2clJsInteropUtils.safeParseJsonObject("{not json"));

    Assert.assertNotNull("listener should have observed the swallowed failure", captured[0]);
    Assert.assertTrue(captured[0].contains("safeParseJsonObject"));
  }

  @Test
  public void validParseDoesNotNotifyFailureListener() {
    final int[] count = new int[1];
    J2clJsInteropUtils.setFailureListener((context, cause) -> count[0]++);

    J2clJsInteropUtils.safeParseJsonObject("{\"a\":1}");

    Assert.assertEquals(0, count[0]);
  }

  // ---- property-map reads (JVM + browser) ----

  @Test
  public void safeGetStringReadsOrFallsBack() {
    Map<String, Object> map = new HashMap<String, Object>();
    map.put("a", "hello");
    map.put("n", 5);
    Assert.assertEquals("hello", J2clJsInteropUtils.safeGetString(map, "a", "fb"));
    Assert.assertEquals("fb", J2clJsInteropUtils.safeGetString(map, "n", "fb"));
    Assert.assertEquals("fb", J2clJsInteropUtils.safeGetString(map, "missing", "fb"));
    Assert.assertEquals("fb", J2clJsInteropUtils.safeGetString(null, "a", "fb"));
  }

  @Test
  public void safeGetBooleanAcceptsBooleanAndStrings() {
    Map<String, Object> map = new HashMap<String, Object>();
    map.put("b", Boolean.TRUE);
    map.put("s", "false");
    map.put("junk", "maybe");
    Assert.assertTrue(J2clJsInteropUtils.safeGetBoolean(map, "b", false));
    Assert.assertFalse(J2clJsInteropUtils.safeGetBoolean(map, "s", true));
    Assert.assertTrue(J2clJsInteropUtils.safeGetBoolean(map, "junk", true));
    Assert.assertFalse(J2clJsInteropUtils.safeGetBoolean(map, "missing", false));
  }

  @Test
  public void safeGetIntAndLongCoerceAndFallBack() {
    Map<String, Object> map = new HashMap<String, Object>();
    map.put("i", 7);
    map.put("s", "42");
    map.put("bad", "x");
    Assert.assertEquals(7, J2clJsInteropUtils.safeGetInt(map, "i", -1));
    Assert.assertEquals(42, J2clJsInteropUtils.safeGetInt(map, "s", -1));
    Assert.assertEquals(-1, J2clJsInteropUtils.safeGetInt(map, "bad", -1));
    Assert.assertEquals(-1, J2clJsInteropUtils.safeGetInt(map, "missing", -1));
    Assert.assertEquals(99L, J2clJsInteropUtils.safeGetLong(map, "missing", 99L));
    map.put("l", "9000000000");
    Assert.assertEquals(9000000000L, J2clJsInteropUtils.safeGetLong(map, "l", -1L));
  }

  // ---- JSON parsing (JVM + browser) ----

  @Test
  public void safeParseJsonObjectReturnsMapForValidJson() {
    Map<String, Object> parsed =
        J2clJsInteropUtils.safeParseJsonObject("{\"address\":\"user@example.com\"}");
    Assert.assertNotNull(parsed);
    Assert.assertEquals("user@example.com", parsed.get("address"));
  }

  @Test
  public void safeParseJsonObjectReturnsNullForMalformedOrEmpty() {
    Assert.assertNull(J2clJsInteropUtils.safeParseJsonObject("{not json"));
    Assert.assertNull(J2clJsInteropUtils.safeParseJsonObject("[1,2,3]"));
    Assert.assertNull(J2clJsInteropUtils.safeParseJsonObject(""));
    Assert.assertNull(J2clJsInteropUtils.safeParseJsonObject(null));
  }

  // ---- requireNonNull (JVM + browser) ----

  @Test
  public void requireNonNullReturnsValueOrThrowsWithContext() {
    Assert.assertEquals("x", J2clJsInteropUtils.requireNonNull("x", "thing"));
    try {
      J2clJsInteropUtils.requireNonNull(null, "bootstrap.socket");
      Assert.fail("Expected J2clInteropException");
    } catch (J2clInteropException expected) {
      Assert.assertTrue(expected.getMessage().contains("bootstrap.socket"));
    }
  }

  // ---- DOM (browser only) ----

  @Test
  public void requireElementFindsOrThrows() {
    assumeBrowserDom();
    host = createHostWithChild();
    HTMLElement child = J2clJsInteropUtils.requireElement(host, ".child", "test");
    Assert.assertNotNull(child);
    Assert.assertEquals("child", child.getAttribute("data-role"));
    try {
      J2clJsInteropUtils.requireElement(host, ".nope", "test-ctx");
      Assert.fail("Expected J2clInteropException");
    } catch (J2clInteropException expected) {
      Assert.assertTrue(expected.getMessage().contains(".nope"));
      Assert.assertTrue(expected.getMessage().contains("test-ctx"));
    }
    try {
      J2clJsInteropUtils.requireElement(null, ".child", "null-host");
      Assert.fail("Expected J2clInteropException for null host");
    } catch (J2clInteropException expected) {
      Assert.assertTrue(expected.getMessage().contains("null-host"));
    }
  }

  @Test
  public void queryOptionalElementReturnsNullWhenAbsent() {
    assumeBrowserDom();
    host = createHostWithChild();
    Assert.assertNotNull(J2clJsInteropUtils.queryOptionalElement(host, ".child"));
    Assert.assertNull(J2clJsInteropUtils.queryOptionalElement(host, ".nope"));
    Assert.assertNull(J2clJsInteropUtils.queryOptionalElement(null, ".child"));
  }

  private static void assumeBrowserDom() {
    Assume.assumeTrue(DomGlobal.document != null && DomGlobal.document.body != null);
  }

  private HTMLElement createHostWithChild() {
    HTMLElement h = (HTMLElement) DomGlobal.document.createElement("div");
    HTMLElement child = (HTMLElement) DomGlobal.document.createElement("span");
    child.className = "child";
    child.setAttribute("data-role", "child");
    h.appendChild(child);
    DomGlobal.document.body.appendChild(h);
    return h;
  }
}
