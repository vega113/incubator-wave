/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.waveprotocol.box.server.rpc;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

public final class BlipCssLinkStyleTest {
  @Test
  public void linksUseWavyAccentTreatmentBeforeHover() throws Exception {
    String css = loadCss();

    assertContains(css, ".contentContainer a {");
    assertContains(css, "text-decoration-style: wavy;");
    assertContains(css, "text-decoration-color: #00b4d8;");
    assertContains(css, "box-shadow: inset 0 -0.22em 0 rgba(0,180,216,0.22);");
  }

  @Test
  public void visitedLinksKeepDistinctAffordance() throws Exception {
    String css = loadCss();
    String visitedRule = extractRule(css, ".contentContainer a:visited");

    assertTrue(visitedRule.contains("color: #6b5b95;"));
    assertTrue(visitedRule.contains("text-decoration-color: #8b7db5;"));
    assertFalse(visitedRule.contains("box-shadow"));
  }

  private static String loadCss() throws IOException {
    try (InputStream stream =
        BlipCssLinkStyleTest.class.getResourceAsStream(
            "/org/waveprotocol/wave/client/wavepanel/view/dom/full/Blip.css")) {
      assertNotNull("Blip.css should be available on the test classpath", stream);
      byte[] bytes = stream.readAllBytes();
      String css = new String(bytes, StandardCharsets.UTF_8);
      return css;
    }
  }

  private static void assertContains(String css, String expectedSnippet) {
    assertTrue("Expected Blip.css to contain: " + expectedSnippet, css.contains(expectedSnippet));
  }

  private static String extractRule(String css, String selector) {
    int selectorIndex = css.indexOf(selector);
    assertTrue("Expected Blip.css to contain selector: " + selector, selectorIndex >= 0);

    int ruleStart = css.indexOf('{', selectorIndex);
    int ruleEnd = css.indexOf('}', ruleStart);
    assertTrue("Expected rule block for selector: " + selector, ruleStart >= 0 && ruleEnd > ruleStart);

    return css.substring(ruleStart + 1, ruleEnd);
  }
}
