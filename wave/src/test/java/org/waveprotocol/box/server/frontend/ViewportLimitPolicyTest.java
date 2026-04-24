package org.waveprotocol.box.server.frontend;

import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public final class ViewportLimitPolicyTest {
  private int previousDefaultLimit;
  private int previousMaxLimit;

  @Before
  public void setUp() {
    previousDefaultLimit = ViewportLimitPolicy.getDefaultLimit();
    previousMaxLimit = ViewportLimitPolicy.getMaxLimit();
  }

  @After
  public void tearDown() {
    ViewportLimitPolicy.setLimits(previousDefaultLimit, previousMaxLimit);
  }

  @Test
  public void missingOrInvalidLimitFallsBackToConfiguredDefault() {
    ViewportLimitPolicy.setLimits(7, 20);

    assertEquals(7, ViewportLimitPolicy.resolveLimit(null));
    assertEquals(7, ViewportLimitPolicy.resolveLimit("not-a-number"));
    assertEquals(7, ViewportLimitPolicy.resolveLimit("-2"));
    assertEquals(7, ViewportLimitPolicy.resolveLimit("0"));
  }

  @Test
  public void requestedLimitIsClampedToConfiguredMax() {
    ViewportLimitPolicy.setLimits(5, 12);

    assertEquals(8, ViewportLimitPolicy.resolveLimit("8"));
    assertEquals(10, ViewportLimitPolicy.resolveLimit(" 10 "));
    assertEquals(12, ViewportLimitPolicy.resolveLimit("100"));
  }

  @Test
  public void maxLimitCannotDropBelowDefaultLimit() {
    ViewportLimitPolicy.setLimits(9, 3);

    assertEquals(9, ViewportLimitPolicy.getDefaultLimit());
    assertEquals(9, ViewportLimitPolicy.getMaxLimit());
    assertEquals(9, ViewportLimitPolicy.resolveLimit("100"));
  }
}
