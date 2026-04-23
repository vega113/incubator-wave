package org.waveprotocol.box.server.rpc;

import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.waveprotocol.box.server.frontend.ViewportLimitPolicy;
import org.waveprotocol.wave.concurrencycontrol.channel.FragmentsMetrics;

public final class FragmentsServletViewportLimitTest {
  private int previousDefaultLimit;
  private int previousMaxLimit;
  private boolean previousMetricsEnabled;
  private long previousClampApplied;

  @Before
  public void setUp() {
    previousDefaultLimit = ViewportLimitPolicy.getDefaultLimit();
    previousMaxLimit = ViewportLimitPolicy.getMaxLimit();
    previousMetricsEnabled = FragmentsMetrics.isEnabled();
    previousClampApplied = FragmentsMetrics.j2clViewportClampApplied.get();
    FragmentsMetrics.setEnabled(false);
    FragmentsMetrics.j2clViewportClampApplied.set(0L);
  }

  @After
  public void tearDown() {
    ViewportLimitPolicy.setLimits(previousDefaultLimit, previousMaxLimit);
    FragmentsMetrics.j2clViewportClampApplied.set(previousClampApplied);
    FragmentsMetrics.setEnabled(previousMetricsEnabled);
  }

  @Test
  public void servletUsesSharedViewportLimitPolicy() {
    ViewportLimitPolicy.setLimits(6, 11);

    assertEquals(6, FragmentsServlet.resolveLimitForRequest(null));
    assertEquals(6, FragmentsServlet.resolveLimitForRequest("invalid"));
    assertEquals(6, FragmentsServlet.resolveLimitForRequest("0"));
    assertEquals(9, FragmentsServlet.resolveLimitForRequest("9"));
    assertEquals(11, FragmentsServlet.resolveLimitForRequest("90"));
  }

  @Test
  public void servletClampMetricIsScopedToJ2clMarkedRequests() {
    ViewportLimitPolicy.setLimits(6, 11);
    FragmentsMetrics.setEnabled(true);

    assertEquals(11, FragmentsServlet.resolveLimitForRequest("90", false));
    assertEquals(0L, FragmentsMetrics.j2clViewportClampApplied.get());

    assertEquals(11, FragmentsServlet.resolveLimitForRequest("90", true));
    assertEquals(1L, FragmentsMetrics.j2clViewportClampApplied.get());

    assertEquals(6, FragmentsServlet.resolveLimitForRequest("invalid", true));
    assertEquals(1L, FragmentsMetrics.j2clViewportClampApplied.get());
  }
}
