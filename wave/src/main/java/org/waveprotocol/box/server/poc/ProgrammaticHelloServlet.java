package org.waveprotocol.box.server.poc;

import java.io.IOException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Minimal servlet registered programmatically as a POC for Jakarta migration.
 */
public class ProgrammaticHelloServlet extends HttpServlet {
  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    resp.setStatus(HttpServletResponse.SC_OK);
    resp.setContentType("text/plain; charset=UTF-8");
    resp.getWriter().write("hello from programmatic servlet\n");
  }
}

