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
import com.google.inject.Singleton;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.authentication.WebSessions;
import org.waveprotocol.wave.model.wave.ParticipantId;

import java.io.IOException;

/**
 * Jakarta variant of WaveRefServlet that uses {@link WebSessions} to access the
 * session manager bridge.
 */
@SuppressWarnings("serial")
@Singleton
public final class WaveRefServlet extends HttpServlet {
  private final SessionManager sessionManager;

  @Inject
  public WaveRefServlet(SessionManager sessionManager) {
    this.sessionManager = sessionManager;
  }

  @Override
  protected void service(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    ParticipantId user = sessionManager.getLoggedInUser(WebSessions.from(req, false));
    String path = req.getRequestURI().replaceFirst("^/waveref/", "");
    if (user != null) {
      resp.sendRedirect("/#" + path);
    } else {
      resp.sendRedirect("/auth/signin?r=/#" + path);
    }
  }
}
