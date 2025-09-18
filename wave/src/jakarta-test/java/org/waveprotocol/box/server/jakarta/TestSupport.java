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

import org.junit.Assume;

/**
 * Test helpers for Jakarta EE10 (Jetty 12) integration tests.
 *
 * Visibility: public to allow reuse across test packages/modules. This class
 * is located under test sources and is not included in production artifacts.
 * Keep methods static and dependency-free to avoid coupling.
 */
public final class TestSupport {
  private TestSupport() {}

  public static boolean isJettyEe10Available() {
    try {
      Class.forName("org.eclipse.jetty.server.Server");
      Class.forName("org.eclipse.jetty.ee10.servlet.ServletContextHandler");
      return true;
    } catch (Throwable t) {
      return false;
    }
  }

  public static void assumeJettyEe10PresentOrSkip() {
    Assume.assumeTrue("Jetty 12 EE10 classes not available on classpath", isJettyEe10Available());
  }
}
