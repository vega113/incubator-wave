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
package org.waveprotocol.box.server.robots.active;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.wave.api.RobotSerializer;
import com.google.wave.api.data.converter.EventDataConverterManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.oauth.OAuth;
import net.oauth.OAuthAccessor;
import net.oauth.OAuthConsumer;
import net.oauth.OAuthMessage;
import net.oauth.OAuthServiceProvider;
import net.oauth.OAuthValidator;
import org.waveprotocol.box.server.robots.util.JakartaHttpRequestMessage;
import org.waveprotocol.box.server.account.AccountData;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.box.server.robots.OperationServiceRegistry;
import org.waveprotocol.box.server.robots.dataapi.BaseApiServlet;
import org.waveprotocol.box.server.robots.util.ConversationUtil;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.wave.model.wave.InvalidParticipantAddress;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.util.logging.Log;

import java.io.IOException;

import jakarta.inject.Singleton;

/** Servlet endpoint for the Active API (Jakarta variant). */
@SuppressWarnings("serial")
@Singleton
public final class ActiveApiServlet extends BaseApiServlet {
  private static final Log LOG = Log.get(ActiveApiServlet.class);

  private final OAuthServiceProvider oauthServiceProvider;
  private final AccountStore accountStore;

  @Inject
  public ActiveApiServlet(RobotSerializer robotSerializer,
                          EventDataConverterManager converterManager,
                          WaveletProvider waveletProvider,
                          @Named("ActiveApiRegistry") OperationServiceRegistry operationRegistry,
                          ConversationUtil conversationUtil,
                          OAuthServiceProvider oAuthServiceProvider,
                          OAuthValidator validator,
                          AccountStore accountStore) {
    super(robotSerializer, converterManager, waveletProvider, operationRegistry, conversationUtil, validator);
    this.oauthServiceProvider = oAuthServiceProvider;
    this.accountStore = accountStore;
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    OAuthMessage message = new JakartaHttpRequestMessage(req, req.getRequestURL().toString());
    String username = OAuth.decodePercent(message.getConsumerKey());

    ParticipantId participant;
    try {
      participant = ParticipantId.of(username);
    } catch (InvalidParticipantAddress e) {
      LOG.info("Participant id invalid", e);
      resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      return;
    }

    AccountData account;
    try {
      account = accountStore.getAccount(participant);
    } catch (PersistenceException e) {
      LOG.severe("Failed to retrieve account data for " + participant, e);
      resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
          "An unexpected error occured while trying to retrieve account data for "
              + participant.getAddress());
      return;
    }
    if (account == null || !account.isRobot()) {
      LOG.info("The account for robot named " + participant + " does not exist");
      resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      return;
    }

    OAuthConsumer consumer =
        new OAuthConsumer(null, participant.getAddress(), account.asRobot().getConsumerSecret(),
            oauthServiceProvider);
    OAuthAccessor accessor = new OAuthAccessor(consumer);

    processOpsRequest(req, resp, message, accessor, participant);
  }
}
