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
package org.waveprotocol.box.server.jakarta;

import jakarta.websocket.CloseReason;
import jakarta.websocket.Session;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.waveprotocol.box.server.authentication.SessionManager;

import java.util.Collections;
import java.util.concurrent.Executor;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Verifies WaveWebSocketEndpoint guards on missing dependencies and does not
 * NPE when onOpen is called before DI.
 */
public class WaveWebSocketEndpointInitGuardTest {

  @Test
  public void onOpenWithoutDeps_closesSession() {
    org.waveprotocol.box.server.rpc.jakarta.WaveWebSocketEndpoint ep =
        new org.waveprotocol.box.server.rpc.jakarta.WaveWebSocketEndpoint();
    Session s = mock(Session.class);
    ep.onOpen(s);
    ArgumentCaptor<CloseReason> cr = ArgumentCaptor.forClass(CloseReason.class);
    try {
      verify(s, times(1)).close(cr.capture());
    } catch (java.io.IOException ignored) { /* Mockito signature declares throws */ }
    assertEquals(CloseReason.CloseCodes.CANNOT_ACCEPT, cr.getValue().getCloseCode());
  }

  @Test
  public void onOpenAfterDeps_ok() {
    org.waveprotocol.box.server.rpc.jakarta.WaveWebSocketEndpoint ep =
        new org.waveprotocol.box.server.rpc.jakarta.WaveWebSocketEndpoint();
    Executor direct = Runnable::run;
    SessionManager sm = mock(SessionManager.class);
    ep.setDependencies(direct, sm, Collections.emptyMap());
    Session s = mock(Session.class);
    when(s.isOpen()).thenReturn(true);
    ep.onOpen(s);
    // No close should be invoked when deps are set; channel attaches and proceeds.
    try {
      verify(s, never()).close(any());
    } catch (java.io.IOException ignored) { }
  }
}
