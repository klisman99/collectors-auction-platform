package io.github.klisman99.collectorsauctionplatform.notifications;

import io.github.klisman99.collectorsauctionplatform.moderation.ModerationDecisionMade;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
class ModerationDecisionEmailListener {

  private final JavaMailSender mailSender;
  private final MailProperties properties;

  ModerationDecisionEmailListener(JavaMailSender mailSender, MailProperties properties) {
    this.mailSender = mailSender;
    this.properties = properties;
  }

  @ApplicationModuleListener
  void sendDecision(ModerationDecisionMade event) throws MessagingException {
    MimeMessage message = mailSender.createMimeMessage();
    MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
    helper.setFrom(properties.from());
    helper.setTo(event.ownerEmail());
    helper.setSubject("Your collectible moderation decision");
    helper.setText(
        event.decision() == ModerationDecisionMade.Decision.APPROVED
            ? "Your collectible '%s' has been approved and is eligible for auction scheduling."
                .formatted(event.title())
            : "Your collectible '%s' was returned to Draft. Reason: %s"
                .formatted(event.title(), event.publicReason()));
    mailSender.send(message);
  }
}
