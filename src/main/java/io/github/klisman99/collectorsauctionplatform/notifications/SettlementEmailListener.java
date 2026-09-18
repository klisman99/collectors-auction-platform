package io.github.klisman99.collectorsauctionplatform.notifications;

import io.github.klisman99.collectorsauctionplatform.settlement.SaleTransitionEvent;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
class SettlementEmailListener {

  private final JavaMailSender mailSender;
  private final MailProperties properties;

  SettlementEmailListener(JavaMailSender mailSender, MailProperties properties) {
    this.mailSender = mailSender;
    this.properties = properties;
  }

  @ApplicationModuleListener
  void sendSettlementEmail(SaleTransitionEvent event) throws MessagingException {
    switch (event.type()) {
      case CREATED -> sendCreatedMessages(event);
      case PAYMENT_RECORDED -> sendPaymentRecordedMessages(event);
      case SHIPMENT_RECORDED -> sendShipmentRecordedMessages(event);
      case PAYMENT_EXPIRED -> sendPaymentExpiredMessages(event);
      case SHIPMENT_EXPIRED -> sendShipmentExpiredMessages(event);
    }
  }

  private void sendCreatedMessages(SaleTransitionEvent event) throws MessagingException {
    send(
        event.buyerEmail(),
        "Payment required for your auction win",
        "You won '%s' for R$ %s. Simulate payment by %s."
            .formatted(event.itemTitle(), amount(event.amountCents()), event.paymentDeadlineAt()));
    send(
        event.sellerEmail(),
        "Sale created — awaiting payment",
        "Your sale of '%s' to %s is awaiting simulated payment by %s."
            .formatted(event.itemTitle(), event.buyerHandle(), event.paymentDeadlineAt()));
  }

  private void sendPaymentRecordedMessages(SaleTransitionEvent event) throws MessagingException {
    send(
        event.buyerEmail(),
        "Payment recorded for your auction win",
        "Your simulated payment for '%s' was recorded. %s will record shipment next."
            .formatted(event.itemTitle(), event.sellerHandle()));
    send(
        event.sellerEmail(),
        "Payment recorded — shipment required",
        "Simulated payment for '%s' is recorded. Record the carrier and tracking reference by %s."
            .formatted(event.itemTitle(), event.shipmentDeadlineAt()));
  }

  private void sendShipmentRecordedMessages(SaleTransitionEvent event) throws MessagingException {
    send(
        event.buyerEmail(),
        "Shipment recorded for your auction win",
        "%s recorded simulated shipment for '%s' with %s tracking %s."
            .formatted(
                event.sellerHandle(),
                event.itemTitle(),
                event.carrier(),
                event.trackingReference()));
    send(
        event.sellerEmail(),
        "Shipment recorded",
        "Your simulated shipment for '%s' has been recorded.".formatted(event.itemTitle()));
  }

  private void sendPaymentExpiredMessages(SaleTransitionEvent event) throws MessagingException {
    send(
        event.buyerEmail(),
        "Payment deadline expired",
        "The simulated payment deadline for '%s' expired before payment was recorded."
            .formatted(event.itemTitle()));
    send(
        event.sellerEmail(),
        "Payment deadline expired for your sale",
        "The simulated payment deadline for '%s' expired before payment was recorded."
            .formatted(event.itemTitle()));
  }

  private void sendShipmentExpiredMessages(SaleTransitionEvent event) throws MessagingException {
    send(
        event.sellerEmail(),
        "Shipment deadline expired",
        "The simulated shipment deadline for '%s' expired before shipment was recorded."
            .formatted(event.itemTitle()));
    send(
        event.buyerEmail(),
        "Shipment deadline expired for your auction win",
        "The simulated shipment deadline for '%s' expired before shipment was recorded."
            .formatted(event.itemTitle()));
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

  private String amount(long amountCents) {
    return String.format(Locale.ROOT, "%.2f", amountCents / 100.0);
  }
}
