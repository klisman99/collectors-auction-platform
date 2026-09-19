package io.github.klisman99.collectorsauctionplatform.settlement;

import io.github.klisman99.collectorsauctionplatform.auctions.AuctionSold;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sales")
class Sale {

  @Id private UUID id;

  @Column(name = "auction_id", nullable = false, unique = true, updatable = false)
  private UUID auctionId;

  @Column(name = "item_id", nullable = false, updatable = false)
  private UUID itemId;

  @Column(name = "item_title", nullable = false, updatable = false)
  private String itemTitle;

  @Column(name = "seller_id", nullable = false, updatable = false)
  private UUID sellerId;

  @Column(name = "seller_handle", nullable = false, updatable = false)
  private String sellerHandle;

  @Column(name = "buyer_id", nullable = false, updatable = false)
  private UUID buyerId;

  @Column(name = "buyer_handle", nullable = false, updatable = false)
  private String buyerHandle;

  @Column(name = "amount_cents", nullable = false, updatable = false)
  private long amountCents;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  private State state;

  @Column(name = "payment_deadline_at", nullable = false, updatable = false)
  private Instant paymentDeadlineAt;

  @Column(name = "shipment_deadline_at")
  private Instant shipmentDeadlineAt;

  @Column(name = "paid_at")
  private Instant paidAt;

  @Column(name = "shipped_at")
  private Instant shippedAt;

  @Column(name = "delivery_confirmation_deadline_at")
  private Instant deliveryConfirmationDeadlineAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "failed_at")
  private Instant failedAt;

  @Column(name = "terminal_reason")
  @Enumerated(EnumType.STRING)
  private TerminalReason terminalReason;

  @Column(name = "item_disposition")
  @Enumerated(EnumType.STRING)
  private ItemDisposition itemDisposition;

  @Column(length = 120)
  private String carrier;

  @Column(name = "tracking_reference", length = 160)
  private String trackingReference;

  protected Sale() {}

  private Sale(AuctionSold auction, String sellerHandle, String buyerHandle) {
    this.id = UUID.randomUUID();
    this.auctionId = auction.auctionId();
    this.itemId = auction.itemId();
    this.itemTitle = auction.itemTitle();
    this.sellerId = auction.sellerId();
    this.sellerHandle = sellerHandle;
    this.buyerId = auction.buyerId();
    this.buyerHandle = buyerHandle;
    this.amountCents = auction.amountCents();
    this.createdAt = auction.occurredAt();
    this.state = State.PAYMENT_PENDING;
    this.paymentDeadlineAt = auction.occurredAt().plus(Duration.ofHours(24));
  }

  static Sale from(AuctionSold auction, String sellerHandle, String buyerHandle) {
    return new Sale(auction, sellerHandle, buyerHandle);
  }

  Transition payment(Instant now) {
    if (paidAt != null) {
      return Transition.IDEMPOTENT;
    }
    if (state != State.PAYMENT_PENDING) {
      if (state == State.FAILED && terminalReason == TerminalReason.PAYMENT_DEADLINE_EXPIRED) {
        return Transition.IDEMPOTENT;
      }
      return Transition.UNAVAILABLE;
    }
    if (!now.isBefore(paymentDeadlineAt)) {
      fail(now, TerminalReason.PAYMENT_DEADLINE_EXPIRED);
      return Transition.EXPIRED;
    }
    state = State.SHIPMENT_PENDING;
    paidAt = now;
    shipmentDeadlineAt = now.plus(Duration.ofDays(3));
    return Transition.APPLIED;
  }

  Transition shipment(String carrier, String trackingReference, Instant now) {
    if (shippedAt != null) {
      return this.carrier.equals(carrier) && this.trackingReference.equals(trackingReference)
          ? Transition.IDEMPOTENT
          : Transition.UNAVAILABLE;
    }
    if (state != State.SHIPMENT_PENDING) {
      if (state == State.FAILED && terminalReason == TerminalReason.SHIPMENT_DEADLINE_EXPIRED) {
        return Transition.IDEMPOTENT;
      }
      return Transition.UNAVAILABLE;
    }
    if (!now.isBefore(shipmentDeadlineAt)) {
      fail(now, TerminalReason.SHIPMENT_DEADLINE_EXPIRED);
      return Transition.EXPIRED;
    }
    this.carrier = carrier;
    this.trackingReference = trackingReference;
    this.shippedAt = now;
    this.deliveryConfirmationDeadlineAt = now.plus(Duration.ofDays(7));
    this.state = State.SHIPPED;
    return Transition.APPLIED;
  }

  Transition confirmDelivery(Instant now) {
    if (completedAt != null) {
      return Transition.IDEMPOTENT;
    }
    if (state != State.SHIPPED) {
      return Transition.UNAVAILABLE;
    }
    if (!now.isBefore(deliveryConfirmationDeadlineAt)) {
      complete(now, TerminalReason.DELIVERY_CONFIRMATION_DEADLINE_EXPIRED);
      return Transition.AUTO_COMPLETED;
    }
    complete(now, TerminalReason.BUYER_CONFIRMED_DELIVERY);
    return Transition.APPLIED;
  }

  boolean expirePaymentIfDue(Instant now) {
    if (state != State.PAYMENT_PENDING || now.isBefore(paymentDeadlineAt)) {
      return false;
    }
    fail(now, TerminalReason.PAYMENT_DEADLINE_EXPIRED);
    return true;
  }

  boolean expireShipmentIfDue(Instant now) {
    if (state != State.SHIPMENT_PENDING || now.isBefore(shipmentDeadlineAt)) {
      return false;
    }
    fail(now, TerminalReason.SHIPMENT_DEADLINE_EXPIRED);
    return true;
  }

  boolean completeDeliveryIfDue(Instant now) {
    if (state != State.SHIPPED || now.isBefore(deliveryConfirmationDeadlineAt)) {
      return false;
    }
    complete(now, TerminalReason.DELIVERY_CONFIRMATION_DEADLINE_EXPIRED);
    return true;
  }

  boolean isBuyer(UUID accountId) {
    return buyerId.equals(accountId);
  }

  boolean isSeller(UUID accountId) {
    return sellerId.equals(accountId);
  }

  UUID id() {
    return id;
  }

  UUID auctionId() {
    return auctionId;
  }

  UUID itemId() {
    return itemId;
  }

  String itemTitle() {
    return itemTitle;
  }

  UUID sellerId() {
    return sellerId;
  }

  String sellerHandle() {
    return sellerHandle;
  }

  UUID buyerId() {
    return buyerId;
  }

  String buyerHandle() {
    return buyerHandle;
  }

  long amountCents() {
    return amountCents;
  }

  Instant createdAt() {
    return createdAt;
  }

  State state() {
    return state;
  }

  Instant paymentDeadlineAt() {
    return paymentDeadlineAt;
  }

  Instant shipmentDeadlineAt() {
    return shipmentDeadlineAt;
  }

  Instant paidAt() {
    return paidAt;
  }

  Instant shippedAt() {
    return shippedAt;
  }

  Instant deliveryConfirmationDeadlineAt() {
    return deliveryConfirmationDeadlineAt;
  }

  Instant completedAt() {
    return completedAt;
  }

  Instant failedAt() {
    return failedAt;
  }

  TerminalReason terminalReason() {
    return terminalReason;
  }

  ItemDisposition itemDisposition() {
    return itemDisposition;
  }

  String carrier() {
    return carrier;
  }

  String trackingReference() {
    return trackingReference;
  }

  private void fail(Instant now, TerminalReason reason) {
    state = State.FAILED;
    failedAt = now;
    terminalReason = reason;
    itemDisposition = ItemDisposition.RELISTING_ELIGIBLE;
  }

  private void complete(Instant now, TerminalReason reason) {
    state = State.COMPLETED;
    completedAt = now;
    terminalReason = reason;
    itemDisposition = ItemDisposition.ARCHIVED;
  }

  enum State {
    PAYMENT_PENDING,
    SHIPMENT_PENDING,
    SHIPPED,
    COMPLETED,
    FAILED
  }

  enum Transition {
    APPLIED,
    IDEMPOTENT,
    EXPIRED,
    AUTO_COMPLETED,
    UNAVAILABLE
  }

  enum TerminalReason {
    PAYMENT_DEADLINE_EXPIRED,
    SHIPMENT_DEADLINE_EXPIRED,
    BUYER_CONFIRMED_DELIVERY,
    DELIVERY_CONFIRMATION_DEADLINE_EXPIRED
  }

  enum ItemDisposition {
    RELISTING_ELIGIBLE,
    ARCHIVED
  }
}
