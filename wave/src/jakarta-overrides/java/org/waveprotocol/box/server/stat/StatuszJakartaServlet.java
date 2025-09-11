package org.waveprotocol.box.server.stat;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Minimal status endpoint for Jakarta path. Returns plain "ok".
 */
public class StatuszJakartaServlet extends HttpServlet {
  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    byte[] out = "ok\n".getBytes(StandardCharsets.UTF_8);
    resp.setStatus(HttpServletResponse.SC_OK);
    resp.setContentType("text/plain; charset=utf-8");
    resp.setContentLength(out.length);
    try (var os = resp.getOutputStream()) {
      os.write(out);
      os.flush();
    }
  }
}
