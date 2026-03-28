package org.waveprotocol.box.server.rpc;

import junit.framework.TestCase;

public class HtmlRendererTopBarTest extends TestCase {
  public void testRenderTopBarIncludesRobotDashboardLink() {
    String topBarHtml = HtmlRenderer.renderTopBar("vega", "example.com", "user");

    assertTrue(topBarHtml.contains("Robot &amp; Data API"));
    assertTrue(topBarHtml.contains("href=\"/account/robots\""));
  }

  public void testRenderTopBarGroupsMenuSections() {
    String topBarHtml = HtmlRenderer.renderTopBar("vega", "example.com", "admin");

    assertTrue(topBarHtml.contains("section-label\">Account"));
    assertTrue(topBarHtml.contains("section-label\">Automation"));
    assertTrue(topBarHtml.contains("section-label\">Product"));
    assertTrue(topBarHtml.contains("section-label\">Legal"));
    assertTrue(topBarHtml.contains("href=\"/admin\""));
  }
}
