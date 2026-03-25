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

package org.waveprotocol.box.server.persistence.memory;

import org.waveprotocol.box.server.persistence.ContactMessageStore;
import org.waveprotocol.box.server.persistence.PersistenceException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of {@link ContactMessageStore}.
 * Suitable for development and testing.
 */
public class MemoryContactMessageStore implements ContactMessageStore {

  private final Map<String, ContactMessage> messages = new ConcurrentHashMap<>();

  @Override
  public void initializeContactMessageStore() throws PersistenceException {
    // Nothing to initialize for in-memory store.
  }

  @Override
  public String storeMessage(ContactMessage message) throws PersistenceException {
    String id = UUID.randomUUID().toString().replace("-", "").substring(0, 24);
    message.setId(id);
    if (message.getStatus() == null) {
      message.setStatus(STATUS_NEW);
    }
    if (message.getReplies() == null) {
      message.setReplies(new ArrayList<>());
    }
    messages.put(id, message);
    return id;
  }

  @Override
  public List<ContactMessage> getMessages(String statusFilter, int offset, int limit)
      throws PersistenceException {
    List<ContactMessage> filtered = new ArrayList<>();
    for (ContactMessage msg : messages.values()) {
      if (statusFilter == null || statusFilter.equals(msg.getStatus())) {
        filtered.add(msg);
      }
    }
    filtered.sort(Comparator.comparingLong(ContactMessage::getCreatedAt).reversed());
    int from = Math.min(offset, filtered.size());
    int to = Math.min(from + limit, filtered.size());
    return new ArrayList<>(filtered.subList(from, to));
  }

  @Override
  public long countMessages(String statusFilter) throws PersistenceException {
    if (statusFilter == null) {
      return messages.size();
    }
    long count = 0;
    for (ContactMessage msg : messages.values()) {
      if (statusFilter.equals(msg.getStatus())) count++;
    }
    return count;
  }

  @Override
  public ContactMessage getMessage(String id) throws PersistenceException {
    return messages.get(id);
  }

  @Override
  public void updateStatus(String id, String newStatus) throws PersistenceException {
    ContactMessage msg = messages.get(id);
    if (msg != null) {
      msg.setStatus(newStatus);
    }
  }

  @Override
  public void addReply(String messageId, ContactReply reply) throws PersistenceException {
    ContactMessage msg = messages.get(messageId);
    if (msg != null) {
      if (msg.getReplies() == null) {
        msg.setReplies(new ArrayList<>());
      }
      msg.getReplies().add(reply);
      msg.setStatus(STATUS_REPLIED);
    }
  }
}
