/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.waveprotocol.box.server.robots.dataapi;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import jakarta.inject.Singleton;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.waveprotocol.box.server.CoreSettingsNames;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.authentication.WebSessions;
import org.waveprotocol.box.server.authentication.jwt.JwtAudience;
import org.waveprotocol.box.server.authentication.jwt.JwtClaims;
import org.waveprotocol.box.server.authentication.jwt.JwtKeyRing;
import org.waveprotocol.box.server.authentication.jwt.JwtTokenType;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.util.logging.Log;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.Clock;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Simple token endpoint that issues a DATA_API_ACCESS JWT for the currently
 * logged-in user. Replaces the old OAuth token exchange flow.
 *
 * <p>The user must be logged in via a browser session. Returns JSON:
 * <pre>{"access_token": "...", "token_type": "bearer", "expires_in": 3600}</pre>
 */
@SuppressWarnings("serial")
@Singleton
public final class DataApiTokenServlet extends HttpServlet {
  private static final Log LOG = Log.get(DataApiTokenServlet.class);
  private static final long TOKEN_LIFETIME_SECONDS = 3600L;
  private static final String JSON_CONTENT_TYPE = "application/json";

  private final SessionManager sessionManager;
  private final JwtKeyRing keyRing;
  private final Clock clock;
  private final String issuer;

  @Inject
  public DataApiTokenServlet(SessionManager sessionManager,
                             JwtKeyRing keyRing,
                             Clock clock,
                             @Named(CoreSettingsNames.WAVE_SERVER_DOMAIN) String issuer) {
    this.sessionManager = sessionManager;
    this.keyRing = keyRing;
    this.clock = clock;
    this.issuer = issuer;
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    ParticipantId user = sessionManager.getLoggedInUser(WebSessions.from(req, false));
    if (user == null) {
      resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      resp.setContentType(JSON_CONTENT_TYPE);
      try (PrintWriter writer = resp.getWriter()) {
        writer.write("{\"error\": \"not_authenticated\"}");
      }
      return;
    }

    long issuedAt = clock.instant().getEpochSecond();
    long expiresAt = issuedAt + TOKEN_LIFETIME_SECONDS;

    JwtClaims claims = new JwtClaims(
        JwtTokenType.DATA_API_ACCESS,
        issuer,
        user.getAddress(),
        UUID.randomUUID().toString(),
        keyRing.signingKeyId(),
        EnumSet.of(JwtAudience.DATA_API),
        Set.of(),
        issuedAt,
        issuedAt,
        expiresAt,
        0L);

    String token = keyRing.issuer().issue(claims);

    resp.setContentType(JSON_CONTENT_TYPE);
    resp.setStatus(HttpServletResponse.SC_OK);
    try (PrintWriter writer = resp.getWriter()) {
      writer.write("{\"access_token\": \"" + token + "\", "
          + "\"token_type\": \"bearer\", "
          + "\"expires_in\": " + TOKEN_LIFETIME_SECONDS + "}");
    }
  }
}
