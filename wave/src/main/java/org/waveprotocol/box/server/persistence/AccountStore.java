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

package org.waveprotocol.box.server.persistence;

import org.waveprotocol.box.server.account.AccountData;
import org.waveprotocol.box.server.account.RobotAccountData;
import org.waveprotocol.box.server.account.RobotAccountDataImpl;
import org.waveprotocol.box.server.account.SocialIdentity;
import org.waveprotocol.wave.model.wave.ParticipantId;

import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * Interface for the storage and retrieval of {@link AccountData}s.
 *
 * @author ljvderijk@google.com (Lennard de Rijk)
 */
public interface AccountStore {
  Logger ACCOUNT_STORE_LOG = Logger.getLogger(AccountStore.class.getName());
  /**
   * Initialize the account store.
   * Implementations are expected to validate any configuration values, validate the state of the
   * store, and perform an start-up action needed (e.g. load list of accounts into memory,
   * establish connection to database, etc...).
   * 
   * @throws PersistenceException
   */
  void initializeAccountStore() throws PersistenceException;

  /**
   * Returns an {@link AccountData} for the given username or null if not
   * exists.
   *
   * @param id participant id of the requested account.
   */
  AccountData getAccount(ParticipantId id) throws PersistenceException;

  /**
   * Puts the given {@link AccountData} in the storage, overrides an existing
   * account if the username is already in use.
   *
   * @param account to store.
   */
  void putAccount(AccountData account) throws PersistenceException;

  /**
   * Updates the robot last-active timestamp for the given account.
   *
   * <p>The intent is to update only {@code lastActiveAtMillis} without touching
   * other account fields. The default implementation performs a non-atomic
   * read-modify-write via {@link #getAccount} and {@link #putAccount}, which
   * may overwrite concurrent updates to other fields. Implementations backed
   * by a database (e.g. MongoDB) should override this with an atomic field-level
   * update to avoid that race.
   *
   * @param id robot participant id.
   * @param lastActiveAtMillis milliseconds since epoch.
   */
  default void updateRobotLastActive(ParticipantId id, long lastActiveAtMillis)
      throws PersistenceException {
    AccountData account = getAccount(id);
    if (account == null || !account.isRobot()) {
      return;
    }
    RobotAccountData current = account.asRobot();
    putAccount(new RobotAccountDataImpl(
        current.getId(),
        current.getUrl(),
        current.getConsumerSecret(),
        current.getCapabilities(),
        current.isVerified(),
        current.getTokenExpirySeconds(),
        current.getOwnerAddress(),
        current.getDescription(),
        current.getCreatedAtMillis(),
        current.getUpdatedAtMillis(),
        current.isPaused(),
        current.getTokenVersion(),
        lastActiveAtMillis));
  }

  /**
   * Updates human login/activity timestamps.
   *
   * <p>The default implementation performs a read-modify-write. Database-backed
   * stores should override this with a field-targeted update.
   */
  default void updateHumanLoginTimestamps(ParticipantId id, long lastLoginTime,
      long lastActivityTime) throws PersistenceException {
    AccountData account = getAccount(id);
    if (account == null || !account.isHuman()) {
      return;
    }
    account.asHuman().setLastLoginTime(lastLoginTime);
    account.asHuman().setLastActivityTime(lastActivityTime);
    putAccount(account);
  }

  /**
   * Removes an account from storage.
   *
   * @param id the participant id of the account to remove.
   */
  void removeAccount(ParticipantId id) throws PersistenceException;

  /**
   * Returns an {@link AccountData} for the given email address, or null if
   * no account has that email.
   *
   * <p>Default implementation scans human accounts case-insensitively. Stores
   * with an email index should override this method.
   *
   * @param email the email address to look up.
   */
  default AccountData getAccountByEmail(String email) throws PersistenceException {
    String normalized = normalizeEmail(email);
    if (normalized.isEmpty()) {
      return null;
    }
    ACCOUNT_STORE_LOG.fine(
        "Scanning accounts for email lookup; backend should override this method");
    for (AccountData account : getAllAccounts()) {
      if (account == null || !account.isHuman()) {
        continue;
      }
      String accountEmail = normalizeEmail(account.asHuman().getEmail());
      if (!accountEmail.isEmpty() && accountEmail.equals(normalized)) {
        return account;
      }
    }
    return null;
  }

  /**
   * Returns a human account linked to the given provider subject, or null.
   */
  default AccountData getAccountBySocialIdentity(String provider, String subject)
      throws PersistenceException {
    if (provider == null || provider.trim().isEmpty()
        || subject == null || subject.trim().isEmpty()) {
      return null;
    }
    ACCOUNT_STORE_LOG.fine(
        "Scanning accounts for social identity lookup; backend should override this method");
    String normalizedProvider = provider.trim().toLowerCase(Locale.ROOT);
    String normalizedSubject = subject.trim();
    for (AccountData account : getAllAccounts()) {
      if (account == null || !account.isHuman()) {
        continue;
      }
      for (SocialIdentity identity : account.asHuman().getSocialIdentities()) {
        if (identity.matches(normalizedProvider, normalizedSubject)) {
          return account;
        }
      }
    }
    return null;
  }

  /**
   * Links a provider identity to an existing human account.
   */
  default void linkSocialIdentity(ParticipantId id, SocialIdentity socialIdentity)
      throws PersistenceException {
    AccountData account = getAccount(id);
    if (account == null || !account.isHuman()) {
      throw new PersistenceException("No human account found for " + id);
    }
    account.asHuman().addOrReplaceSocialIdentity(socialIdentity);
    putAccount(account);
  }

  /**
   * Returns all human accounts in the store.
   *
   * <p>Default implementation returns an empty list (backward compatible for
   * stores that have not implemented enumeration).
   */
  default List<AccountData> getAllAccounts() throws PersistenceException {
    return java.util.Collections.emptyList();
  }

  /**
   * Returns all robot accounts owned by the given human account address.
   */
  default List<RobotAccountData> getRobotAccountsOwnedBy(String ownerAddress)
      throws PersistenceException {
    return java.util.Collections.emptyList();
  }

  /**
   * Returns the total count of human accounts in the store.
   *
   * <p>Default implementation returns 0 (backward compatible).
   */
  default long getAccountCount() throws PersistenceException {
    return 0;
  }

  private static String normalizeEmail(String email) {
    return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
  }
}
