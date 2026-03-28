package org.waveprotocol.box.server.robots;

import com.google.common.base.Strings;
import com.google.common.cache.CacheBuilder;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.waveprotocol.box.server.CoreSettingsNames;
import org.waveprotocol.box.server.account.AccountData;
import org.waveprotocol.box.server.account.RobotAccountData;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.authentication.WebSessions;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.box.server.robots.register.RobotRegistrar;
import org.waveprotocol.box.server.robots.util.RobotsUtil.RobotRegistrationException;
import org.waveprotocol.box.server.rpc.HtmlRenderer;
import org.waveprotocol.wave.model.id.TokenGenerator;
import org.waveprotocol.wave.model.wave.InvalidParticipantAddress;
import org.waveprotocol.wave.model.wave.ParticipantId;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("serial")
@Singleton
public final class RobotDashboardServlet extends HttpServlet {
  private static final int XSRF_TOKEN_LENGTH = 12;
  private static final int XSRF_TOKEN_TIMEOUT_HOURS = 12;

  private final String domain;
  private final SessionManager sessionManager;
  private final AccountStore accountStore;
  private final RobotRegistrar robotRegistrar;
  private final TokenGenerator tokenGenerator;
  private final ConcurrentMap<ParticipantId, String> xsrfTokens;

  @Inject
  public RobotDashboardServlet(@Named(CoreSettingsNames.WAVE_SERVER_DOMAIN) String domain,
                               SessionManager sessionManager,
                               AccountStore accountStore,
                               RobotRegistrar robotRegistrar,
                               TokenGenerator tokenGenerator) {
    this.domain = domain;
    this.sessionManager = sessionManager;
    this.accountStore = accountStore;
    this.robotRegistrar = robotRegistrar;
    this.tokenGenerator = tokenGenerator;
    this.xsrfTokens = CacheBuilder.newBuilder()
        .expireAfterWrite(XSRF_TOKEN_TIMEOUT_HOURS, TimeUnit.HOURS)
        .<ParticipantId, String>build()
        .asMap();
  }

  RobotDashboardServlet(String domain,
                        SessionManager sessionManager,
                        AccountStore accountStore,
                        RobotRegistrar robotRegistrar) {
    this.domain = domain;
    this.sessionManager = sessionManager;
    this.accountStore = accountStore;
    this.robotRegistrar = robotRegistrar;
    this.tokenGenerator = length -> "dashboard-xsrf";
    this.xsrfTokens = CacheBuilder.newBuilder()
        .expireAfterWrite(XSRF_TOKEN_TIMEOUT_HOURS, TimeUnit.HOURS)
        .<ParticipantId, String>build()
        .asMap();
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    ParticipantId user = requireUser(req, resp);
    if (user == null) {
      return;
    }
    renderDashboard(resp, user, "", null, HttpServletResponse.SC_OK);
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    ParticipantId user = requireUser(req, resp);
    if (user == null) {
      return;
    }
    if (!hasValidXsrfToken(user, req)) {
      renderDashboard(resp, user, "Invalid XSRF token.", null,
          HttpServletResponse.SC_UNAUTHORIZED);
      return;
    }

    String action = req.getParameter("action");
    if ("register".equals(action)) {
      handleRegister(req, resp, user);
      return;
    }
    if ("update-url".equals(action)) {
      handleUpdateUrl(req, resp, user);
      return;
    }
    if ("rotate-secret".equals(action)) {
      handleRotateSecret(req, resp, user);
      return;
    }
    renderDashboard(resp, user, "Unknown robot action", null, HttpServletResponse.SC_BAD_REQUEST);
  }

  private ParticipantId requireUser(HttpServletRequest req, HttpServletResponse resp)
      throws IOException {
    ParticipantId user = sessionManager.getLoggedInUser(WebSessions.from(req, false));
    if (user == null) {
      resp.sendRedirect("/auth/signin?r=/account/robots");
    }
    return user;
  }

  private void handleRegister(HttpServletRequest req, HttpServletResponse resp, ParticipantId user)
      throws IOException {
    String username = req.getParameter("username");
    String location = req.getParameter("location");
    long tokenExpirySeconds = parseTokenExpiry(req.getParameter("token_expiry"));
    if (Strings.isNullOrEmpty(username) || Strings.isNullOrEmpty(location)) {
      renderDashboard(resp, user, "Robot username and callback URL are required.", null,
          HttpServletResponse.SC_BAD_REQUEST);
      return;
    }

    ParticipantId robotId;
    try {
      robotId = ParticipantId.of(username + "@" + domain);
    } catch (InvalidParticipantAddress e) {
      renderDashboard(resp, user, "Robot username is invalid.", null,
          HttpServletResponse.SC_BAD_REQUEST);
      return;
    }

    try {
      RobotAccountData robotAccount = robotRegistrar.registerNew(robotId, location,
          user.getAddress(), tokenExpirySeconds);
      renderDashboard(resp, user, "Robot registered: " + robotAccount.getId().getAddress(),
          robotAccount.getConsumerSecret(), HttpServletResponse.SC_OK);
    } catch (RobotRegistrationException e) {
      renderDashboard(resp, user, e.getMessage(), null, HttpServletResponse.SC_BAD_REQUEST);
    } catch (PersistenceException e) {
      renderDashboard(resp, user, "Robot registration failed.", null,
          HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
  }

  private void handleUpdateUrl(HttpServletRequest req, HttpServletResponse resp, ParticipantId user)
      throws IOException {
    String robotIdValue = req.getParameter("robotId");
    String location = req.getParameter("location");
    if (Strings.isNullOrEmpty(robotIdValue) || Strings.isNullOrEmpty(location)) {
      renderDashboard(resp, user, "Robot and callback URL are required.", null,
          HttpServletResponse.SC_BAD_REQUEST);
      return;
    }

    RobotAccountData ownedRobot = findOwnedRobot(robotIdValue, user.getAddress());
    if (ownedRobot == null) {
      renderDashboard(resp, user, "You do not own this robot.", null,
          HttpServletResponse.SC_FORBIDDEN);
      return;
    }

    try {
      robotRegistrar.registerOrUpdate(ownedRobot.getId(), location, user.getAddress());
      renderDashboard(resp, user, "Callback URL updated for " + ownedRobot.getId().getAddress(),
          null, HttpServletResponse.SC_OK);
    } catch (RobotRegistrationException e) {
      renderDashboard(resp, user, e.getMessage(), null, HttpServletResponse.SC_BAD_REQUEST);
    } catch (PersistenceException e) {
      renderDashboard(resp, user, "Callback URL update failed.", null,
          HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
  }

  private void handleRotateSecret(HttpServletRequest req, HttpServletResponse resp, ParticipantId user)
      throws IOException {
    String robotIdValue = req.getParameter("robotId");
    if (Strings.isNullOrEmpty(robotIdValue)) {
      renderDashboard(resp, user, "Robot selection is required.", null,
          HttpServletResponse.SC_BAD_REQUEST);
      return;
    }

    RobotAccountData ownedRobot = findOwnedRobot(robotIdValue, user.getAddress());
    if (ownedRobot == null) {
      renderDashboard(resp, user, "You do not own this robot.", null,
          HttpServletResponse.SC_FORBIDDEN);
      return;
    }

    try {
      RobotAccountData rotatedRobot = robotRegistrar.rotateSecret(ownedRobot.getId());
      renderDashboard(resp, user, "Secret rotated for " + ownedRobot.getId().getAddress(),
          rotatedRobot != null ? rotatedRobot.getConsumerSecret() : null,
          HttpServletResponse.SC_OK);
    } catch (RobotRegistrationException e) {
      renderDashboard(resp, user, e.getMessage(), null, HttpServletResponse.SC_BAD_REQUEST);
    } catch (PersistenceException e) {
      renderDashboard(resp, user, "Secret rotation failed.", null,
          HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
  }

  private RobotAccountData findOwnedRobot(String robotIdValue, String ownerAddress) {
    RobotAccountData ownedRobot = null;
    try {
      ParticipantId robotId = ParticipantId.of(robotIdValue);
      AccountData account = accountStore.getAccount(robotId);
      if (account != null && account.isRobot()) {
        RobotAccountData robotAccount = account.asRobot();
        if (ownerAddress.equals(robotAccount.getOwnerAddress())) {
          ownedRobot = robotAccount;
        }
      }
    } catch (InvalidParticipantAddress | PersistenceException ignored) {
      ownedRobot = null;
    }
    return ownedRobot;
  }

  private long parseTokenExpiry(String tokenExpiryValue) {
    long tokenExpirySeconds = 0L;
    if (!Strings.isNullOrEmpty(tokenExpiryValue)) {
      try {
        tokenExpirySeconds = Long.parseLong(tokenExpiryValue);
      } catch (NumberFormatException ignored) {
        tokenExpirySeconds = 0L;
      }
    }
    if (tokenExpirySeconds < 0L) {
      tokenExpirySeconds = 0L;
    }
    return tokenExpirySeconds;
  }

  private boolean hasValidXsrfToken(ParticipantId user, HttpServletRequest req) {
    String token = req.getParameter("token");
    String expectedToken = xsrfTokens.get(user);
    boolean validToken = !Strings.isNullOrEmpty(token)
        && !Strings.isNullOrEmpty(expectedToken)
        && token.equals(expectedToken);
    return validToken;
  }

  private String getOrGenerateXsrfToken(ParticipantId user) {
    String token = xsrfTokens.get(user);
    if (Strings.isNullOrEmpty(token)) {
      token = tokenGenerator.generateToken(XSRF_TOKEN_LENGTH);
      xsrfTokens.put(user, token);
    }
    return token;
  }

  private void renderDashboard(HttpServletResponse resp, ParticipantId user, String message,
      String rotatedSecret, int statusCode) throws IOException {
    List<RobotAccountData> ownedRobots;
    try {
      ownedRobots = accountStore.getRobotAccountsOwnedBy(user.getAddress());
      if (ownedRobots == null) {
        ownedRobots = Collections.emptyList();
      }
    } catch (PersistenceException e) {
      ownedRobots = Collections.emptyList();
    }
    resp.setStatus(statusCode);
    resp.setCharacterEncoding("UTF-8");
    resp.setContentType("text/html; charset=UTF-8");
    resp.getWriter().write(renderDashboardPage(user.getAddress(), ownedRobots, message,
        rotatedSecret, getOrGenerateXsrfToken(user)));
  }

  private String renderDashboardPage(String userAddress, List<RobotAccountData> robots,
      String message, String rotatedSecret, String xsrfToken) {
    StringBuilder sb = new StringBuilder(8192);
    sb.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\">");
    sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">");
    sb.append("<title>Robot Control Room</title>");
    sb.append("<link rel=\"icon\" type=\"image/svg+xml\" href=\"/static/favicon.svg\">");
    sb.append("<style>");
    sb.append("body{margin:0;font-family:Georgia,'Times New Roman',serif;background:linear-gradient(180deg,#f3efe6 0%,#e7dcc8 100%);color:#1f2933;}");
    sb.append(".shell{max-width:1180px;margin:0 auto;padding:40px 24px 64px;}");
    sb.append(".hero{display:grid;grid-template-columns:1.3fr .7fr;gap:20px;align-items:start;}");
    sb.append(".panel{background:rgba(255,255,255,0.84);border:1px solid rgba(84,63,42,0.18);border-radius:24px;box-shadow:0 14px 40px rgba(86,63,41,0.12);backdrop-filter:blur(10px);}");
    sb.append(".hero-copy{padding:28px 30px;}");
    sb.append(".eyebrow{display:inline-block;padding:6px 12px;border-radius:999px;background:#1f4b45;color:#f7f3ea;font-size:11px;letter-spacing:.14em;text-transform:uppercase;}");
    sb.append("h1{margin:18px 0 10px;font-size:46px;line-height:1;font-weight:700;}");
    sb.append(".lede{margin:0;color:#4c5a66;font-size:18px;line-height:1.6;max-width:44rem;}");
    sb.append(".hero-notes{padding:24px;display:grid;gap:12px;background:#15322f;color:#f7f3ea;}");
    sb.append(".hero-notes h2{margin:0;font-size:20px;}");
    sb.append(".hero-notes p{margin:0;color:rgba(247,243,234,.82);line-height:1.6;}");
    sb.append(".grid{display:grid;grid-template-columns:1.2fr .8fr;gap:20px;margin-top:22px;}");
    sb.append(".section{padding:24px 26px;}");
    sb.append(".section h2{margin:0 0 12px;font-size:24px;}");
    sb.append(".section p{margin:0 0 16px;color:#52606d;line-height:1.6;}");
    sb.append(".status{margin:18px 0;padding:12px 14px;border-radius:14px;background:#efe3d0;color:#5c3b12;font-size:14px;}");
    sb.append(".card-stack{display:grid;gap:14px;}");
    sb.append(".robot-card{padding:18px;border-radius:18px;background:#f9f6ef;border:1px solid rgba(84,63,42,.12);}");
    sb.append(".robot-card h3{margin:0 0 8px;font-size:20px;}");
    sb.append(".robot-meta{font-size:13px;color:#6b7280;margin-bottom:12px;}");
    sb.append(".row{display:grid;grid-template-columns:1fr auto;gap:12px;align-items:end;}");
    sb.append(".actions{display:flex;gap:10px;flex-wrap:wrap;margin-top:12px;}");
    sb.append("label{display:block;font-size:12px;letter-spacing:.08em;text-transform:uppercase;color:#6b7280;margin-bottom:6px;}");
    sb.append("input,select,textarea{width:100%;padding:12px 14px;border-radius:14px;border:1px solid rgba(84,63,42,.18);background:#fffdf8;font:inherit;color:#14212b;}");
    sb.append("textarea{min-height:140px;resize:vertical;font-family:'SFMono-Regular',Consolas,'Liberation Mono',Menlo,monospace;font-size:13px;}");
    sb.append("button{appearance:none;border:none;border-radius:999px;padding:12px 18px;font:inherit;font-weight:700;cursor:pointer;}");
    sb.append(".primary{background:#15322f;color:#f7f3ea;}");
    sb.append(".secondary{background:#efe3d0;color:#5c3b12;}");
    sb.append(".danger{background:#7f1d1d;color:#fff4f1;}");
    sb.append(".empty{padding:24px;border-radius:18px;border:1px dashed rgba(84,63,42,.26);background:rgba(255,252,245,.7);color:#5b6670;}");
    sb.append(".token-box{margin-top:16px;display:none;}");
    sb.append(".token-box.visible{display:block;}");
    sb.append(".token-meta{margin-top:8px;font-size:12px;color:#6b7280;}");
    sb.append(".rotated{margin-top:14px;padding:14px;border-radius:16px;background:#173f38;color:#f7f3ea;}");
    sb.append(".rotated strong{display:block;margin-bottom:8px;font-size:12px;letter-spacing:.08em;text-transform:uppercase;color:rgba(247,243,234,.7);}");
    sb.append(".top-link{display:inline-flex;align-items:center;gap:8px;text-decoration:none;color:#173f38;font-weight:700;}");
    sb.append("@media (max-width:900px){.hero,.grid,.row{grid-template-columns:1fr;}h1{font-size:36px;}}");
    sb.append("</style></head><body><div class=\"shell\">");
    sb.append("<a class=\"top-link\" href=\"/\">&larr; Back to SupaWave</a>");
    sb.append("<div class=\"hero\">");
    sb.append("<section class=\"panel hero-copy\"><span class=\"eyebrow\">Robot Control Room</span>");
    sb.append("<h1>Robot and Data API management in one place.</h1>");
    sb.append("<p class=\"lede\">Review the robots registered to ").append(HtmlRenderer.escapeHtml(userAddress));
    sb.append(", update callback URLs without rotating secrets, and issue fresh human Data API tokens when you need to replace a compromised credential.</p>");
    if (!Strings.isNullOrEmpty(message)) {
      sb.append("<div class=\"status\">").append(HtmlRenderer.escapeHtml(message)).append("</div>");
    }
    sb.append("</section>");
    sb.append("<aside class=\"panel hero-notes\"><h2>What this lane manages</h2>");
    sb.append("<p>Robot callback URLs stay editable after registration.</p>");
    sb.append("<p>Secret rotation is isolated as a separate high-risk action.</p>");
    sb.append("<p>Human Data API tokens can be generated inline without leaving the dashboard.</p>");
    sb.append("</aside></div>");
    sb.append("<div class=\"grid\">");
    sb.append("<section class=\"panel section\"><h2>Registered robots</h2><p>Each robot card exposes a callback URL editor and a dedicated secret-rotation action.</p><div class=\"card-stack\">");
    if (robots.isEmpty()) {
      sb.append("<div class=\"empty\">No robots are registered for this account yet.</div>");
    } else {
      for (RobotAccountData robot : robots) {
        sb.append("<article class=\"robot-card\"><h3>").append(HtmlRenderer.escapeHtml(robot.getId().getAddress())).append("</h3>");
        sb.append("<div class=\"robot-meta\">Current callback URL: ").append(HtmlRenderer.escapeHtml(robot.getUrl())).append("</div>");
        sb.append("<form method=\"post\"><input type=\"hidden\" name=\"action\" value=\"update-url\"><input type=\"hidden\" name=\"token\" value=\"")
            .append(HtmlRenderer.escapeHtml(xsrfToken)).append("\">");
        sb.append("<input type=\"hidden\" name=\"robotId\" value=\"").append(HtmlRenderer.escapeHtml(robot.getId().getAddress())).append("\">");
        sb.append("<label>Callback URL</label><div class=\"row\"><input type=\"text\" name=\"location\" value=\"")
            .append(HtmlRenderer.escapeHtml(robot.getUrl())).append("\"><button class=\"secondary\" type=\"submit\">Update URL</button></div></form>");
        sb.append("<form method=\"post\" class=\"actions\"><input type=\"hidden\" name=\"action\" value=\"rotate-secret\"><input type=\"hidden\" name=\"token\" value=\"")
            .append(HtmlRenderer.escapeHtml(xsrfToken)).append("\">");
        sb.append("<input type=\"hidden\" name=\"robotId\" value=\"").append(HtmlRenderer.escapeHtml(robot.getId().getAddress())).append("\">");
        sb.append("<button class=\"danger\" type=\"submit\">Rotate Secret</button></form></article>");
      }
    }
    sb.append("</div>");
    if (!Strings.isNullOrEmpty(rotatedSecret)) {
      sb.append("<div class=\"rotated\"><strong>Latest secret</strong>")
          .append(HtmlRenderer.escapeHtml(rotatedSecret)).append("</div>");
    }
    sb.append("</section>");
    sb.append("<section class=\"panel section\"><h2>Register a robot</h2><p>Create a robot account and decide whether its tokens should expire automatically.</p>");
    sb.append("<form method=\"post\"><input type=\"hidden\" name=\"action\" value=\"register\"><input type=\"hidden\" name=\"token\" value=\"")
        .append(HtmlRenderer.escapeHtml(xsrfToken)).append("\">");
    sb.append("<label>Robot username</label><input type=\"text\" name=\"username\" placeholder=\"robot\">");
    sb.append("<label>Callback URL</label><input type=\"text\" name=\"location\" placeholder=\"https://robot.example.com/callback\">");
    sb.append("<label>Token expiry</label><select name=\"token_expiry\"><option value=\"0\">No expiry</option><option value=\"3600\">1 hour</option><option value=\"86400\">1 day</option><option value=\"604800\">1 week</option></select>");
    sb.append("<div class=\"actions\"><button class=\"primary\" type=\"submit\">Register robot</button></div></form>");
    sb.append("<div class=\"section\" style=\"padding:24px 0 0;\"><h2 style=\"font-size:22px;\">Generate Data API Token</h2><p>Generate a fresh human Data API JWT for this account without leaving the dashboard.</p>");
    sb.append("<div class=\"actions\"><button class=\"primary\" type=\"button\" onclick=\"generateDataApiToken()\">Generate Data API Token</button><button class=\"secondary\" type=\"button\" onclick=\"copyToken()\">Copy Token</button></div>");
    sb.append("<div id=\"tokenBox\" class=\"token-box\"><label>Current access token</label><textarea id=\"tokenText\" readonly></textarea><div class=\"token-meta\" id=\"tokenMeta\"></div></div></div>");
    sb.append("</section></div></div>");
    sb.append("<script>");
    sb.append("window.robotDashboardXsrf='").append(HtmlRenderer.escapeHtml(xsrfToken)).append("';");
    sb.append("function generateDataApiToken(){fetch('/robot/dataapi/token',{method:'POST',credentials:'same-origin',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:'expiry=3600&token='+encodeURIComponent(window.robotDashboardXsrf)}).then(function(r){return r.json();}).then(function(data){var box=document.getElementById('tokenBox');document.getElementById('tokenText').value=data.access_token||'';document.getElementById('tokenMeta').textContent=data.expires_in?'Expires in '+data.expires_in+' seconds':'Token generation failed';box.className='token-box visible';});}");
    sb.append("function copyToken(){var tokenText=document.getElementById('tokenText');if(!tokenText.value){return;}tokenText.select();tokenText.setSelectionRange(0,tokenText.value.length);if(navigator.clipboard&&navigator.clipboard.writeText){navigator.clipboard.writeText(tokenText.value);}}");
    sb.append("</script></body></html>");
    return sb.toString();
  }
}
