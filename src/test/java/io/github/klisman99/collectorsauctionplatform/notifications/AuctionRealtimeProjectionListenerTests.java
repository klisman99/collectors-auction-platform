package io.github.klisman99.collectorsauctionplatform.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.klisman99.collectorsauctionplatform.auctions.AuctionLifecycleEvent;
import io.github.klisman99.collectorsauctionplatform.bidding.BidAccepted;
import io.github.klisman99.collectorsauctionplatform.bidding.BidDisqualified;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

class AuctionRealtimeProjectionListenerTests {

  private static final UUID AUCTION_ID = UUID.fromString("55d176df-5230-49f1-b950-45c039f92f2e");
  private static final UUID BIDDER_ID = UUID.fromString("a3764d8c-138f-4ad5-abba-df14b38f49a7");
  private static final Instant OCCURRED_AT = Instant.parse("2026-09-17T19:01:00Z");

  private final CapturingMessageChannel channel = new CapturingMessageChannel();
  private final SimpMessagingTemplate messaging = new SimpMessagingTemplate(channel);
  private final AuctionRealtimeProjectionListener listener =
      new AuctionRealtimeProjectionListener(messaging);

  @Test
  void projectsAnAcceptedBidUsingOnlyItsAuctionLocalPublicIdentity() {
    listener.projectAcceptedBid(
        new BidAccepted(
            AUCTION_ID,
            BIDDER_ID,
            12_000,
            3,
            7,
            "Bidder-A1B2C3D4",
            OCCURRED_AT,
            12_000,
            13_000,
            true,
            Instant.parse("2026-09-17T20:03:00Z")));

    AuctionRealtimeEvent event = capturedEvent();
    assertThat(event)
        .extracting(
            AuctionRealtimeEvent::type,
            AuctionRealtimeEvent::auctionId,
            AuctionRealtimeEvent::projectionVersion,
            AuctionRealtimeEvent::bidSequence,
            AuctionRealtimeEvent::amountCents,
            AuctionRealtimeEvent::bidderPseudonym,
            AuctionRealtimeEvent::occurredAt)
        .containsExactly(
            "BID_ACCEPTED", AUCTION_ID, 7L, 3L, 12_000L, "Bidder-A1B2C3D4", OCCURRED_AT);
    assertThat(event.toString()).doesNotContain(BIDDER_ID.toString());
    assertThat(event.reserveMet()).isTrue();
    assertThat(event.effectiveEndAt()).isEqualTo(Instant.parse("2026-09-17T20:03:00Z"));
  }

  @Test
  void projectsDisqualificationAndAuctionOutcomeVersions() {
    listener.projectDisqualifiedBid(
        new BidDisqualified(
            UUID.randomUUID(),
            AUCTION_ID,
            BIDDER_ID,
            3,
            8,
            UUID.randomUUID(),
            "POLICY_VIOLATION",
            "Account suspended.",
            "private note",
            OCCURRED_AT,
            10_000,
            11_000,
            false,
            Instant.parse("2026-09-17T20:03:00Z")));

    AuctionRealtimeEvent disqualified = capturedEvent();
    assertThat(disqualified.type()).isEqualTo("BID_DISQUALIFIED");
    assertThat(disqualified.projectionVersion()).isEqualTo(8);
    assertThat(disqualified.toString()).doesNotContain("private note");

    listener.projectLifecycle(lifecycle(AuctionLifecycleEvent.Type.SOLD, 9));

    AuctionRealtimeEvent sold = capturedEvent();
    assertThat(sold.type()).isEqualTo("AUCTION_SOLD");
    assertThat(sold.projectionVersion()).isEqualTo(9);
  }

  @Test
  void brokerFailurePropagatesForDurableListenerRetryAfterTheDomainCommit() {
    BidAccepted accepted =
        new BidAccepted(
            AUCTION_ID,
            BIDDER_ID,
            12_000,
            3,
            7,
            "Bidder-A1B2C3D4",
            OCCURRED_AT,
            12_000,
            13_000,
            true,
            Instant.parse("2026-09-17T20:03:00Z"));
    channel.failure = new IllegalStateException("broker unavailable");

    assertThatThrownBy(() -> listener.projectAcceptedBid(accepted))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("broker unavailable");
  }

  private AuctionRealtimeEvent capturedEvent() {
    Message<?> message = channel.message;
    assertThat(SimpMessageHeaderAccessor.getDestination(message.getHeaders()))
        .isEqualTo("/topic/auctions/" + AUCTION_ID);
    channel.message = null;
    return (AuctionRealtimeEvent) message.getPayload();
  }

  private AuctionLifecycleEvent lifecycle(AuctionLifecycleEvent.Type type, long version) {
    return new AuctionLifecycleEvent(
        AUCTION_ID,
        UUID.randomUUID(),
        UUID.randomUUID(),
        "seller@example.com",
        "Collectible",
        type,
        version,
        null,
        null,
        null,
        null,
        null,
        OCCURRED_AT,
        null,
        null,
        12_000L,
        BIDDER_ID,
        "winner",
        "winner@example.com");
  }

  private static final class CapturingMessageChannel implements MessageChannel {

    private Message<?> message;
    private RuntimeException failure;

    @Override
    public boolean send(Message<?> message) {
      if (failure != null) {
        throw failure;
      }
      this.message = message;
      return true;
    }

    @Override
    public boolean send(Message<?> message, long timeout) {
      return send(message);
    }
  }
}
