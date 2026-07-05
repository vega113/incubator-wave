package org.waveprotocol.box.j2cl.common;

import com.google.j2cl.junit.apt.J2clTestInput;
import java.util.ArrayList;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

@J2clTestInput(J2clDisposeRegistryTest.class)
public class J2clDisposeRegistryTest {

  @Test
  public void destroyRunsTrackedDisposersInReverseOrder() {
    J2clDisposeRegistry registry = new J2clDisposeRegistry();
    List<String> order = new ArrayList<String>();
    registry.track(() -> order.add("first"));
    registry.track(() -> order.add("second"));

    registry.destroy();

    Assert.assertEquals(java.util.Arrays.asList("second", "first"), order);
    Assert.assertTrue(registry.isDestroyed());
  }

  @Test
  public void destroyIsIdempotent() {
    J2clDisposeRegistry registry = new J2clDisposeRegistry();
    int[] runs = {0};
    registry.track(() -> runs[0]++);

    registry.destroy();
    registry.destroy();

    Assert.assertEquals(1, runs[0]);
  }

  @Test
  public void trackAfterDestroyRunsImmediately() {
    J2clDisposeRegistry registry = new J2clDisposeRegistry();
    registry.destroy();
    int[] runs = {0};

    registry.track(() -> runs[0]++);

    Assert.assertEquals("late registration must be disposed immediately", 1, runs[0]);
  }

  @Test
  public void aThrowingDisposerDoesNotAbortTeardown() {
    J2clDisposeRegistry registry = new J2clDisposeRegistry();
    int[] laterRan = {0};
    // Registered first so it runs LAST (reverse order); the throwing disposer
    // registered second runs first and must not prevent this one.
    registry.track(() -> laterRan[0]++);
    registry.track(
        () -> {
          throw new RuntimeException("boom");
        });

    registry.destroy();

    Assert.assertEquals(1, laterRan[0]);
  }

  @Test
  public void nullDisposerIsIgnored() {
    J2clDisposeRegistry registry = new J2clDisposeRegistry();
    registry.track(null);
    registry.destroy(); // must not throw
    Assert.assertTrue(registry.isDestroyed());
  }
}
