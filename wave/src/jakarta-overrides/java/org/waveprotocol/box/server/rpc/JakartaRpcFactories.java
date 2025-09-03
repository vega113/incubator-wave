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
package org.waveprotocol.box.server.rpc;

import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;
import com.google.protobuf.RpcCallback;
import com.google.protobuf.Service;
import org.waveprotocol.wave.model.wave.ParticipantId;

/**
 * Jakarta bridge factories for constructs with package-private constructors
 * in the legacy package. This lives in the same package to invoke
 * package-private constructors and expose public factory methods.
 *
 * Design and compatibility notes:
 * - Role: Provide minimal, stable creation of components needed by the
 *   Jakarta endpoint path without widening visibility of legacy classes.
 * - API stability: newServerRpcController is intended to be binary/source
 *   compatible with the legacy ServerRpcControllerImpl constructor signature
 *   used on the server path. It returns the interface type
 *   ServerRpcController to keep the call-site decoupled from implementation
 *   details.
 * - Scope: Only factory creation – no extra behavior or lifecycle.
 * - Future: Once the Jakarta path fully replaces the legacy server path, this
 *   factory can be removed and callers can be refactored to DI.
 */
public final class JakartaRpcFactories {
  private JakartaRpcFactories() {}

  public static ServerRpcController newServerRpcController(
      Message requestMessage,
      Service service,
      Descriptors.MethodDescriptor method,
      ParticipantId loggedInUser,
      RpcCallback<Message> callback) {
    return new ServerRpcControllerImpl(requestMessage, service, method, loggedInUser, callback);
  }
}
