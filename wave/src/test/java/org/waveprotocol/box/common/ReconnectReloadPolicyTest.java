package org.waveprotocol.box.common;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ReconnectReloadPolicyTest {

  @Test
  public void doesNotReloadBeforeTheThirtySecondThresholdWhenNoWaveIsOpen() {
    assertFalse(
        ReconnectReloadPolicy.shouldReloadAfterProlongedDisconnect(false, 29999d));
  }

  @Test
  public void doesNotReloadWhenAWaveIsOpen() {
    assertFalse(
        ReconnectReloadPolicy.shouldReloadAfterProlongedDisconnect(true, 30001d));
  }

  @Test
  public void reloadsAfterTheThirtySecondThresholdWhenNoWaveIsOpen() {
    assertTrue(
        ReconnectReloadPolicy.shouldReloadAfterProlongedDisconnect(false, 30001d));
  }
}
