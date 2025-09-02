package org.waveprotocol.box.server.poc;

import java.io.IOException;
import javax.servlet.*;
import javax.servlet.http.HttpServletResponse;

/**
 * Simple security headers filter registered programmatically as part of the POC.
 */
public class SecurityHeadersFilter implements Filter {
  @Override
  public void init(FilterConfig filterConfig) {}

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    if (response instanceof HttpServletResponse) {
      HttpServletResponse resp = (HttpServletResponse) response;
      resp.setHeader("X-Content-Type-Options", "nosniff");
      resp.setHeader("Referrer-Policy", "no-referrer");
      resp.setHeader("Content-Security-Policy", "default-src 'self'");
    }
    chain.doFilter(request, response);
  }

  @Override
  public void destroy() {}
}

