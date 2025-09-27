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

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.eclipse.jetty.ee10.servlet.SessionHandler;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.waveprotocol.box.server.authentication.SessionManager;

import java.io.File;
import java.io.FileWriter;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.Executors;

import static org.junit.Assert.*;

/**
 * Verifies that DefaultServlet mappings in the Jakarta bootstrap set Cache-Control and ETag
 * headers for /static/* as expected.
 */
public class StaticHeadersJakartaIT {
  private Object provider;
  private File tempDir;

  @Before
  public void setUp() throws Exception {
    try {
      tempDir = Files.createTempDirectory("wave-static-").toFile();
      File staticDir = new File(tempDir, "static");
      staticDir.mkdirs();
      File f = new File(staticDir, "test.txt");
      try (FileWriter w = new FileWriter(f)) { w.write("ok"); }

      String conf = "core { http_frontend_addresses : [\"127.0.0.1:0\"], resource_bases : [\"" + tempDir.getAbsolutePath().replace("\\", "/") + "\"] }";
      Config cfg = ConfigFactory.parseString(conf);
      Class<?> prov = Class.forName("org.waveprotocol.box.server.rpc.ServerRpcProvider");
      SessionManager sessionManager = Mockito.mock(SessionManager.class);
      provider = prov.getConstructor(com.typesafe.config.Config.class,
              org.waveprotocol.box.server.authentication.SessionManager.class,
              SessionHandler.class,
              java.util.concurrent.Executor.class)
          .newInstance(cfg, sessionManager, new SessionHandler(), Executors.newSingleThreadExecutor());
      prov.getMethod("startWebSocketServer", com.google.inject.Injector.class).invoke(provider, new Object[]{null});
    } catch (Throwable t) {
      Assume.assumeNoException("Jakarta bootstrap unavailable", t);
    }
  }

  @After
  public void tearDown() throws Exception {
    try {
      if (provider != null) provider.getClass().getMethod("stopServer").invoke(provider);
    } catch (Exception ignore) {}
    if (tempDir != null) {
      try { Files.walk(tempDir.toPath()).map(java.nio.file.Path::toFile).sorted((a,b)->-a.compareTo(b)).forEach(File::delete); } catch (Exception ignore) {}
    }
  }

  @Test
  public void staticHeaders() throws Exception {
    Assume.assumeNotNull(provider);
    @SuppressWarnings("unchecked")
    List<InetSocketAddress> addrs = (List<InetSocketAddress>) provider.getClass().getMethod("getBoundAddresses").invoke(provider);
    Assume.assumeTrue(!addrs.isEmpty());
    InetSocketAddress a = addrs.get(0);
    URL url = new URL("http://" + a.getHostString() + ":" + a.getPort() + "/static/test.txt");
    HttpURLConnection c = TestSupport.openConnection(url);
    int code = c.getResponseCode();
    assertEquals(200, code);
    String cc = c.getHeaderField("Cache-Control");
    assertNotNull(cc);
    assertTrue(cc.contains("max-age=31536000"));
    assertTrue(cc.contains("immutable"));
    String etag = c.getHeaderField("ETag");
    assertNotNull(etag);
    c.disconnect();
  }
}
