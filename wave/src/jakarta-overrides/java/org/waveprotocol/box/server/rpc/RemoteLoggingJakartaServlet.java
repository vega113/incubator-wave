package org.waveprotocol.box.server.rpc;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.waveprotocol.wave.util.logging.Log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Minimal Jakarta-compatible remote logging endpoint. Accepts POST bodies
 * (including text/x-gwt-rpc or JSON/text) and writes them to server logs.
 * This is a best-effort compatibility shim; it does not implement full GWT RPC.
 */
public class RemoteLoggingJakartaServlet extends HttpServlet {
  private static final Log LOG = Log.get(RemoteLoggingJakartaServlet.class);

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    // Read body upto 32 KiB to avoid unbounded growth
    final int max = 32 * 1024; // 32 KiB hard cap
    int cl = req.getContentLength();
    int initial = 256;
    if (cl > 0) {
      // Bound initial capacity strictly to max to avoid large preallocation
      initial = Math.min(max, Math.max(initial, cl));
    }
    StringBuilder sb = new StringBuilder(initial);
    char[] buf = new char[2048];
    int n, total = 0;
    try (var reader = req.getReader()) {
      while ((n = reader.read(buf)) != -1) {
        if (total >= max) break;
        int remaining = max - total;
        int toAppend = Math.min(n, remaining);
        sb.append(buf, 0, toAppend);
        total += toAppend;
        if (toAppend < n) break; // stop if we truncated this chunk
      }
    }
    String payload = sb.toString();
    String ct = req.getContentType();
    if (ct == null) ct = "";

    if (ct.startsWith("text/x-gwt-rpc")) {
      // Don’t try to parse; log and ack.
      LOG.info("[remote_log][gwt] " + summarize(payload));
      resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
      return;
    }

    LOG.info("[remote_log] " + summarize(payload));
    resp.setStatus(HttpServletResponse.SC_OK);
    resp.setContentType("text/plain; charset=utf-8");
    resp.getOutputStream().write("OK".getBytes(StandardCharsets.UTF_8));
  }

  private static String summarize(String s) {
    if (s == null) return "<null>";
    s = s.replace('\r', ' ').replace('\n', ' ');
    return (s.length() > 500) ? (s.substring(0, 500) + "…") : s;
  }
}
