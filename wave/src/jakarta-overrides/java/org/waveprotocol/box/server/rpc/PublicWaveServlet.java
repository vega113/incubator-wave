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

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.waveprotocol.box.server.CoreSettingsNames;
import org.waveprotocol.box.server.frontend.CommittedWaveletSnapshot;
import org.waveprotocol.box.server.rpc.render.ServerHtmlRenderer;
import org.waveprotocol.box.server.util.WaveletDataUtil;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.box.server.waveserver.WaveServerException;
import org.waveprotocol.wave.model.id.InvalidIdException;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.ParticipantIdUtil;
import org.waveprotocol.wave.model.wave.data.ObservableWaveletData;
import org.waveprotocol.wave.model.wave.data.ReadableWaveletData;
import org.waveprotocol.wave.model.wave.data.WaveViewData;
import org.waveprotocol.wave.model.wave.data.impl.WaveViewDataImpl;
import org.waveprotocol.wave.util.logging.Log;

import java.io.IOException;
import java.util.Set;

/**
 * Serves server-rendered read-only HTML for public waves.
 *
 * <p>A wave is "public" if the shared domain participant (e.g. {@code @example.com})
 * is among the wavelet's participants. Public waves are viewable by anyone,
 * including unauthenticated users.
 *
 * <p>Mapped to {@code /wave/*} and expects URLs of the form
 * {@code /wave/{domain}!{id}} (the modern serialised WaveId format).
 *
 * <p>Non-public, non-existent, or malformed wave IDs all return 404 to avoid
 * leaking the existence of private waves.
 *
 * @see ServerHtmlRenderer
 */
@SuppressWarnings("serial")
public final class PublicWaveServlet extends HttpServlet {
  private static final Log LOG = Log.get(PublicWaveServlet.class);

  private final WaveletProvider waveletProvider;
  private final String domain;

  @Inject
  public PublicWaveServlet(
      WaveletProvider waveletProvider,
      @Named(CoreSettingsNames.WAVE_SERVER_DOMAIN) String domain) {
    this.waveletProvider = waveletProvider;
    this.domain = domain;
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    // Extract wave ID from path: /wave/{waveId}
    String pathInfo = req.getPathInfo();
    if (pathInfo == null || pathInfo.length() <= 1) {
      resp.sendError(HttpServletResponse.SC_NOT_FOUND);
      return;
    }
    // Strip leading '/'
    String waveIdStr = pathInfo.substring(1);

    // Parse the wave ID
    WaveId waveId;
    try {
      waveId = WaveId.checkedDeserialise(waveIdStr);
    } catch (InvalidIdException e) {
      resp.sendError(HttpServletResponse.SC_NOT_FOUND);
      return;
    }

    try {
      // Look up all wavelet IDs in this wave
      ImmutableSet<WaveletId> waveletIds = waveletProvider.getWaveletIds(waveId);
      if (waveletIds == null || waveletIds.isEmpty()) {
        resp.sendError(HttpServletResponse.SC_NOT_FOUND);
        return;
      }

      // Find the conversational root wavelet
      WaveletId convRootId = null;
      for (WaveletId wid : waveletIds) {
        if (wid.getId().equals("conv+root")) {
          convRootId = wid;
          break;
        }
      }
      if (convRootId == null) {
        resp.sendError(HttpServletResponse.SC_NOT_FOUND);
        return;
      }

      WaveletName waveletName = WaveletName.of(waveId, convRootId);

      // Get the snapshot
      CommittedWaveletSnapshot committedSnapshot = waveletProvider.getSnapshot(waveletName);
      if (committedSnapshot == null) {
        resp.sendError(HttpServletResponse.SC_NOT_FOUND);
        return;
      }
      ReadableWaveletData snapshot = committedSnapshot.snapshot;

      // Check if this wave is public (has the shared domain participant)
      ParticipantId domainParticipant =
          ParticipantIdUtil.makeUnsafeSharedDomainParticipantId(domain);
      Set<ParticipantId> participants = snapshot.getParticipants();
      if (!participants.contains(domainParticipant)) {
        // Not public -- return 404 to avoid leaking existence
        resp.sendError(HttpServletResponse.SC_NOT_FOUND);
        return;
      }

      // Build a WaveViewData for the renderer
      ObservableWaveletData waveletData = WaveletDataUtil.copyWavelet(snapshot);
      WaveViewData waveViewData = WaveViewDataImpl.create(waveId);
      waveViewData.addWavelet(waveletData);

      // Extract title and description for SEO
      String title = extractTitle(waveletData);
      String description = extractDescription(waveletData);
      if (title.isEmpty()) {
        title = "Wave";
      }

      // Render the wave content using ServerHtmlRenderer
      // Use a synthetic anonymous viewer for rendering
      ParticipantId anonymousViewer = ParticipantId.ofUnsafe("@" + domain);
      String waveContentHtml = ServerHtmlRenderer.renderWave(waveViewData, anonymousViewer);

      // Build the full page
      String fullPage = renderPublicWavePage(title, description, waveContentHtml, waveId);

      // Set response headers
      resp.setContentType("text/html");
      resp.setCharacterEncoding("UTF-8");
      resp.setStatus(HttpServletResponse.SC_OK);
      resp.setHeader("Cache-Control", "public, max-age=60");

      resp.getWriter().write(fullPage);

    } catch (WaveServerException e) {
      LOG.warning("Error serving public wave " + waveIdStr, e);
      resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
  }

  /**
   * Extracts the title from the root blip of a wavelet by looking at the
   * first text content of the first document.
   */
  private String extractTitle(ObservableWaveletData waveletData) {
    try {
      // The root blip document ID typically starts with "b+"
      for (String docId : waveletData.getDocumentIds()) {
        if (docId.startsWith("b+")) {
          ReadableWaveletData readable = waveletData;
          var blipData = readable.getDocument(docId);
          if (blipData != null) {
            // Extract text from the document content operation
            var docOp = blipData.getContent().asOperation();
            if (docOp != null) {
              StringBuilder text = new StringBuilder();
              docOp.apply(new org.waveprotocol.wave.model.document.operation.DocOpCursor() {
                @Override public void characters(String chars) { text.append(chars); }
                @Override public void elementStart(String type,
                    org.waveprotocol.wave.model.document.operation.Attributes attrs) {}
                @Override public void elementEnd() {}
                @Override public void retain(int itemCount) {}
                @Override public void deleteCharacters(String chars) {}
                @Override public void deleteElementStart(String type,
                    org.waveprotocol.wave.model.document.operation.Attributes attrs) {}
                @Override public void deleteElementEnd() {}
                @Override public void replaceAttributes(
                    org.waveprotocol.wave.model.document.operation.Attributes oldAttrs,
                    org.waveprotocol.wave.model.document.operation.Attributes newAttrs) {}
                @Override public void updateAttributes(
                    org.waveprotocol.wave.model.document.operation.AttributesUpdate attrUpdate) {}
                @Override public void annotationBoundary(
                    org.waveprotocol.wave.model.document.operation.AnnotationBoundaryMap map) {}
              });
              String fullText = text.toString().trim();
              if (!fullText.isEmpty()) {
                // Take up to the first line break or first 200 chars as title
                int lineBreak = fullText.indexOf('\n');
                if (lineBreak > 0 && lineBreak < 200) {
                  return fullText.substring(0, lineBreak).trim();
                }
                return fullText.substring(0, Math.min(fullText.length(), 200)).trim();
              }
            }
          }
          break; // Only check the first blip
        }
      }
    } catch (Exception e) {
      LOG.warning("Failed to extract title from wave", e);
    }
    return "";
  }

  /**
   * Extracts a description snippet from the wave content (first ~300 chars of text).
   */
  private String extractDescription(ObservableWaveletData waveletData) {
    try {
      StringBuilder allText = new StringBuilder();
      for (String docId : waveletData.getDocumentIds()) {
        if (!docId.startsWith("b+")) continue;
        var blipData = waveletData.getDocument(docId);
        if (blipData == null) continue;
        var docOp = blipData.getContent().asOperation();
        if (docOp == null) continue;
        docOp.apply(new org.waveprotocol.wave.model.document.operation.DocOpCursor() {
          @Override public void characters(String chars) { allText.append(chars).append(' '); }
          @Override public void elementStart(String type,
              org.waveprotocol.wave.model.document.operation.Attributes attrs) {}
          @Override public void elementEnd() {}
          @Override public void retain(int itemCount) {}
          @Override public void deleteCharacters(String chars) {}
          @Override public void deleteElementStart(String type,
              org.waveprotocol.wave.model.document.operation.Attributes attrs) {}
          @Override public void deleteElementEnd() {}
          @Override public void replaceAttributes(
              org.waveprotocol.wave.model.document.operation.Attributes oldAttrs,
              org.waveprotocol.wave.model.document.operation.Attributes newAttrs) {}
          @Override public void updateAttributes(
              org.waveprotocol.wave.model.document.operation.AttributesUpdate attrUpdate) {}
          @Override public void annotationBoundary(
              org.waveprotocol.wave.model.document.operation.AnnotationBoundaryMap map) {}
        });
        if (allText.length() >= 300) break;
      }
      String desc = allText.toString().trim();
      if (desc.length() > 300) {
        desc = desc.substring(0, 297) + "...";
      }
      return desc;
    } catch (Exception e) {
      LOG.warning("Failed to extract description from wave", e);
    }
    return "";
  }

  /**
   * Renders a full HTML page wrapping the SSR wave content with the SupaWave
   * ocean theme, SEO meta tags, header, footer, and sign-in CTA.
   */
  private String renderPublicWavePage(String title, String description,
      String waveContentHtml, WaveId waveId) {
    String escapedTitle = HtmlRenderer.escapeHtml(title);
    String escapedDesc = HtmlRenderer.escapeHtml(description);
    String waveIdStr = HtmlRenderer.escapeHtml(waveId.serialise());

    StringBuilder sb = new StringBuilder(8192);

    // DOCTYPE + head
    sb.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
    sb.append("<meta charset=\"UTF-8\">\n");
    sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n");
    sb.append("<link rel=\"icon\" type=\"image/svg+xml\" href=\"/static/favicon.svg\">\n");
    sb.append("<link rel=\"alternate icon\" href=\"/static/favicon.ico\">\n");
    sb.append("<title>").append(escapedTitle).append(" - SupaWave</title>\n");

    // SEO meta tags
    sb.append("<meta name=\"description\" content=\"").append(escapedDesc).append("\">\n");
    sb.append("<meta property=\"og:title\" content=\"").append(escapedTitle).append("\">\n");
    sb.append("<meta property=\"og:description\" content=\"").append(escapedDesc).append("\">\n");
    sb.append("<meta property=\"og:type\" content=\"article\">\n");
    sb.append("<meta property=\"og:url\" content=\"/wave/").append(waveIdStr).append("\">\n");
    sb.append("<meta name=\"twitter:card\" content=\"summary\">\n");
    sb.append("<meta name=\"twitter:title\" content=\"").append(escapedTitle).append("\">\n");
    sb.append("<meta name=\"twitter:description\" content=\"").append(escapedDesc).append("\">\n");
    sb.append("<meta name=\"robots\" content=\"index, follow\">\n");

    // CSS
    sb.append("<style>\n");
    sb.append(PAGE_CSS);
    sb.append("</style>\n");
    sb.append("</head>\n<body>\n");

    // Header / nav bar
    sb.append("<header class=\"pw-header\">\n");
    sb.append("  <a href=\"/\" class=\"pw-brand\">\n");
    sb.append("    <svg width=\"28\" height=\"28\" viewBox=\"0 0 48 48\" fill=\"none\" xmlns=\"http://www.w3.org/2000/svg\" style=\"vertical-align:middle;\">\n");
    sb.append("      <defs>\n");
    sb.append("        <linearGradient id=\"wg\" x1=\"0%\" y1=\"0%\" x2=\"100%\" y2=\"100%\">\n");
    sb.append("          <stop offset=\"0%\" stop-color=\"#0077b6\"/>\n");
    sb.append("          <stop offset=\"50%\" stop-color=\"#00b4d8\"/>\n");
    sb.append("          <stop offset=\"100%\" stop-color=\"#90e0ef\"/>\n");
    sb.append("        </linearGradient>\n");
    sb.append("      </defs>\n");
    sb.append("      <path d=\"M8 32 C12 20, 16 28, 20 18 C24 8, 28 24, 32 14 C36 4, 40 20, 44 12\" ");
    sb.append("stroke=\"url(#wg)\" stroke-width=\"4\" stroke-linecap=\"round\" fill=\"none\">\n");
    sb.append("        <animate attributeName=\"d\" dur=\"4s\" repeatCount=\"indefinite\" ");
    sb.append("values=\"M8 32 C12 20,16 28,20 18 C24 8,28 24,32 14 C36 4,40 20,44 12;");
    sb.append("M8 28 C12 24,16 20,20 22 C24 14,28 18,32 10 C36 8,40 16,44 14;");
    sb.append("M8 32 C12 20,16 28,20 18 C24 8,28 24,32 14 C36 4,40 20,44 12\"/>\n");
    sb.append("      </path>\n");
    sb.append("    </svg>\n");
    sb.append("    <span class=\"pw-brand-name\">SupaWave</span>\n");
    sb.append("  </a>\n");
    sb.append("  <div class=\"pw-nav-links\">\n");
    sb.append("    <a href=\"/auth/signin\" class=\"pw-nav-link pw-nav-signin\">Sign In</a>\n");
    sb.append("    <a href=\"/auth/register\" class=\"pw-nav-link pw-nav-register\">Register</a>\n");
    sb.append("  </div>\n");
    sb.append("</header>\n");

    // Wave title bar
    sb.append("<div class=\"pw-title-bar\">\n");
    sb.append("  <h1 class=\"pw-title\">").append(escapedTitle).append("</h1>\n");
    sb.append("  <div class=\"pw-read-only-badge\">Read Only</div>\n");
    sb.append("</div>\n");

    // Wave content
    sb.append("<main class=\"pw-content\">\n");
    sb.append(waveContentHtml);
    sb.append("</main>\n");

    // CTA
    sb.append("<div class=\"pw-cta\">\n");
    sb.append("  <a href=\"/auth/signin\" class=\"pw-cta-btn\">Sign in to collaborate</a>\n");
    sb.append("  <span class=\"pw-cta-text\">or <a href=\"/auth/register\">create an account</a> to start contributing</span>\n");
    sb.append("</div>\n");

    // Footer
    sb.append("<footer class=\"pw-footer\">\n");
    sb.append("  <div style=\"margin-bottom:8px;\">\n");
    sb.append("    <a href=\"/terms\">Terms</a> &middot; ");
    sb.append("<a href=\"/privacy\">Privacy</a> &middot; ");
    sb.append("<a href=\"/contact\">Contact</a>\n");
    sb.append("  </div>\n");
    sb.append("  Powered by <a href=\"https://incubator.apache.org/projects/wave.html\">Apache Wave</a>\n");
    sb.append("  &middot; @").append(HtmlRenderer.escapeHtml(domain)).append("\n");
    sb.append("</footer>\n");

    sb.append("</body>\n</html>\n");
    return sb.toString();
  }

  // =========================================================================
  // CSS for the public wave page
  // =========================================================================

  private static final String PAGE_CSS =
      "*, *::before, *::after { box-sizing: border-box; }\n"
      + "body {\n"
      + "  margin: 0; padding: 0;\n"
      + "  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto,\n"
      + "    Oxygen, Ubuntu, Cantarell, 'Helvetica Neue', Arial, sans-serif;\n"
      + "  color: #1a202c;\n"
      + "  background: #f0f4f8;\n"
      + "}\n"
      + "a { color: #0077b6; text-decoration: none; }\n"
      + "a:hover { text-decoration: underline; }\n"
      // Header
      + ".pw-header {\n"
      + "  display: flex; align-items: center; justify-content: space-between;\n"
      + "  padding: 12px 24px;\n"
      + "  background: #fff;\n"
      + "  border-bottom: 1px solid #e2e8f0;\n"
      + "  box-shadow: 0 1px 3px rgba(0,0,0,0.06);\n"
      + "}\n"
      + ".pw-brand {\n"
      + "  display: flex; align-items: center; gap: 8px;\n"
      + "  text-decoration: none;\n"
      + "}\n"
      + ".pw-brand-name {\n"
      + "  font-size: 20px; font-weight: 700; color: #0077b6;\n"
      + "}\n"
      + ".pw-nav-links { display: flex; gap: 8px; align-items: center; }\n"
      + ".pw-nav-link {\n"
      + "  padding: 6px 16px; font-size: 14px; font-weight: 600;\n"
      + "  border-radius: 6px; transition: all 0.2s;\n"
      + "}\n"
      + ".pw-nav-signin {\n"
      + "  color: #0077b6; border: 1.5px solid #0077b6;\n"
      + "}\n"
      + ".pw-nav-signin:hover { background: #f0f8ff; text-decoration: none; }\n"
      + ".pw-nav-register {\n"
      + "  color: #fff; background: #0077b6;\n"
      + "}\n"
      + ".pw-nav-register:hover { background: #005f8f; text-decoration: none; }\n"
      // Title bar
      + ".pw-title-bar {\n"
      + "  max-width: 900px; margin: 24px auto 0; padding: 0 16px;\n"
      + "  display: flex; align-items: center; gap: 12px;\n"
      + "}\n"
      + ".pw-title {\n"
      + "  font-size: 28px; font-weight: 700; color: #1a202c;\n"
      + "  margin: 0; flex: 1;\n"
      + "}\n"
      + ".pw-read-only-badge {\n"
      + "  font-size: 11px; font-weight: 600; text-transform: uppercase;\n"
      + "  color: #718096; background: #e2e8f0; border-radius: 4px;\n"
      + "  padding: 3px 8px; letter-spacing: 0.5px;\n"
      + "}\n"
      // Main content area
      + ".pw-content {\n"
      + "  max-width: 900px; margin: 16px auto; padding: 0 16px;\n"
      + "}\n"
      // Wave content styles (matches ServerHtmlRenderer CSS)
      + ".pw-content .wave { max-width: 100%; }\n"
      + ".pw-content .conversation {\n"
      + "  background: #fff; border-radius: 8px;\n"
      + "  box-shadow: 0 1px 3px rgba(0,0,0,0.12); padding: 16px;\n"
      + "}\n"
      + ".pw-content .participants {\n"
      + "  display: flex; flex-wrap: wrap; gap: 8px;\n"
      + "  padding: 8px 0; border-bottom: 1px solid #e2e8f0;\n"
      + "  margin-bottom: 16px;\n"
      + "}\n"
      + ".pw-content .participant {\n"
      + "  display: inline-flex; align-items: center; gap: 4px;\n"
      + "  font-size: 0.85em; color: #4a5568;\n"
      + "}\n"
      + ".pw-content .participant-avatar {\n"
      + "  display: inline-flex; align-items: center; justify-content: center;\n"
      + "  width: 24px; height: 24px; border-radius: 50%;\n"
      + "  background: #0077b6; color: #fff;\n"
      + "  font-size: 0.75em; font-weight: bold;\n"
      + "}\n"
      + ".pw-content .blip {\n"
      + "  margin-bottom: 12px; padding: 8px 0;\n"
      + "  border-bottom: 1px solid #edf2f7;\n"
      + "}\n"
      + ".pw-content .blip:last-child { border-bottom: none; }\n"
      + ".pw-content .blip-meta {\n"
      + "  display: flex; align-items: center; gap: 8px; margin-bottom: 4px;\n"
      + "}\n"
      + ".pw-content .blip-author {\n"
      + "  font-weight: 600; color: #0077b6; font-size: 0.9em;\n"
      + "}\n"
      + ".pw-content .blip-time {\n"
      + "  font-size: 0.8em; color: #a0aec0;\n"
      + "}\n"
      + ".pw-content .blip-content { line-height: 1.6; }\n"
      + ".pw-content .blip-content p { margin: 0.25em 0; }\n"
      + ".pw-content .blip-content h1,\n"
      + ".pw-content .blip-content h2,\n"
      + ".pw-content .blip-content h3,\n"
      + ".pw-content .blip-content h4 { margin: 0.5em 0 0.25em; }\n"
      + ".pw-content .blip-content a { color: #0077b6; text-decoration: underline; }\n"
      + ".pw-content .blip-replies {\n"
      + "  margin-left: 24px; padding-left: 12px;\n"
      + "  border-left: 2px solid #e2e8f0;\n"
      + "}\n"
      + ".pw-content .inline-thread {\n"
      + "  margin-left: 24px; padding-left: 12px;\n"
      + "  border-left: 2px solid #90e0ef;\n"
      + "}\n"
      // CTA
      + ".pw-cta {\n"
      + "  max-width: 900px; margin: 24px auto; padding: 24px 16px;\n"
      + "  text-align: center;\n"
      + "}\n"
      + ".pw-cta-btn {\n"
      + "  display: inline-block; padding: 12px 32px;\n"
      + "  font-size: 16px; font-weight: 600;\n"
      + "  color: #fff; background: #0077b6;\n"
      + "  border-radius: 8px;\n"
      + "  transition: background 0.2s, box-shadow 0.2s;\n"
      + "}\n"
      + ".pw-cta-btn:hover {\n"
      + "  background: #005f8f;\n"
      + "  box-shadow: 0 4px 12px rgba(0,119,182,0.3);\n"
      + "  text-decoration: none;\n"
      + "}\n"
      + ".pw-cta-text {\n"
      + "  display: block; margin-top: 8px;\n"
      + "  font-size: 14px; color: #718096;\n"
      + "}\n"
      // Footer
      + ".pw-footer {\n"
      + "  text-align: center; padding: 24px;\n"
      + "  color: #888; font-size: 13px;\n"
      + "  border-top: 1px solid #e2e8f0;\n"
      + "  margin-top: 24px;\n"
      + "}\n"
      + ".pw-footer a { color: #0077b6; }\n"
      // Responsive
      + "@media (max-width: 640px) {\n"
      + "  .pw-header { padding: 10px 12px; }\n"
      + "  .pw-title-bar { margin-top: 16px; }\n"
      + "  .pw-title { font-size: 22px; }\n"
      + "  .pw-content .conversation { padding: 12px; }\n"
      + "}\n";
}
