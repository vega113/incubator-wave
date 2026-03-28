package org.waveprotocol.box.server.robots;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import junit.framework.TestCase;

import org.waveprotocol.box.server.account.AccountData;
import org.waveprotocol.box.server.account.RobotAccountData;
import org.waveprotocol.box.server.account.RobotAccountDataImpl;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.authentication.WebSession;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.box.server.robots.register.RobotRegistrar;
import org.waveprotocol.wave.model.wave.ParticipantId;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

public class RobotDashboardServletTest extends TestCase {
  private static final ParticipantId OWNER = ParticipantId.ofUnsafe("owner@example.com");
  private static final ParticipantId OTHER_OWNER = ParticipantId.ofUnsafe("other@example.com");
  private static final ParticipantId ROBOT = ParticipantId.ofUnsafe("robot@example.com");

  private SessionManager sessionManager;
  private AccountStore accountStore;
  private RobotRegistrar robotRegistrar;
  private HttpServletRequest req;
  private HttpServletResponse resp;
  private StringWriter outputWriter;
  private RobotDashboardServlet servlet;

  @Override
  protected void setUp() throws Exception {
    sessionManager = mock(SessionManager.class);
    accountStore = mock(AccountStore.class);
    robotRegistrar = mock(RobotRegistrar.class);

    req = mock(HttpServletRequest.class);
    resp = mock(HttpServletResponse.class);
    HttpSession session = mock(HttpSession.class);
    outputWriter = new StringWriter();
    when(resp.getWriter()).thenReturn(new PrintWriter(outputWriter));
    when(req.getRequestURI()).thenReturn("/account/robots");
    when(req.getSession(false)).thenReturn(session);

    servlet = new RobotDashboardServlet("example.com", sessionManager, accountStore, robotRegistrar);
  }

  public void testDoGetRedirectsWhenLoggedOut() throws Exception {
    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(null);

    servlet.doGet(req, resp);

    verify(resp).sendRedirect("/auth/signin?r=/account/robots");
  }

  public void testDoGetRendersOwnedRobots() throws Exception {
    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(OWNER);
    when(accountStore.getRobotAccountsOwnedBy(OWNER.getAddress())).thenReturn(List.of(
        new RobotAccountDataImpl(ROBOT, "https://robot.example.com/callback", "secret", null,
            true, 3600L, OWNER.getAddress())));

    servlet.doGet(req, resp);

    assertTrue(outputWriter.toString().contains("robot@example.com"));
    assertTrue(outputWriter.toString().contains("https://robot.example.com/callback"));
    assertTrue(outputWriter.toString().contains("Generate Data API Token"));
  }

  public void testDoPostRejectsRobotMutationFromDifferentOwner() throws Exception {
    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(OWNER);
    when(accountStore.getRobotAccountsOwnedBy(OWNER.getAddress())).thenReturn(List.of());
    servlet.doGet(req, resp);
    outputWriter.getBuffer().setLength(0);
    when(req.getParameter("action")).thenReturn("rotate-secret");
    when(req.getParameter("token")).thenReturn("dashboard-xsrf");
    when(req.getParameter("robotId")).thenReturn(ROBOT.getAddress());
    when(accountStore.getAccount(ROBOT)).thenReturn(new RobotAccountDataImpl(ROBOT,
        "https://robot.example.com/callback", "secret", null, true, 0L,
        OTHER_OWNER.getAddress()));

    servlet.doPost(req, resp);

    verify(resp).setStatus(HttpServletResponse.SC_FORBIDDEN);
    assertTrue(outputWriter.toString().contains("You do not own this robot"));
  }

  public void testDoPostRejectsInvalidXsrfToken() throws Exception {
    RobotAccountData existingRobot = new RobotAccountDataImpl(ROBOT,
        "https://robot.example.com/callback", "secret", null, true, 0L,
        OWNER.getAddress());

    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(OWNER);
    when(accountStore.getRobotAccountsOwnedBy(OWNER.getAddress())).thenReturn(List.of(existingRobot));
    servlet.doGet(req, resp);
    outputWriter.getBuffer().setLength(0);
    when(req.getParameter("action")).thenReturn("rotate-secret");
    when(req.getParameter("token")).thenReturn("wrong-token");
    when(req.getParameter("robotId")).thenReturn(ROBOT.getAddress());

    servlet.doPost(req, resp);

    verify(resp).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    assertTrue(outputWriter.toString().contains("Invalid XSRF token"));
  }

  public void testDoPostRotatesSecretForOwnedRobot() throws Exception {
    RobotAccountData existingRobot = new RobotAccountDataImpl(ROBOT,
        "https://robot.example.com/callback", "secret", null, true, 0L,
        OWNER.getAddress());
    RobotAccountData rotatedRobot = new RobotAccountDataImpl(ROBOT,
        "https://robot.example.com/callback", "new-secret", null, true, 0L,
        OWNER.getAddress());

    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(OWNER);
    when(accountStore.getRobotAccountsOwnedBy(OWNER.getAddress())).thenReturn(List.of(existingRobot));
    servlet.doGet(req, resp);
    outputWriter.getBuffer().setLength(0);
    when(req.getParameter("action")).thenReturn("rotate-secret");
    when(req.getParameter("token")).thenReturn("dashboard-xsrf");
    when(req.getParameter("robotId")).thenReturn(ROBOT.getAddress());
    when(accountStore.getAccount(ROBOT)).thenReturn((AccountData) existingRobot);
    when(robotRegistrar.rotateSecret(ROBOT)).thenReturn(rotatedRobot);

    servlet.doPost(req, resp);

    verify(robotRegistrar).rotateSecret(ROBOT);
    assertTrue(outputWriter.toString().contains("new-secret"));
  }

  public void testDoPostRegistersRobotForCurrentOwner() throws Exception {
    RobotAccountData registeredRobot = new RobotAccountDataImpl(ROBOT,
        "https://robot.example.com/callback", "new-secret", null, true, 3600L,
        OWNER.getAddress());

    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(OWNER);
    when(accountStore.getRobotAccountsOwnedBy(OWNER.getAddress())).thenReturn(List.of());
    servlet.doGet(req, resp);
    outputWriter.getBuffer().setLength(0);
    when(req.getParameter("action")).thenReturn("register");
    when(req.getParameter("token")).thenReturn("dashboard-xsrf");
    when(req.getParameter("username")).thenReturn("robot");
    when(req.getParameter("location")).thenReturn("https://robot.example.com/callback");
    when(req.getParameter("token_expiry")).thenReturn("3600");
    when(robotRegistrar.registerNew(eq(ROBOT), anyString(), eq(OWNER.getAddress()), eq(3600L)))
        .thenReturn(registeredRobot);

    servlet.doPost(req, resp);

    verify(robotRegistrar).registerNew(ROBOT, "https://robot.example.com/callback",
        OWNER.getAddress(), 3600L);
    assertTrue(outputWriter.toString().contains("robot@example.com"));
    assertTrue(outputWriter.toString().contains("new-secret"));
  }
}
