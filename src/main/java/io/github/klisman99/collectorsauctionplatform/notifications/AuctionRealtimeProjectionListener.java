package io.github.klisman99.collectorsauctionplatform.notifications;

import io.github.klisman99.collectorsauctionplatform.auctions.AuctionLifecycleEvent;
import io.github.klisman99.collectorsauctionplatform.bidding.BidAccepted;
import io.github.klisman99.collectorsauctionplatform.bidding.BidDisqualified;
import java.util.Locale;
import java.util.UUID;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
class AuctionRealtimeProjectionListener {

  private final SimpMessagingTemplate messaging;

  AuctionRealtimeProjectionListener(SimpMessagingTemplate messaging) {
    this.messaging = messaging;
  }

  @ApplicationModuleListener
  void projectAcceptedBid(BidAccepted event) {
    send(
        event.auctionId(),
        new AuctionRealtimeEvent(
            event.auctionId(),
            "BID_ACCEPTED",
            event.projectionVersion(),
            event.sequence(),
            event.amountCents(),
            event.bidderPseudonym(),
            event.acceptedAt(),
            event.currentAmountCents(),
            event.nextMinimumAmountCents(),
            event.reserveMet(),
            event.effectiveEndAt()));
  }

  @ApplicationModuleListener
  void projectDisqualifiedBid(BidDisqualified event) {
    send(
        event.auctionId(),
        new AuctionRealtimeEvent(
            event.auctionId(),
            "BID_DISQUALIFIED",
            event.projectionVersion(),
            event.bidSequence(),
            null,
            null,
            event.disqualifiedAt(),
            event.currentAmountCents(),
            event.nextMinimumAmountCents(),
            event.reserveMet(),
            event.effectiveEndAt()));
  }

  @ApplicationModuleListener
  void projectLifecycle(AuctionLifecycleEvent event) {
    send(
        event.auctionId(),
        new AuctionRealtimeEvent(
            event.auctionId(),
            publicLifecycleType(event.type()),
            event.projectionVersion(),
            null,
            event.finalAmountCents(),
            null,
            event.occurredAt(),
            null,
            null,
            null,
            event.currentTerms() == null ? null : event.currentTerms().endsAt()));
  }

  private String publicLifecycleType(AuctionLifecycleEvent.Type type) {
    if (type == AuctionLifecycleEvent.Type.ADMINISTRATIVELY_CANCELLED) {
      return "AUCTION_CANCELLED";
    }
    return "AUCTION_" + type.name().toUpperCase(Locale.ROOT);
  }

  private void send(UUID auctionId, AuctionRealtimeEvent event) {
    messaging.convertAndSend("/topic/auctions/" + auctionId, event);
  }
}
