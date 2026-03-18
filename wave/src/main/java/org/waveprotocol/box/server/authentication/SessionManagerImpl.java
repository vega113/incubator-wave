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

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.inject.Inject;

import org.waveprotocol.box.server.account.AccountData;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.util.escapers.PercentEscaper;
import org.waveprotocol.wave.util.logging.Log;

import javax.servlet.http.HttpSession;

/**
 * Utility class for managing the session's authentication status.
 *
 * @author josephg@gmail.com (Joseph Gentle)
 */
public final class SessionManagerImpl implements SessionManager {
  private static final String USER_FIELD = "user";

  private final AccountStore accountStore;
  private final org.eclipse.jetty.server.session.SessionHandler sessionHandler;

  private static final Log LOG = Log.get(SessionManagerImpl.class);

  @Inject
  public SessionManagerImpl(
      AccountStore accountStore, org.eclipse.jetty.server.session.SessionHandler sessionHandler) {
    Preconditions.checkNotNull(accountStore, "Null account store");
    Preconditions.checkNotNull(sessionHandler, "Null session handler");
    this.accountStore = accountStore;
    this.sessionHandler = sessionHandler;
  }

  @Override
  public ParticipantId getLoggedInUser(HttpSession session) {
    if (session != null) {
      return (ParticipantId) session.getAttribute(USER_FIELD);
    } else {
      return null;
    }
  }

  @Override
  public AccountData getLoggedInAccount(HttpSession session) {
    // Consider caching the account data in the session object.
    ParticipantId user = getLoggedInUser(session);
    if (user != null) {
      try {
        return accountStore.getAccount(user);
      } catch (PersistenceException e) {
        LOG.warning("Failed to retrieve account data for " + user, e);
        return null;
      }
    } else {
      return null;
    }
  }

  @Override
  public void setLoggedInUser(HttpSession session, ParticipantId id) {
    Preconditions.checkNotNull(session, "Session is null");
    Preconditions.checkNotNull(id, "Participant id is null");
    session.setAttribute(USER_FIELD, id);
  }

  @Override
  public void logout(HttpSession session) {
    if (session != null) {
      // This function should also remove any other bound fields in the session
      // object.
      session.removeAttribute(USER_FIELD);
    }
  }

  @Override
  public String getLoginUrl(String redirect) {
    if (Strings.isNullOrEmpty(redirect)) {
      return SIGN_IN_URL;
    } else {
      PercentEscaper escaper =
          new PercentEscaper(PercentEscaper.SAFEQUERYSTRINGCHARS_URLENCODER, false);
      String queryStr = "?r=" + escaper.escape(redirect);
      return SIGN_IN_URL + queryStr;
    }
  }

  @Override
  public HttpSession getSessionFromToken(String token) {
    Preconditions.checkNotNull(token);
    try {
      // Normalize potential nodeId-suffixed tokens (e.g., "abc123.node0") to the clusterId ("abc123").
      String sessionId = token;
      int dot = sessionId.indexOf('.');
      if (dot > 0) {
        sessionId = sessionId.substring(0, dot);
      }
      org.eclipse.jetty.server.session.Session s = sessionHandler.getSession(sessionId);
      return (s != null) ? s.getSession() : null;
    } catch (Throwable t) {
      return null;
    }
  }
}
