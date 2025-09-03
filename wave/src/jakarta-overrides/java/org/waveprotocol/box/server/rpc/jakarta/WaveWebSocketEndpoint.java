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

import com.google.protobuf.Descriptors;
import com.google.protobuf.Descriptors.MethodDescriptor;
import com.google.protobuf.Message;
import com.google.protobuf.Service;
import jakarta.websocket.CloseReason;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;
import org.waveprotocol.box.common.comms.WaveClientRpc.ProtocolAuthenticate;
import org.waveprotocol.box.common.comms.WaveClientRpc.ProtocolAuthenticationResult;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.rpc.MessageExpectingChannel;
import org.waveprotocol.box.server.rpc.ProtoCallback;
import org.waveprotocol.box.server.rpc.ServerRpcController;
import org.waveprotocol.box.server.rpc.JakartaRpcFactories;
import org.waveprotocol.box.server.rpc.Rpc;
import org.waveprotocol.box.server.rpc.WebSocketChannel;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.util.logging.Log;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Jakarta WebSocket endpoint bound at "/socket" that bridges frames into
 * Wave's WebSocketChannel, forwarding parsed protobufs to a dispatcher.
 */
@ServerEndpoint("/socket")
public class WaveWebSocketEndpoint {
  private static final Log LOG = Log.get(WaveWebSocketEndpoint.class);

  private volatile EndpointConnection connection;
  private volatile WebSocketChannel channel;
  private volatile Executor executor;
  private volatile org.waveprotocol.box.server.authentication.SessionManager sessionManager;
  private volatile Map<Descriptors.Descriptor, Object[]> services; // value: {Service, MethodDescriptor}
  private final Object depLock = new Object();
  private volatile boolean depsSet = false;
  private final java.util.concurrent.atomic.AtomicInteger parseErrors = new java.util.concurrent.atomic.AtomicInteger(0);

  public WaveWebSocketEndpoint() {}

  public void setDependencies(Executor executor,
                              org.waveprotocol.box.server.authentication.SessionManager sessionManager,
                              Map<Descriptors.Descriptor, Object[]> services) {
    synchronized (depLock) {
      if (depsSet) return;
      if (executor == null || sessionManager == null || services == null) {
        throw new IllegalArgumentException("setDependencies received nulls");
      }
      // Defensive copy and publish immutably
      this.executor = executor;
      this.sessionManager = sessionManager;
      this.services = java.util.Collections.unmodifiableMap(new java.util.HashMap<>(services));
      depsSet = true;
    }
  }

  @OnOpen
  public void onOpen(Session session) {
    if (!depsSet) {
      try { session.close(new CloseReason(CloseReason.CloseCodes.CANNOT_ACCEPT, "Endpoint not initialized")); } catch (Exception ignore) {}
      return;
    }
    if (session == null) {
      LOG.warning("onOpen called with null session; ignoring");
      return;
    }
    if (!session.isOpen()) {
      LOG.warning("onOpen received closed session; ignoring");
      return;
    }
    final Session sref = session;
    channel = new WebSocketChannel(new ProtoCallback() {
      @Override
      public void message(int sequenceNo, Message message) {
        EndpointConnection c = connection;
        if (c != null) c.onMessage(sequenceNo, message);
      }
    }) {
      @Override
      protected void sendMessageString(String data) {
        if (sref != null && sref.isOpen()) {
          sref.getAsyncRemote().sendText(data);
        } else {
          LOG.warning("Attempted to send on closed/null session; dropping frame");
        }
      }
    };
    connection = new EndpointConnection(channel, executor, sessionManager, services);
    connection.registerExpectedMessages(channel);
  }

  @OnMessage
  public void onMessage(Session session, String data) {
    if (session == null || !session.isOpen()) {
      LOG.fine("onMessage on null/closed session; ignoring");
      return;
    }
    if (channel == null) {
      LOG.fine("onMessage received before channel init; ignoring");
      return;
    }
    try {
      // Delegate full parsing and dispatch to the channel/connection pair.
      channel.handleMessageString(data);
    } catch (Throwable t) {
      int errs = parseErrors.incrementAndGet();
      LOG.warning("WebSocket message processing failed (count=" + errs + ")", t);
      if (errs > 1) {
        try {
          session.close(new CloseReason(CloseReason.CloseCodes.CANNOT_ACCEPT, "Malformed message"));
        } catch (Exception ignore) {}
      }
    }
  }

  @OnClose
  public void onClose() {
    connection = null;
    channel = null;
  }

  /** Per-connection dispatcher, mirroring legacy ServerRpcProvider.Connection logic. */
  static final class EndpointConnection {
    private final Map<Integer, ServerRpcController> activeRpcs = new ConcurrentHashMap<>();
    private final WebSocketChannel channel;
    private final Executor executor;
    private final org.waveprotocol.box.server.authentication.SessionManager sessionManager;
    private final Map<Descriptors.Descriptor, Object[]> services; // {Service, MethodDescriptor}
    private ParticipantId loggedInUser;

    EndpointConnection(WebSocketChannel channel, Executor executor,
                       org.waveprotocol.box.server.authentication.SessionManager sessionManager,
                       Map<Descriptors.Descriptor, Object[]> services) {
      this.channel = channel;
      this.executor = executor;
      this.sessionManager = sessionManager;
      this.services = services;
      this.loggedInUser = null;
    }

    void registerExpectedMessages(MessageExpectingChannel ch) {
      Map<Descriptors.Descriptor, Object[]> svc = this.services;
      synchronized (svc) {
        for (Object[] sm : svc.values()) {
          Service svcObj = (Service) sm[0];
          MethodDescriptor m = (MethodDescriptor) sm[1];
          ch.expectMessage(svcObj.getRequestPrototype(m));
        }
      }
      ch.expectMessage(Rpc.CancelRpc.getDefaultInstance());
    }

    private ParticipantId authenticate(String token) {
      SessionManager sm = this.sessionManager;
      javax.servlet.http.HttpSession httpSession = sm.getSessionFromToken(token);
      return sm.getLoggedInUser(httpSession);
    }

    void onMessage(final int sequenceNo, Message message) {
      if (message instanceof Rpc.CancelRpc) {
        final ServerRpcController controller = activeRpcs.get(sequenceNo);
        if (controller != null) {
          controller.cancel();
        } else {
          throw new IllegalStateException("Trying to cancel an RPC that is not active!");
        }
        return;
      }

      if (message instanceof ProtocolAuthenticate) {
        ProtocolAuthenticate auth = (ProtocolAuthenticate) message;
        ParticipantId as = authenticate(auth.getToken());
        if (as == null) {
          // Authentication failed
          channel.sendMessage(sequenceNo, Rpc.RpcFinished.newBuilder().setFailed(true)
              .setErrorText("Authentication failed").build());
          return;
        }
        if (loggedInUser != null && !loggedInUser.equals(as)) {
          throw new IllegalStateException("Session already authenticated as a different user");
        }
        loggedInUser = as;
        channel.sendMessage(sequenceNo, ProtocolAuthenticationResult.getDefaultInstance());
        return;
      }

      Object[] arr = services.get(message.getDescriptorForType());
      if (arr == null) {
        throw new IllegalStateException("Unknown message type: " + message.getDescriptorForType().getFullName());
      }
      Service svcObj = (Service) arr[0];
      MethodDescriptor m = (MethodDescriptor) arr[1];

      if (activeRpcs.containsKey(sequenceNo)) {
        throw new IllegalStateException("Sequence already in use: " + sequenceNo);
      }

      final ServerRpcController controller = JakartaRpcFactories.newServerRpcController(
          message, svcObj, m, loggedInUser,
          result -> {
            boolean finished = (result instanceof Rpc.RpcFinished)
                || !m.getOptions().getExtension(Rpc.isStreamingRpc);
            if (finished) {
              activeRpcs.remove(sequenceNo);
            }
            channel.sendMessage(sequenceNo, result);
          });

      activeRpcs.put(sequenceNo, controller);
      executor.execute(controller);
    }
  }
}
