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
      case DELIVERY_CONFIRMED -> sendDeliveryConfirmedMessages(event);
      case DELIVERY_CONFIRMATION_DEADLINE_EXPIRED -> sendDeliveryConfirmationExpiredMessages(event);
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
        "%s recorded simulated shipment for '%s' with %s tracking %s. Confirm delivery by %s."
            .formatted(
                event.sellerHandle(),
                event.itemTitle(),
                event.carrier(),
                event.trackingReference(),
                event.deliveryConfirmationDeadlineAt()));
    send(
        event.sellerEmail(),
        "Shipment recorded",
        "Your simulated shipment for '%s' has been recorded. The buyer may confirm delivery until %s; after that the sale completes automatically."
            .formatted(event.itemTitle(), event.deliveryConfirmationDeadlineAt()));
  }

  private void sendPaymentExpiredMessages(SaleTransitionEvent event) throws MessagingException {
    send(
        event.buyerEmail(),
        "Payment deadline expired",
        "%s The unchanged approved item is now eligible for relisting."
            .formatted(terminalSummary(event)));
    send(
        event.sellerEmail(),
        "Payment deadline expired for your sale",
        "%s The unchanged approved item is now eligible for relisting."
            .formatted(terminalSummary(event)));
  }

  private void sendShipmentExpiredMessages(SaleTransitionEvent event) throws MessagingException {
    send(
        event.sellerEmail(),
        "Shipment deadline expired",
        "%s The unchanged approved item is now eligible for relisting."
            .formatted(terminalSummary(event)));
    send(
        event.buyerEmail(),
        "Shipment deadline expired for your auction win",
        "%s The unchanged approved item is now eligible for relisting."
            .formatted(terminalSummary(event)));
  }

  private void sendDeliveryConfirmedMessages(SaleTransitionEvent event) throws MessagingException {
    send(
        event.buyerEmail(),
        "Delivery confirmed — settlement completed",
        "%s The collectible is archived and cannot be relisted.".formatted(terminalSummary(event)));
    send(
        event.sellerEmail(),
        "Delivery confirmed — settlement completed",
        "%s The collectible is archived and cannot be relisted.".formatted(terminalSummary(event)));
  }

  private void sendDeliveryConfirmationExpiredMessages(SaleTransitionEvent event)
      throws MessagingException {
    send(
        event.buyerEmail(),
        "Delivery confirmation deadline reached — settlement completed",
        "%s The collectible is archived and cannot be relisted.".formatted(terminalSummary(event)));
    send(
        event.sellerEmail(),
        "Delivery confirmation deadline reached — settlement completed",
        "%s The collectible is archived and cannot be relisted.".formatted(terminalSummary(event)));
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

  private String terminalSummary(SaleTransitionEvent event) {
    return "Settlement for '%s' finished with %s; item disposition: %s."
        .formatted(
            event.itemTitle(),
            terminalReason(event.terminalReason()),
            itemDisposition(event.itemDisposition()));
  }

  private String terminalReason(String reason) {
    return switch (reason) {
      case "PAYMENT_DEADLINE_EXPIRED" -> "the payment deadline expired";
      case "SHIPMENT_DEADLINE_EXPIRED" -> "the shipment deadline expired";
      case "BUYER_CONFIRMED_DELIVERY" -> "the buyer confirmed delivery";
      case "DELIVERY_CONFIRMATION_DEADLINE_EXPIRED" -> "the delivery confirmation deadline expired";
      default -> "an unavailable terminal reason";
    };
  }

  private String itemDisposition(String disposition) {
    return switch (disposition) {
      case "RELISTING_ELIGIBLE" -> "eligible for relisting";
      case "ARCHIVED" -> "archived";
      default -> "unavailable";
    };
  }
}
