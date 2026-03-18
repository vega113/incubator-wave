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
package org.waveprotocol.box.server.stat;

import static org.junit.Assert.assertTrue;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Properties;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public final class StatuszServletConfigTest {
  private Properties originalProperties;

  @Before
  public void captureSystemProperties() {
    originalProperties = (Properties) System.getProperties().clone();
  }

  @After
  public void restoreSystemProperties() {
    System.setProperties((Properties) originalProperties.clone());
  }

  @Test
  public void fragmentsViewUsesInjectedConfigInsteadOfJvmProperties() throws Exception {
    Config config = ConfigFactory.parseString(
        "server.fragments.transport=\"stream\"\n" +
        "server.preferSegmentState=true\n" +
        "server.enableStorageSegmentState=true");

    System.setProperty("server.fragments.transport", "off");
    System.setProperty("server.preferSegmentState", "false");
    System.setProperty("server.enableStorageSegmentState", "false");

    StatuszServlet servlet = new StatuszServlet(config);
    StringWriter buffer = new StringWriter();
    HttpServletRequest request = proxy(HttpServletRequest.class, (proxy, method, args) -> {
      if ("getParameter".equals(method.getName()) && args != null && args.length == 1) {
        return "fragments";
      }
      return defaultValue(method.getReturnType());
    });
    HttpServletResponse response = proxy(HttpServletResponse.class, (proxy, method, args) -> {
      if ("getWriter".equals(method.getName())) {
        return new PrintWriter(buffer);
      }
      return defaultValue(method.getReturnType());
    });

    servlet.service(request, response);

    String output = buffer.toString();
    assertTrue(output.contains(
        "<pre>transport=stream; preferSegmentState=true; enableStorageSegmentState=true</pre>"));
  }

  private static <T> T proxy(Class<T> type, InvocationHandler handler) {
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
}
