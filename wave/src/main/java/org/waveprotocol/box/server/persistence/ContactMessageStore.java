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

import java.util.List;

/**
 * Storage interface for contact form submissions.
 *
 * <p>Each message document has the structure:
 * <pre>{
 *   _id, userId, name, email, subject, message, status, createdAt, replies[], ip
 * }</pre>
 */
public interface ContactMessageStore {

  /** Status constants. */
  String STATUS_NEW = "new";
  String STATUS_READ = "read";
  String STATUS_REPLIED = "replied";
  String STATUS_ARCHIVED = "archived";

  /**
   * Initializes the store (e.g. ensure indexes).
   *
   * @throws PersistenceException if initialization fails
   */
  void initializeContactMessageStore() throws PersistenceException;

  /**
   * Stores a new contact message and returns its generated ID.
   */
  String storeMessage(ContactMessage message) throws PersistenceException;

  /**
   * Returns messages matching the given status filter, ordered by creation time
   * descending. Pass {@code null} for statusFilter to return all.
   *
   * @param statusFilter filter by status (null = all)
   * @param offset       number of results to skip
   * @param limit        max results to return
   */
  List<ContactMessage> getMessages(String statusFilter, int offset, int limit)
      throws PersistenceException;

  /**
   * Returns the total count of messages matching the given status filter.
   * Pass {@code null} to count all.
   */
  long countMessages(String statusFilter) throws PersistenceException;

  /**
   * Returns a single message by ID, or null if not found.
   */
  ContactMessage getMessage(String id) throws PersistenceException;

  /**
   * Updates the status of a message.
   */
  void updateStatus(String id, String newStatus) throws PersistenceException;

  /**
   * Adds a reply to a message and sets the status to {@link #STATUS_REPLIED}.
   */
  void addReply(String messageId, ContactReply reply) throws PersistenceException;

  /**
   * Represents a stored contact message.
   */
  class ContactMessage {
    private String id;
    private String userId;
    private String name;
    private String email;
    private String subject;
    private String message;
    private String status;
    private long createdAt;
    private String ip;
    private List<ContactReply> replies;

    public ContactMessage() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }

    public List<ContactReply> getReplies() { return replies; }
    public void setReplies(List<ContactReply> replies) { this.replies = replies; }
  }

  /**
   * Represents an admin reply to a contact message.
   */
  class ContactReply {
    private String adminUser;
    private String body;
    private long sentAt;

    public ContactReply() {}

    public ContactReply(String adminUser, String body, long sentAt) {
      this.adminUser = adminUser;
      this.body = body;
      this.sentAt = sentAt;
    }

    public String getAdminUser() { return adminUser; }
    public void setAdminUser(String adminUser) { this.adminUser = adminUser; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public long getSentAt() { return sentAt; }
    public void setSentAt(long sentAt) { this.sentAt = sentAt; }
  }
}
