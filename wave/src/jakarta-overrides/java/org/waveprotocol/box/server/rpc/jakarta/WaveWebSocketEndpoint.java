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
package org.waveprotocol.box.server.rpc.jakarta;

import jakarta.websocket.OnClose;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;
import org.waveprotocol.wave.util.logging.Log;

import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Minimal Jakarta WebSocket endpoint bound at "/socket" used to satisfy
 * compile-time references during the servlet/EE10 migration. The real
 * per-connection dispatch remains in the legacy path until the full
 * migration lands. This endpoint accepts connections and drops frames.
 */
@ServerEndpoint("/socket")
public class WaveWebSocketEndpoint {
  private static final Log LOG = Log.get(WaveWebSocketEndpoint.class);

  private volatile boolean depsSet = false;

  // Matches the signature invoked via reflection from ServerRpcProvider
  public void setDependencies(Executor executor,
                              org.waveprotocol.box.server.authentication.SessionManager sessionManager,
                              Map<com.google.protobuf.Descriptors.Descriptor, Object[]> services) {
    // Accept dependencies but do not wire a dispatcher in this minimal stub
    depsSet = true;
  }

  @OnOpen
  public void onOpen(Session session) {
    if (!depsSet) {
      try { session.close(); } catch (Exception ignore) {}
      LOG.warning("WebSocket open rejected: dependencies not set");
      return;
    }
    LOG.fine("WebSocket opened: " + (session != null ? session.getId() : "null"));
  }

  @OnMessage
  public void onMessage(Session session, String data) {
    // Minimal stub: drop frames; future work will add per-connection dispatch.
    LOG.fine("Dropping WebSocket frame (stub)");
  }

  @OnClose
  public void onClose() {
    LOG.fine("WebSocket closed (stub)");
  }
}
