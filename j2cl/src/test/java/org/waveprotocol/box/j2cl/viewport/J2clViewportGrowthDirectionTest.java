package org.waveprotocol.box.j2cl.viewport;

import com.google.j2cl.junit.apt.J2clTestInput;
import org.junit.Assert;
import org.junit.Test;

@J2clTestInput(J2clViewportGrowthDirectionTest.class)
public final class J2clViewportGrowthDirectionTest {
  @Test
  public void normalizeTrimsAndCaseNormalizesBackwardDirection() {
    Assert.assertEquals(
        J2clViewportGrowthDirection.BACKWARD,
        J2clViewportGrowthDirection.normalize(" Backward "));
  }

  @Test
  public void normalizeFallsBackToForwardForMissingOrUnknownDirection() {
    Assert.assertEquals(
        J2clViewportGrowthDirection.FORWARD,
        J2clViewportGrowthDirection.normalize(null));
    Assert.assertEquals(
        J2clViewportGrowthDirection.FORWARD,
        J2clViewportGrowthDirection.normalize("sideways"));
  }
}
