package org.waveprotocol.box.common;

import junit.framework.TestCase;

public class ReconnectReloadPolicyTest extends TestCase {

  public void testReloadsAfterLongDisconnectWhenNoWaveIsOpen() {
    assertTrue(ReconnectReloadPolicy.shouldReloadAfterProlongedDisconnect(false, 5001));
  }

  public void testDoesNotReloadWhenWaveIsOpen() {
    assertFalse(ReconnectReloadPolicy.shouldReloadAfterProlongedDisconnect(true, 5001));
  }

  public void testDoesNotReloadForShortDisconnect() {
    assertFalse(ReconnectReloadPolicy.shouldReloadAfterProlongedDisconnect(false, 5000));
  }
}
