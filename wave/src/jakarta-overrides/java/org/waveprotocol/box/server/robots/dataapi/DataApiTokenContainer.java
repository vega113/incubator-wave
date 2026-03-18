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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.cache.CacheBuilder;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import net.oauth.OAuth;
import net.oauth.OAuthAccessor;
import net.oauth.OAuthConsumer;
import net.oauth.OAuthProblemException;
import org.waveprotocol.box.server.util.OAuthUtil;
import org.waveprotocol.wave.model.id.TokenGenerator;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.util.logging.Log;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

@Singleton
public final class DataApiTokenContainer {
  private static final Log LOG = Log.get(DataApiTokenContainer.class);
  private static final int TOKEN_LENGTH = 48;
  private static final int REQUEST_TOKEN_EXPIRATION = 10;
  public static final int ACCESS_TOKEN_EXPIRATION = 60;
  public static final String USER_PROPERTY_NAME = "user";

  private final ConcurrentMap<String, OAuthAccessor> requestTokenAccessors;
  private final ConcurrentMap<String, OAuthAccessor> accessTokenAccessors;
  private final TokenGenerator tokenGenerator;

  @Inject
  @VisibleForTesting
  DataApiTokenContainer(TokenGenerator tokenGenerator) {
    this.tokenGenerator = tokenGenerator;
    this.requestTokenAccessors = CacheBuilder.newBuilder()
        .expireAfterWrite(REQUEST_TOKEN_EXPIRATION, TimeUnit.MINUTES)
        .<String, OAuthAccessor>build()
        .asMap();
    this.accessTokenAccessors = CacheBuilder.newBuilder()
        .expireAfterWrite(ACCESS_TOKEN_EXPIRATION, TimeUnit.MINUTES)
        .<String, OAuthAccessor>build()
        .asMap();
  }

  public OAuthAccessor getRequestTokenAccessor(String requestToken) throws OAuthProblemException {
    OAuthAccessor accessor = requestTokenAccessors.get(requestToken);
    if (accessor == null) {
      OAuthProblemException exception =
          OAuthUtil.newOAuthProblemException(OAuth.Problems.TOKEN_REJECTED);
      exception.setParameter(OAuth.OAUTH_TOKEN, requestToken);
      throw exception;
    }
    return accessor.clone();
  }

  public OAuthAccessor getAccessTokenAccessor(String accessToken) throws OAuthProblemException {
    OAuthAccessor accessor = accessTokenAccessors.get(accessToken);
    if (accessor == null) {
      OAuthProblemException exception =
          OAuthUtil.newOAuthProblemException(OAuth.Problems.TOKEN_REJECTED);
      exception.setParameter(OAuth.OAUTH_TOKEN, accessToken);
      throw exception;
    }
    return accessor.clone();
  }

  public OAuthAccessor generateRequestToken(OAuthConsumer consumer) {
    Preconditions.checkNotNull(consumer, "Consumer must not be null");
    OAuthAccessor accessor = new OAuthAccessor(consumer);
    accessor.tokenSecret = generateToken();
    do {
      accessor.requestToken = generateToken();
    } while (requestTokenAccessors.putIfAbsent(accessor.requestToken, accessor) != null);
    return accessor.clone();
  }

  public OAuthAccessor authorizeRequestToken(String requestToken, ParticipantId user)
      throws OAuthProblemException {
    Preconditions.checkNotNull(user, "User must not be null");
    OAuthAccessor accessor = getRequestTokenAccessor(requestToken);
    if (accessor.getProperty(USER_PROPERTY_NAME) != null) {
      throw OAuthUtil.newOAuthProblemException(OAuth.Problems.TOKEN_USED);
    }
    accessor.setProperty(USER_PROPERTY_NAME, user);
    requestTokenAccessors.put(requestToken, accessor);
    LOG.info("Authorized request token for " + user);
    return accessor.clone();
  }

  public void rejectRequestToken(String requestToken) throws OAuthProblemException {
    OAuthAccessor accessor = getRequestTokenAccessor(requestToken);
    if (accessor.getProperty(USER_PROPERTY_NAME) != null) {
      throw OAuthUtil.newOAuthProblemException(OAuth.Problems.TOKEN_USED);
    }
    requestTokenAccessors.remove(requestToken);
    LOG.info("Rejected request token " + requestToken);
  }

  public OAuthAccessor generateAccessToken(String requestToken) throws OAuthProblemException {
    OAuthAccessor accessor = getRequestTokenAccessor(requestToken);
    if (accessor.getProperty(USER_PROPERTY_NAME) == null) {
      throw OAuthUtil.newOAuthProblemException(OAuth.Problems.PERMISSION_DENIED);
    }
    OAuthAccessor authorizedAccessor = new OAuthAccessor(accessor.consumer);
    authorizedAccessor.accessToken = generateToken();
    authorizedAccessor.tokenSecret = generateToken();
    authorizedAccessor.setProperty(USER_PROPERTY_NAME,
        accessor.getProperty(USER_PROPERTY_NAME));
    accessTokenAccessors.put(authorizedAccessor.accessToken, authorizedAccessor);
    requestTokenAccessors.remove(requestToken);
    LOG.info("Access token generated for " + authorizedAccessor.getProperty(USER_PROPERTY_NAME));
    return authorizedAccessor.clone();
  }

  private String generateToken() {
    return tokenGenerator.generateToken(TOKEN_LENGTH);
  }
}
