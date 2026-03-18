package org.waveprotocol.box.server.authentication;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.waveprotocol.wave.util.logging.Log;

/** Helpers to adapt container sessions to WebSession. */
public final class WebSessions {
  private static final Log LOG = Log.get(WebSessions.class);
  private WebSessions() {}

  public static WebSession from(HttpServletRequest req, boolean create) {
    if (req == null) return null;
    try {
      HttpSession js = req.getSession(create);
      return (js != null) ? wrap(js) : null;
    } catch (IllegalStateException ise) {
      if (LOG.isFineLoggable()) {
        LOG.fine("Session lookup skipped (" + ise.getMessage() + ")");
      }
      return null;
    } catch (Throwable t) {
      LOG.warning("Session lookup failed; returning null", t);
      return null;
    }
  }

  public static WebSession wrap(HttpSession js) {
    return (js == null) ? null : new JakartaWebSession(js);
  }

  static final class JakartaWebSession implements WebSession {
    private final HttpSession d;
    JakartaWebSession(HttpSession d) { this.d = d; }
    @Override public Object getAttribute(String name) { return d.getAttribute(name); }
    @Override public void setAttribute(String name, Object value) { d.setAttribute(name, value); }
    @Override public void removeAttribute(String name) { d.removeAttribute(name); }
  }
}

