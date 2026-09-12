package io.github.klisman99.collectorsauctionplatform.notifications;

import io.github.klisman99.collectorsauctionplatform.auctions.AuctionLifecycleEvent;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
class AuctionLifecycleEmailListener {

  private final JavaMailSender mailSender;
  private final MailProperties properties;

  AuctionLifecycleEmailListener(JavaMailSender mailSender, MailProperties properties) {
    this.mailSender = mailSender;
    this.properties = properties;
  }

  @ApplicationModuleListener
  void sendLifecycleEmail(AuctionLifecycleEvent event) throws MessagingException {
    if (event.type() != AuctionLifecycleEvent.Type.STARTED
        && event.type() != AuctionLifecycleEvent.Type.CANCELLED
        && event.type() != AuctionLifecycleEvent.Type.ADMINISTRATIVELY_CANCELLED) {
      return;
    }
    MimeMessage message = mailSender.createMimeMessage();
    MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
    helper.setFrom(properties.from());
    helper.setTo(event.sellerEmail());
    if (event.type() == AuctionLifecycleEvent.Type.STARTED) {
      helper.setSubject("Your auction is live");
      helper.setText("Your auction for '%s' is now live.".formatted(event.itemTitle()));
    } else {
      helper.setSubject("Your auction was cancelled");
      helper.setText(
          "Your auction for '%s' was cancelled. Public reason: %s"
              .formatted(event.itemTitle(), event.publicReason()));
    }
    mailSender.send(message);
  }
}
