package org.waveprotocol.box.server.authentication.email;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import junit.framework.TestCase;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.waveprotocol.box.server.account.HumanAccountDataImpl;
import org.waveprotocol.box.server.authentication.PasswordDigest;
import org.waveprotocol.box.server.authentication.jwt.EmailTokenIssuer;
import org.waveprotocol.box.server.mail.MailProvider;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.wave.model.wave.ParticipantId;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import jakarta.servlet.http.HttpServletRequest;

public class AuthEmailServiceTest extends TestCase {
  private static final ParticipantId USER = ParticipantId.ofUnsafe("frodo@example.com");

  @Mock private AccountStore accountStore;
  @Mock private EmailTokenIssuer emailTokenIssuer;
  @Mock private MailProvider mailProvider;
  @Mock private HttpServletRequest req;

  @Override
  protected void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    when(req.getScheme()).thenReturn("https");
    when(req.getServerName()).thenReturn("wave.example.com");
    when(req.getServerPort()).thenReturn(443);
    when(req.getRemoteAddr()).thenReturn("198.51.100.11");
  }

  public void testConfirmationEmailsAreThrottledPerRecipient() throws Exception {
    Config config = ConfigFactory.parseMap(ImmutableMap.<String, Object>builder()
        .put("core.auth_email_send_cooldown_seconds", 300)
        .put("core.auth_email_send_max_per_address_per_hour", 5)
        .put("core.auth_email_send_max_per_ip_per_hour", 20)
        .build());
    AuthEmailService service = new AuthEmailService(
        accountStore,
        emailTokenIssuer,
        mailProvider,
        Clock.fixed(Instant.parse("2026-03-28T08:00:00Z"), ZoneOffset.UTC),
        config);

    HumanAccountDataImpl account =
        new HumanAccountDataImpl(USER, new PasswordDigest("password".toCharArray()));
    account.setEmail("frodo@example.com");
    when(emailTokenIssuer.issueEmailConfirmToken(USER)).thenReturn("confirm-token");

    AuthEmailService.DispatchResult first = service.sendConfirmationEmail(req, account);
    AuthEmailService.DispatchResult second = service.sendConfirmationEmail(req, account);

    assertEquals(AuthEmailService.DispatchResult.SENT, first);
    assertEquals(AuthEmailService.DispatchResult.THROTTLED, second);
    verify(mailProvider).sendEmail(eq("frodo@example.com"),
        eq("Confirm your Wave account"), contains("confirm-token"));
  }
}
