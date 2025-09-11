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

package org.waveprotocol.wave.client.wavepanel.render;

import com.google.gwt.core.client.GWT;

/**
 * GWT-safe requester that relies on the ViewChannel stream to deliver
 * fragments (ProtocolFragments) rather than issuing HTTP calls.
 *
 * Rationale
 * - When the server is configured to emit fragments on the RPC stream
 *   (server.enableFetchFragmentsRpc=true), the client receives
 *   ProtocolFragments as part of updates. Those are surfaced to
 *   {@code ViewChannelImpl} and optionally to a global applier.
 * - In that mode, the renderer does not need to actively fetch via HTTP; it
 *   only needs a "requester" that participates in the shaping cycle without
 *   doing network I/O. This class fulfills that contract.
 *
 * Behavior
 * - {@link #fetchRange(int, int, Callback)} succeeds immediately and logs at
 *   trace-level when GWT logging is enabled.
 */
public final class ViewChannelFragmentRequester implements FragmentRequester {

  @Override
  public void fetchRange(int viewportTop, int viewportBottom, Callback cb) {
    // In ViewChannel-driven mode, server-side emission occurs independently of
    // this call (on update boundaries). We do not perform any network I/O.
    try {
      GWT.log("ViewChannelFragmentRequester: noop fetch top=" + viewportTop + ", bottom=" + viewportBottom);
    } catch (Throwable ignored) {
      // GWT log may not be available in some contexts
    }
    if (cb != null) cb.onSuccess();
  }
}

