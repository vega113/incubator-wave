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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Properties;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.waveprotocol.box.server.authentication.SessionManager;
import jakarta.servlet.http.HttpServletRequest;

public final class WaveClientServletFragmentDefaultsTest {
  private Properties originalProperties;

  @Before
  public void captureSystemProperties() {
    originalProperties = (Properties) System.getProperties().clone();
    System.clearProperty("wave.clientFlags");
  }

  @After
  public void restoreSystemProperties() {
    System.setProperties((Properties) originalProperties.clone());
  }

  @Test
  public void fragmentDefaultsComeFromConfigWhenClientFlagsPropertyIsAbsent() throws Exception {
    Config config = ConfigFactory.parseString(
        "core.http_frontend_addresses=[\"127.0.0.1:9898\"]\n" +
        "core.http_websocket_public_address=\"\"\n" +
        "core.http_websocket_presented_address=\"\"\n" +
        "administration.analytics_account=\"\"\n" +
        "server.fragments.transport=\"stream\"\n" +
        "wave.fragments.forceClientApplier=true");

    WaveClientServlet servlet = new WaveClientServlet(
        "example.com", config, proxy(SessionManager.class));
    HttpServletRequest request = proxy(HttpServletRequest.class, (proxy, method, args) -> {
      if ("getParameterNames".equals(method.getName())) {
        return Collections.emptyEnumeration();
      }
      if ("getParameter".equals(method.getName())) {
        return null;
      }
      return defaultValue(method.getReturnType());
    });

    Method method = WaveClientServlet.class.getDeclaredMethod("getClientFlags", HttpServletRequest.class);
    method.setAccessible(true);
    JSONObject flags = (JSONObject) method.invoke(servlet, request);

    assertEquals("stream", flags.getString(clientFlagKey("fragmentFetchMode")));
    assertTrue(flags.getBoolean(clientFlagKey("forceClientFragments")));
  }

  private static <T> T proxy(Class<T> type) {
    return proxy(type, (proxy, method, args) -> defaultValue(method.getReturnType()));
  }

  private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
    return type.cast(Proxy.newProxyInstance(
        type.getClassLoader(), new Class<?>[] {type}, handler));
  }

  private static Object defaultValue(Class<?> returnType) {
    if (!returnType.isPrimitive()) {
      return null;
    }
    if (returnType == boolean.class) {
      return false;
    }
    if (returnType == byte.class) {
      return (byte) 0;
    }
    if (returnType == short.class) {
      return (short) 0;
    }
    if (returnType == int.class) {
      return 0;
    }
    if (returnType == long.class) {
      return 0L;
    }
    if (returnType == float.class) {
      return 0f;
    }
    if (returnType == double.class) {
      return 0d;
    }
    if (returnType == char.class) {
      return '\0';
    }
    return null;
  }

  private static String clientFlagKey(String name) throws Exception {
    Field field = WaveClientServlet.class.getDeclaredField("FLAG_MAP");
    field.setAccessible(true);
    @SuppressWarnings("unchecked")
    java.util.Map<String, String> flagMap = (java.util.Map<String, String>) field.get(null);
    return flagMap.get(name);
  }
}
