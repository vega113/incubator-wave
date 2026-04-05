package org.waveprotocol.box.common;

import junit.framework.TestCase;

public class ReconnectReloadPolicyTest extends TestCase {

  public void testReloadsAfterLongDisconnect() {
    assertTrue(ReconnectReloadPolicy.shouldReloadAfterProlongedDisconnect(5001));
  }

  public void testDoesNotReloadForShortDisconnect() {
    assertFalse(ReconnectReloadPolicy.shouldReloadAfterProlongedDisconnect(5000));
  }
}
