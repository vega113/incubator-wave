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

package org.waveprotocol.box.server.persistence.mongodb4;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.waveprotocol.box.server.persistence.ContactMessageStore;
import org.waveprotocol.box.server.persistence.PersistenceException;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * MongoDB 4.x implementation of {@link ContactMessageStore}.
 *
 * <p>Documents are stored in the {@code contact_messages} collection:
 * <pre>{
 *   "_id": ObjectId,
 *   "userId": "user@domain",
 *   "name": "Display Name",
 *   "email": "user@email.com",
 *   "subject": "General Inquiry",
 *   "message": "...",
 *   "status": "new",
 *   "createdAt": 1711234567890,
 *   "ip": "1.2.3.4",
 *   "replies": [
 *     { "adminUser": "admin@domain", "body": "...", "sentAt": 1711234567890 }
 *   ]
 * }</pre>
 */
final class Mongo4ContactMessageStore implements ContactMessageStore {
  private static final Logger LOG = Logger.getLogger(Mongo4ContactMessageStore.class.getName());

  private static final String COLLECTION = "contact_messages";

  private final MongoCollection<Document> col;

  Mongo4ContactMessageStore(MongoDatabase db) {
    this.col = db.getCollection(COLLECTION);
  }

  @Override
  public void initializeContactMessageStore() throws PersistenceException {
    // Mongock owns Mongo schema/index evolution for this collection.
  }

  @Override
  public String storeMessage(ContactMessage message) throws PersistenceException {
    try {
      Document doc = new Document()
          .append("userId", message.getUserId())
          .append("name", message.getName())
          .append("email", message.getEmail())
          .append("subject", message.getSubject())
          .append("message", message.getMessage())
          .append("status", message.getStatus() != null ? message.getStatus() : STATUS_NEW)
          .append("createdAt", message.getCreatedAt())
          .append("ip", message.getIp())
          .append("replies", new ArrayList<>());
      col.insertOne(doc);
      return doc.getObjectId("_id").toString();
    } catch (RuntimeException e) {
      throw new PersistenceException("Failed to store contact message", e);
    }
  }

  @Override
  public List<ContactMessage> getMessages(String statusFilter, int offset, int limit)
      throws PersistenceException {
    try {
      var filter = statusFilter != null ? Filters.eq("status", statusFilter) : new Document();
      List<ContactMessage> results = new ArrayList<>();
      for (Document doc : col.find(filter)
          .sort(Sorts.descending("createdAt"))
          .skip(offset)
          .limit(limit)) {
        results.add(fromDocument(doc));
      }
      return results;
    } catch (RuntimeException e) {
      throw new PersistenceException("Failed to query contact messages", e);
    }
  }

  @Override
  public long countMessages(String statusFilter) throws PersistenceException {
    try {
      var filter = statusFilter != null ? Filters.eq("status", statusFilter) : new Document();
      return col.countDocuments(filter);
    } catch (RuntimeException e) {
      throw new PersistenceException("Failed to count contact messages", e);
    }
  }

  @Override
  public ContactMessage getMessage(String id) throws PersistenceException {
    try {
      ObjectId oid = new ObjectId(id);
      Document doc = col.find(Filters.eq("_id", oid)).first();
      return doc != null ? fromDocument(doc) : null;
    } catch (IllegalArgumentException e) {
      return null; // invalid ObjectId
    } catch (RuntimeException e) {
      throw new PersistenceException("Failed to get contact message " + id, e);
    }
  }

  @Override
  public void updateStatus(String id, String newStatus) throws PersistenceException {
    try {
      ObjectId oid = new ObjectId(id);
      col.updateOne(Filters.eq("_id", oid), Updates.set("status", newStatus));
    } catch (RuntimeException e) {
      throw new PersistenceException("Failed to update status for " + id, e);
    }
  }

  @Override
  public void addReply(String messageId, ContactReply reply) throws PersistenceException {
    try {
      ObjectId oid = new ObjectId(messageId);
      Document replyDoc = new Document()
          .append("adminUser", reply.getAdminUser())
          .append("body", reply.getBody())
          .append("sentAt", reply.getSentAt());
      col.updateOne(
          Filters.eq("_id", oid),
          Updates.combine(
              Updates.push("replies", replyDoc),
              Updates.set("status", STATUS_REPLIED)));
    } catch (RuntimeException e) {
      throw new PersistenceException("Failed to add reply to " + messageId, e);
    }
  }

  private static ContactMessage fromDocument(Document doc) {
    ContactMessage msg = new ContactMessage();
    msg.setId(doc.getObjectId("_id").toString());
    msg.setUserId(doc.getString("userId"));
    msg.setName(doc.getString("name"));
    msg.setEmail(doc.getString("email"));
    msg.setSubject(doc.getString("subject"));
    msg.setMessage(doc.getString("message"));
    msg.setStatus(doc.getString("status"));
    Long createdAt = doc.getLong("createdAt");
    msg.setCreatedAt(createdAt != null ? createdAt : 0L);
    msg.setIp(doc.getString("ip"));

    List<ContactReply> replies = new ArrayList<>();
    List<?> replyDocs = doc.getList("replies", Document.class);
    if (replyDocs != null) {
      for (Object obj : replyDocs) {
        Document rd = (Document) obj;
        ContactReply r = new ContactReply();
        r.setAdminUser(rd.getString("adminUser"));
        r.setBody(rd.getString("body"));
        Long sentAt = rd.getLong("sentAt");
        r.setSentAt(sentAt != null ? sentAt : 0L);
        replies.add(r);
      }
    }
    msg.setReplies(replies);
    return msg;
  }
}
