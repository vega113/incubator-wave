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

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.google.protobuf.Service;
import com.typesafe.config.Config;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.session.SessionHandler;
import org.eclipse.jetty.ee10.servlet.DefaultServlet;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.websocket.jakarta.server.config.JakartaWebSocketServletContainerInitializer;
import org.eclipse.jetty.server.handler.gzip.GzipHandler;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.resource.ResourceCollection;
import org.eclipse.jetty.servlet.ServletHolder;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.wave.util.logging.Log;

import javax.annotation.Nullable;
import javax.servlet.Filter;
import javax.servlet.http.HttpServlet;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Minimal Jakarta-compatible stub of ServerRpcProvider to allow compiling the
 * main module against Jetty 12 while we migrate servlet APIs to jakarta.*.
 * This stub preserves the public surface used by ServerMain and friends.
 *
 * Build-time notes
 * - Included only when building with -PjettyFamily=jakarta.
 * - Default build (javax/Jetty 9.4) continues to use the real class in src/main/java.
 *
 * Migration checklist (P5‑T3)
 * - Servlet/Filter APIs: replace javax.servlet.* with jakarta.servlet.* across server code.
 * - Context/handlers: use ee10 variants (e.g., ServletContextHandler from org.eclipse.jetty.ee10.servlet).
 * - WebSockets: migrate org.eclipse.jetty.websocket.servlet.* to Jetty 12 websocket APIs
 *   (jetty-websocket-jetty12 / ee10 websocket modules) and update WaveWebSocketServlet wiring.
 * - GZip: prefer server-side GzipHandler compatible with Jetty 12 packages.
 * - Forwarded headers, access logs, HTTPS/ALPN/HTTP2: map existing setup to Jetty 12 equivalents.
 * - Static resources and proxies: validate DefaultServlet and ProxyServlet coordinates under Jetty 12.
 * - Sessions: confirm SessionHandler and SessionDataStore APIs under Jetty 12.
 * - Remove this stub once jakarta implementation is complete and tests pass.
 */
@Singleton
public class ServerRpcProvider {
  private static final Log LOG = Log.get(ServerRpcProvider.class);
  private final Config config;
  private final SessionHandler sessionHandler;
  private Server httpServer;

  public ServerRpcProvider(InetSocketAddress[] httpAddresses,
                           String[] resourceBases, Executor threadPool,
                           SessionManager sessionManager,
                           SessionHandler sessionHandler, String sessionStoreDir,
                           boolean sslEnabled, String sslKeystorePath, String sslKeystorePassword,
                           boolean enableForwardedHeaders, boolean nativeServletRegistration,
                           boolean enableProgrammaticPoc) {
    // No-op: stub constructor
    this.config = null;
    this.sessionHandler = sessionHandler;
  }

  public ServerRpcProvider(InetSocketAddress[] httpAddresses,
                           String[] resourceBases, SessionManager sessionManager,
                           SessionHandler sessionHandler, String sessionStoreDir,
                           boolean sslEnabled, String sslKeystorePath, String sslKeystorePassword,
                           Executor executor) {
    // No-op: stub constructor
  }

  @Inject
  public ServerRpcProvider(Config config,
                           SessionManager sessionManager, SessionHandler sessionHandler,
                           @org.waveprotocol.box.server.executor.ExecutorAnnotations.ClientServerExecutor Executor executorService) {
    this.config = config;
    this.sessionHandler = sessionHandler;
  }

  public void startWebSocketServer(final Injector injector) {
    try {
      httpServer = new Server();
      // Build connectors for all configured addresses (compat with legacy)
      int added = 0;
      try {
        List<String> addrs = (config != null && config.hasPath("core.http_frontend_addresses"))
            ? config.getStringList("core.http_frontend_addresses")
            : java.util.Collections.singletonList("127.0.0.1:9898");
        for (String a : addrs) {
          if (a == null || a.isBlank() || !a.contains(":")) continue;
          int idx = a.lastIndexOf(':');
          String host = a.substring(0, idx);
          int port = Integer.parseInt(a.substring(idx + 1));
          ServerConnector c = new ServerConnector(httpServer);
          c.setHost(host);
          c.setPort(port);
          httpServer.addConnector(c);
          added++;
        }
      } catch (Exception e) {
        LOG.warning("Failed to parse core.http_frontend_addresses; falling back to 127.0.0.1:9898", e);
      }
      if (added == 0) {
        ServerConnector c = new ServerConnector(httpServer);
        c.setHost("127.0.0.1");
        c.setPort(9898);
        httpServer.addConnector(c);
      }

      ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
      context.setContextPath("/");
      if (sessionHandler != null) {
        context.setSessionHandler(sessionHandler);
      }

      // Static resources (multiple bases) similar to legacy ResourceCollection
      try {
        if (config != null && config.hasPath("core.resource_bases")) {
          java.util.List<String> bases = config.getStringList("core.resource_bases");
          if (bases != null && !bases.isEmpty()) {
            java.util.List<Resource> resources = new java.util.ArrayList<>();
            for (String b : bases) {
              if (b != null && !b.isBlank()) resources.add(Resource.newResource(b));
            }
            if (!resources.isEmpty()) {
              context.setBaseResource(new ResourceCollection(resources.toArray(new Resource[0])));
            }
          }
        }
      } catch (Exception ignore) {}

      // DefaultServlet mappings with caching semantics
      java.util.Map<String,String> staticParams = new java.util.HashMap<>();
      staticParams.put("etags", "true");
      staticParams.put("cacheControl", "public, max-age=31536000, immutable");
      ServletHolder staticHolder = new ServletHolder(DefaultServlet.class);
      staticHolder.setInitParameters(staticParams);
      context.addServlet(staticHolder, "/static/*");

      java.util.Map<String,String> webclientParams = new java.util.HashMap<>();
      webclientParams.put("etags", "true");
      webclientParams.put("cacheControl", "no-cache, no-store, must-revalidate");
      ServletHolder webclientHolder = new ServletHolder(DefaultServlet.class);
      webclientHolder.setInitParameters(webclientParams);
      context.addServlet(webclientHolder, "/webclient/*");

      // Register Jakarta WebSocket endpoint programmatically
      JakartaWebSocketServletContainerInitializer.configure(context, (ctx, container) -> {
        org.waveprotocol.box.server.rpc.jakarta.WaveWebSocketEndpoint.provider = this;
        container.addEndpoint(org.waveprotocol.box.server.rpc.jakarta.WaveWebSocketEndpoint.class);
      });

      // Wrap with GzipHandler (prefer server-side gzip over legacy filters)
      GzipHandler gzip = new GzipHandler();
      gzip.setMinGzipSize(1024);
      gzip.setIncludedMethods("GET", "POST");
      gzip.setInflateBufferSize(8 * 1024);
      gzip.setHandler(context);

      httpServer.setHandler(gzip);
      httpServer.start();
      // Log all bound addresses
      var connectors = httpServer.getConnectors();
      StringBuilder sb = new StringBuilder("Jakarta WebSocket server started at: ");
      for (int i = 0; i < connectors.length; i++) {
        if (connectors[i] instanceof ServerConnector) {
          ServerConnector sc = (ServerConnector) connectors[i];
          if (i > 0) sb.append(", ");
          sb.append("ws://").append(sc.getHost()).append(":").append(sc.getLocalPort()).append("/socket");
        }
      }
      LOG.info(sb.toString());
    } catch (Exception e) {
      LOG.severe("Fatal error starting Jakarta http server.", e);
    }
  }

  /**
   * Register the Jakarta WebSocket endpoint on the given ServletContext.
   * This uses the standard ServerContainer attribute to add the endpoint at
   * path "/socket".
   */
  public void registerWebSocketEndpoint(Object servletContext) {
    try {
      if (!(servletContext instanceof jakarta.servlet.ServletContext)) {
        LOG.info("registerWebSocketEndpoint called without a Jakarta ServletContext; skipping.");
        return;
      }
      jakarta.servlet.ServletContext ctx = (jakarta.servlet.ServletContext) servletContext;
      Object attr = ctx.getAttribute("jakarta.websocket.server.ServerContainer");
      if (attr == null) {
        LOG.info("No ServerContainer found on ServletContext; WebSocket endpoint not registered.");
        return;
      }
      jakarta.websocket.server.ServerContainer sc = (jakarta.websocket.server.ServerContainer) attr;
      sc.addEndpoint(org.waveprotocol.box.server.rpc.jakarta.WaveWebSocketEndpoint.class);
      LOG.info("Registered Jakarta WebSocket endpoint at /socket");
    } catch (Throwable t) {
      LOG.warning("Failed to register Jakarta WebSocket endpoint", t);
    }
  }

  public ServletHolder addServlet(String urlPattern, Class<? extends HttpServlet> servlet,
                                  @Nullable Map<String, String> initParams) {
    LOG.info("[Jakarta stub] addServlet {} -> {}", urlPattern, servlet.getName());
    return new ServletHolder();
  }

  public ServletHolder addServlet(String urlPattern, Class<? extends HttpServlet> servlet) {
    return addServlet(urlPattern, servlet, null);
  }

  public void addFilter(String urlPattern, Class<? extends Filter> filter) {
    LOG.info("[Jakarta stub] addFilter {} -> {}", urlPattern, filter.getName());
  }

  public void addTransparentProxy(String urlPattern, String proxyTo, String prefix) {
    LOG.info("[Jakarta stub] addTransparentProxy {} -> {} (prefix {})", urlPattern, proxyTo, prefix);
  }

  public SocketAddress getWebSocketAddress() {
    return null;
  }

  public void stopServer() throws IOException {
    try {
      if (httpServer != null) httpServer.stop();
    } catch (Exception e) {
      LOG.warning("Error stopping Jakarta http server", e);
    }
  }

  /** Returns bound ws addresses for testing/logging. */
  public java.util.List<java.net.InetSocketAddress> getBoundAddresses() {
    java.util.List<java.net.InetSocketAddress> out = new java.util.ArrayList<>();
    if (httpServer != null) {
      for (var conn : httpServer.getConnectors()) {
        if (conn instanceof ServerConnector) {
          ServerConnector sc = (ServerConnector) conn;
          out.add(new java.net.InetSocketAddress(sc.getHost(), sc.getLocalPort()));
        }
      }
    }
    return out;
  }

  public void registerService(Service service) {
    // no-op
  }

  // Called by the Jakarta WebSocket endpoint to forward messages into the
  // provider's dispatch machinery (stubbed here; implement as part of full migration).
  public void receiveWebSocketMessage(int sequenceNo, com.google.protobuf.Message message) {
    // TODO: wire to actual dispatcher once Jakarta path is complete
  }
}
