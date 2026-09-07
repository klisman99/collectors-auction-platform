package io.github.klisman99.collectorsauctionplatform.notifications;

import io.github.klisman99.collectorsauctionplatform.identity.PasswordRecoveryRequested;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
class PasswordRecoveryEmailListener {

  private final JavaMailSender mailSender;
  private final MailProperties properties;

  PasswordRecoveryEmailListener(JavaMailSender mailSender, MailProperties properties) {
    this.mailSender = mailSender;
    this.properties = properties;
  }

  @ApplicationModuleListener
  void sendPasswordRecoveryEmail(PasswordRecoveryRequested event) throws MessagingException {
    MimeMessage message = mailSender.createMimeMessage();
    MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
    helper.setFrom(properties.from());
    helper.setTo(event.email());
    helper.setSubject("Reset your Collectors Auction Platform password");
    helper.setText(
        """
                Hello %s.

                Reset your password with this link:
                %s

                This link expires in one hour and can only be used once. If you did not request a password reset,
                you can safely ignore this email.
                """
            .formatted(event.publicHandle(), recoveryUrl(event.recoveryToken())));
    mailSender.send(message);
  }

  private String recoveryUrl(String token) {
    String encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8);
    return properties.webBaseUrl().resolve("/?recoveryToken=" + encodedToken).toString();
  }
}
