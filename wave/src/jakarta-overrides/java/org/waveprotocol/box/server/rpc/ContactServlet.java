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
import com.google.inject.name.Named;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.waveprotocol.box.server.CoreSettingsNames;
import org.waveprotocol.box.server.account.AccountData;
import org.waveprotocol.box.server.account.HumanAccountData;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.authentication.WebSession;
import org.waveprotocol.box.server.authentication.WebSessions;
import org.waveprotocol.box.server.mail.MailException;
import org.waveprotocol.box.server.mail.MailProvider;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.box.server.persistence.ContactMessageStore;
import org.waveprotocol.box.server.persistence.ContactMessageStore.ContactMessage;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.util.logging.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Servlet for the Contact Us feature.
 *
 * <ul>
 *   <li>{@code GET /contact} - Render the contact form page</li>
 *   <li>{@code POST /contact} - Submit a contact message (JSON)</li>
 * </ul>
 */
@SuppressWarnings("serial")
public final class ContactServlet extends HttpServlet {
  private static final Log LOG = Log.get(ContactServlet.class);

  private final SessionManager sessionManager;
  private final AccountStore accountStore;
  private final ContactMessageStore contactMessageStore;
  private final MailProvider mailProvider;
  private final String domain;

  @Inject
  public ContactServlet(SessionManager sessionManager,
                         AccountStore accountStore,
                         ContactMessageStore contactMessageStore,
                         MailProvider mailProvider,
                         @Named(CoreSettingsNames.WAVE_SERVER_DOMAIN) String domain) {
    this.sessionManager = sessionManager;
    this.accountStore = accountStore;
    this.contactMessageStore = contactMessageStore;
    this.mailProvider = mailProvider;
    this.domain = domain;
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    // Attempt to get logged-in user — anonymous users get an empty form
    WebSession session = WebSessions.from(req, false);
    ParticipantId user = sessionManager.getLoggedInUser(session);

    String email = "";
    String name = "";
    if (user != null) {
      try {
        AccountData acct = accountStore.getAccount(user);
        if (acct != null && acct.isHuman()) {
          HumanAccountData human = acct.asHuman();
          if (human.getEmail() != null) {
            email = human.getEmail();
          }
        }
      } catch (PersistenceException e) {
        LOG.warning("Failed to load account data for contact page", e);
      }
      String username = user.getAddress();
      int atIdx = username.indexOf('@');
      if (atIdx > 0) {
        name = username.substring(0, atIdx);
      }
    }

    resp.setContentType("text/html;charset=utf-8");
    resp.setCharacterEncoding("UTF-8");
    resp.getWriter().write(HtmlRenderer.renderContactPage(email, name, domain));
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    WebSession session = WebSessions.from(req, false);
    ParticipantId user = sessionManager.getLoggedInUser(session);
    if (user == null) {
      sendJsonError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Not authenticated");
      return;
    }

    String body = readBody(req);
    String name = extractJsonField(body, "name");
    String subject = extractJsonField(body, "subject");
    String message = extractJsonField(body, "message");

    if (name == null || name.trim().isEmpty()) {
      sendJsonError(resp, HttpServletResponse.SC_BAD_REQUEST, "Name is required");
      return;
    }
    if (message == null || message.trim().isEmpty()) {
      sendJsonError(resp, HttpServletResponse.SC_BAD_REQUEST, "Message is required");
      return;
    }
    if (subject == null || subject.trim().isEmpty()) {
      subject = "General Inquiry";
    }

    // Get user's email
    String email = "";
    try {
      AccountData acct = accountStore.getAccount(user);
      if (acct != null && acct.isHuman()) {
        HumanAccountData human = acct.asHuman();
        if (human.getEmail() != null) {
          email = human.getEmail();
        }
      }
    } catch (PersistenceException e) {
      LOG.warning("Failed to load account for contact form submit", e);
    }

    // Store the message
    ContactMessage msg = new ContactMessage();
    msg.setUserId(user.getAddress());
    msg.setName(name.trim());
    msg.setEmail(email);
    msg.setSubject(subject.trim());
    msg.setMessage(message.trim());
    msg.setStatus(ContactMessageStore.STATUS_NEW);
    msg.setCreatedAt(System.currentTimeMillis());
    msg.setIp(getClientIp(req));

    try {
      String id = contactMessageStore.storeMessage(msg);
      LOG.info("Contact message stored: id=" + id + " from=" + user.getAddress()
          + " subject=" + subject);

      // Send notification email to admin (best-effort, don't fail the request)
      try {
        String adminSubject = "[SupaWave Contact] " + subject + " from " + name;
        String htmlBody = "<h3>New Contact Form Submission</h3>"
            + "<p><strong>From:</strong> " + HtmlRenderer.escapeHtml(name)
            + " (" + HtmlRenderer.escapeHtml(email.isEmpty() ? user.getAddress() : email) + ")</p>"
            + "<p><strong>Subject:</strong> " + HtmlRenderer.escapeHtml(subject) + "</p>"
            + "<p><strong>Message:</strong></p>"
            + "<div style=\"padding:12px;background:#f5f5f5;border-radius:8px;\">"
            + HtmlRenderer.escapeHtml(message).replace("\n", "<br>") + "</div>"
            + "<p style=\"color:#888;font-size:12px;\">IP: " + HtmlRenderer.escapeHtml(msg.getIp()) + "</p>";
        mailProvider.sendEmail(email.isEmpty() ? "admin@" + domain : email, adminSubject, htmlBody);
      } catch (MailException e) {
        LOG.warning("Failed to send contact notification email: " + e.getMessage());
      }

      setJsonUtf8(resp);
      resp.getWriter().write("{\"ok\":true,\"id\":\"" + id + "\"}");
    } catch (PersistenceException e) {
      LOG.severe("Failed to store contact message", e);
      sendJsonError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to store message");
    }
  }

  private static String getClientIp(HttpServletRequest req) {
    String forwarded = req.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isEmpty()) {
      return forwarded.split(",")[0].trim();
    }
    return req.getRemoteAddr();
  }

  private static String readBody(HttpServletRequest req) throws IOException {
    StringBuilder sb = new StringBuilder(512);
    char[] buf = new char[512];
    int n;
    BufferedReader reader = req.getReader();
    while ((n = reader.read(buf)) != -1) {
      sb.append(buf, 0, n);
      if (sb.length() > 16384) break; // safety limit
    }
    return sb.toString();
  }

  /**
   * Crude JSON field extractor for simple {"key":"value"} bodies.
   */
  static String extractJsonField(String json, String field) {
    if (json == null) return null;
    String key = "\"" + field + "\"";
    int idx = json.indexOf(key);
    if (idx < 0) return null;
    int colon = json.indexOf(':', idx + key.length());
    if (colon < 0) return null;
    int qStart = json.indexOf('"', colon + 1);
    if (qStart < 0) return null;
    // Handle escaped quotes in value
    int qEnd = qStart + 1;
    while (qEnd < json.length()) {
      char c = json.charAt(qEnd);
      if (c == '\\') {
        qEnd += 2; // skip escaped char
        continue;
      }
      if (c == '"') break;
      qEnd++;
    }
    if (qEnd >= json.length()) return null;
    String raw = json.substring(qStart + 1, qEnd);
    // Unescape basic sequences
    return raw.replace("\\n", "\n").replace("\\t", "\t")
        .replace("\\\"", "\"").replace("\\\\", "\\");
  }

  private static void setJsonUtf8(HttpServletResponse resp) {
    resp.setContentType("application/json");
    resp.setCharacterEncoding("UTF-8");
  }

  private static void sendJsonError(HttpServletResponse resp, int status, String message)
      throws IOException {
    resp.setStatus(status);
    setJsonUtf8(resp);
    resp.getWriter().write("{\"error\":\"" + message.replace("\"", "\\\"") + "\"}");
  }
}
