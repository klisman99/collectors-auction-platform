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
    if (event.type() == AuctionLifecycleEvent.Type.SOLD
        || event.type() == AuctionLifecycleEvent.Type.SELLER_DECISION_ACCEPTED) {
      sendSoldEmails(event);
      return;
    }
    if (event.type() == AuctionLifecycleEvent.Type.UNSOLD
        || event.type() == AuctionLifecycleEvent.Type.SELLER_DECISION_NO_ELIGIBLE_BID) {
      send(
          event.sellerEmail(),
          "Your auction ended unsold",
          "Your auction for '%s' ended without an eligible bid.".formatted(event.itemTitle()));
      return;
    }
    if (event.type() == AuctionLifecycleEvent.Type.AWAITING_SELLER_DECISION
        || event.type() == AuctionLifecycleEvent.Type.SELLER_DECISION_REOPENED) {
      sendBelowReserveDecisionEmails(event);
      return;
    }
    if (event.type() == AuctionLifecycleEvent.Type.SELLER_DECISION_REJECTED
        || event.type() == AuctionLifecycleEvent.Type.SELLER_DECISION_EXPIRED) {
      send(
          event.winningBidderEmail(),
          "Your final offer was not accepted",
          "Your offer of R$ %s for '%s' was not accepted."
              .formatted(amount(event.finalAmountCents()), event.itemTitle()));
      return;
    }
    if (event.type() != AuctionLifecycleEvent.Type.STARTED
        && event.type() != AuctionLifecycleEvent.Type.CANCELLED
        && event.type() != AuctionLifecycleEvent.Type.ADMINISTRATIVELY_CANCELLED) {
      return;
    }
    sendLifecycleMessage(event);
  }

  private void sendLifecycleMessage(AuctionLifecycleEvent event) throws MessagingException {
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

  private void sendSoldEmails(AuctionLifecycleEvent event) throws MessagingException {
    send(
        event.sellerEmail(),
        "Your auction sold",
        "Your auction for '%s' sold to %s for R$ %s."
            .formatted(
                event.itemTitle(), event.winningBidderHandle(), amount(event.finalAmountCents())));
    send(
        event.winningBidderEmail(),
        "You won an auction",
        "You won '%s' for R$ %s.".formatted(event.itemTitle(), amount(event.finalAmountCents())));
  }

  private void sendBelowReserveDecisionEmails(AuctionLifecycleEvent event)
      throws MessagingException {
    send(
        event.sellerEmail(),
        "Reserve decision required",
        "Your auction for '%s' has an eligible final offer of R$ %s. Decide by %s."
            .formatted(
                event.itemTitle(),
                amount(event.finalAmountCents()),
                event.sellerDecisionDeadlineAt()));
    send(
        event.winningBidderEmail(),
        "Your final offer is awaiting a seller decision",
        "Your offer of R$ %s for '%s' is awaiting the seller's reserve decision."
            .formatted(amount(event.finalAmountCents()), event.itemTitle()));
  }

  private void send(String recipient, String subject, String body) throws MessagingException {
    MimeMessage message = mailSender.createMimeMessage();
    MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
    helper.setFrom(properties.from());
    helper.setTo(recipient);
    helper.setSubject(subject);
    helper.setText(body);
    mailSender.send(message);
  }

  private String amount(Long amountCents) {
    return String.format(java.util.Locale.ROOT, "%.2f", amountCents / 100.0);
  }
}
