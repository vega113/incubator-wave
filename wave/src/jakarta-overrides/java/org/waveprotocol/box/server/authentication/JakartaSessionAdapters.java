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
package org.waveprotocol.box.server.authentication;

/**
 * Small adapter utilities to bridge jakarta.servlet.http.HttpSession to
 * javax.servlet.http.HttpSession for code paths that still depend on the
 * javax-based SessionManager interface.
 */
public final class JakartaSessionAdapters {
  private JakartaSessionAdapters() {}

  public static javax.servlet.http.HttpSession toJavax(jakarta.servlet.http.HttpSession js) {
    if (js == null) return null;
    return new JavaxSessionWrapper(js);
  }

  static final class JavaxSessionWrapper implements javax.servlet.http.HttpSession {
    private final jakarta.servlet.http.HttpSession d;
    JavaxSessionWrapper(jakarta.servlet.http.HttpSession delegate) { this.d = delegate; }
    @Override public long getCreationTime() { return d.getCreationTime(); }
    @Override public String getId() { return d.getId(); }
    @Override public long getLastAccessedTime() { return d.getLastAccessedTime(); }
    @Override public javax.servlet.ServletContext getServletContext() { return null; }
    @Override public void setMaxInactiveInterval(int interval) { d.setMaxInactiveInterval(interval); }
    @Override public int getMaxInactiveInterval() { return d.getMaxInactiveInterval(); }
    @Override public javax.servlet.http.HttpSessionContext getSessionContext() { return null; }
    @Override public Object getAttribute(String name) { return d.getAttribute(name); }
    @Override public Object getValue(String name) { return d.getAttribute(name); }
    @Override public java.util.Enumeration<String> getAttributeNames() { return d.getAttributeNames(); }
    @Override public String[] getValueNames() { return java.util.Collections.list(d.getAttributeNames()).toArray(new String[0]); }
    @Override public void setAttribute(String name, Object value) { d.setAttribute(name, value); }
    @Override public void putValue(String name, Object value) { d.setAttribute(name, value); }
    @Override public void removeAttribute(String name) { d.removeAttribute(name); }
    @Override public void removeValue(String name) { d.removeAttribute(name); }
    @Override public void invalidate() { d.invalidate(); }
    @Override public boolean isNew() { return d.isNew(); }
  }
}

