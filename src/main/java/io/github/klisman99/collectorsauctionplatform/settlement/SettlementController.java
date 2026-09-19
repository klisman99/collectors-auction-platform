package io.github.klisman99.collectorsauctionplatform.settlement;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/sales", produces = MediaType.APPLICATION_JSON_VALUE)
class SettlementController {

  private final SaleService sales;

  SettlementController(SaleService sales) {
    this.sales = sales;
  }

  @GetMapping("/mine")
  @Operation(
      operationId = "listMySales",
      summary = "List settlement sales for the signed-in participant")
  List<SaleResponse> mine(Principal principal) {
    UUID participantId = UUID.fromString(principal.getName());
    return sales.listForParticipant(participantId).stream()
        .map(sale -> SaleResponse.from(sale, participantId))
        .toList();
  }

  @PostMapping("/{id}/payment")
  @Operation(operationId = "simulateSalePayment", summary = "Simulate the buyer's payment")
  SaleResponse payment(Principal principal, @PathVariable UUID id) {
    UUID buyerId = UUID.fromString(principal.getName());
    return SaleResponse.from(sales.recordPayment(buyerId, id), buyerId);
  }

  @PostMapping(path = "/{id}/shipment", consumes = MediaType.APPLICATION_JSON_VALUE)
  @Operation(operationId = "recordSaleShipment", summary = "Record simulated shipment tracking")
  SaleResponse shipment(
      Principal principal, @PathVariable UUID id, @Valid @RequestBody ShipmentRequest request) {
    UUID sellerId = UUID.fromString(principal.getName());
    return SaleResponse.from(
        sales.recordShipment(sellerId, id, request.carrier(), request.trackingReference()),
        sellerId);
  }

  @PostMapping("/{id}/delivery-confirmation")
  @Operation(operationId = "confirmSaleDelivery", summary = "Confirm delivery of a shipped sale")
  SaleResponse confirmDelivery(Principal principal, @PathVariable UUID id) {
    UUID buyerId = UUID.fromString(principal.getName());
    return SaleResponse.from(sales.confirmDelivery(buyerId, id), buyerId);
  }

  record ShipmentRequest(
      @NotBlank @Size(max = 120) String carrier,
      @NotBlank @Size(max = 160) String trackingReference) {}

  record SaleResponse(
      UUID id,
      UUID auctionId,
      ItemResponse item,
      ParticipantResponse buyer,
      ParticipantResponse seller,
      String participantRole,
      long amountCents,
      String state,
      Instant createdAt,
      Instant paymentDeadlineAt,
      Instant shipmentDeadlineAt,
      Instant paidAt,
      Instant shippedAt,
      Instant deliveryConfirmationDeadlineAt,
      Instant completedAt,
      Instant failedAt,
      String terminalReason,
      String itemDisposition,
      String carrier,
      String trackingReference) {

    static SaleResponse from(Sale sale, UUID participantId) {
      return new SaleResponse(
          sale.id(),
          sale.auctionId(),
          new ItemResponse(sale.itemId(), sale.itemTitle()),
          new ParticipantResponse(sale.buyerHandle()),
          new ParticipantResponse(sale.sellerHandle()),
          sale.isBuyer(participantId) ? "BUYER" : "SELLER",
          sale.amountCents(),
          sale.state().name(),
          sale.createdAt(),
          sale.paymentDeadlineAt(),
          sale.shipmentDeadlineAt(),
          sale.paidAt(),
          sale.shippedAt(),
          sale.deliveryConfirmationDeadlineAt(),
          sale.completedAt(),
          sale.failedAt(),
          sale.terminalReason() == null ? null : sale.terminalReason().name(),
          sale.itemDisposition() == null ? null : sale.itemDisposition().name(),
          sale.carrier(),
          sale.trackingReference());
    }
  }

  record ItemResponse(UUID id, String title) {}

  record ParticipantResponse(String handle) {}
}
