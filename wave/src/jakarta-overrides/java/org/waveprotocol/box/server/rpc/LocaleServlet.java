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
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.waveprotocol.box.server.account.AccountData;
import org.waveprotocol.box.server.account.HumanAccountData;
import org.waveprotocol.box.server.account.HumanAccountDataImpl;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.authentication.WebSessions;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.util.logging.Log;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Jakarta variant of LocaleServlet that bridges HttpSession access through
 * {@link WebSessions} to maintain compatibility with the legacy session manager.
 */
@SuppressWarnings("serial")
@Singleton
public final class LocaleServlet extends HttpServlet {
  private static final Log LOG = Log.get(LocaleServlet.class);

  private final SessionManager sessionManager;
  private final AccountStore accountStore;

  @Inject
  public LocaleServlet(SessionManager sessionManager, AccountStore accountStore) {
    this.sessionManager = sessionManager;
    this.accountStore = accountStore;
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    ParticipantId participant = sessionManager.getLoggedInUser(WebSessions.from(req, false));
    if (participant == null) {
      resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
      return;
    }

    String locale = req.getParameter("locale");
    if (locale == null) {
      resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      return;
    }
    locale = URLDecoder.decode(locale, StandardCharsets.UTF_8);

    try {
      AccountData account = accountStore.getAccount(participant);
      HumanAccountData humanAccount = (account != null) ? account.asHuman() : new HumanAccountDataImpl(participant);
      humanAccount.setLocale(locale);
      accountStore.putAccount(humanAccount);
    } catch (PersistenceException ex) {
      LOG.severe("Failed to persist locale for " + participant, ex);
      throw new IOException(ex);
    }
  }
}
