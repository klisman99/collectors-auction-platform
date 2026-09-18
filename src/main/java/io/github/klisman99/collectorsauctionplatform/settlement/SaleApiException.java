package io.github.klisman99.collectorsauctionplatform.settlement;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

final class SaleApiException extends ErrorResponseException {

  private SaleApiException(HttpStatus status, String code, String ruleId, String detail) {
    super(status, problem(status, code, ruleId, detail), null);
  }

  static SaleApiException notFound() {
    return new SaleApiException(
        HttpStatus.NOT_FOUND, "SALE_NOT_FOUND", null, "The sale was not found.");
  }

  static SaleApiException paymentForbidden() {
    return new SaleApiException(
        HttpStatus.FORBIDDEN,
        "PAYMENT_FORBIDDEN",
        "BR-SALE-003",
        "Only the buyer may simulate payment for this sale.");
  }

  static SaleApiException shipmentForbidden() {
    return new SaleApiException(
        HttpStatus.FORBIDDEN,
        "SHIPMENT_FORBIDDEN",
        "BR-SALE-004",
        "Only the seller may record shipment for this sale.");
  }

  static SaleApiException paymentUnavailable() {
    return new SaleApiException(
        HttpStatus.CONFLICT,
        "PAYMENT_UNAVAILABLE",
        "BR-SALE-003",
        "Payment can no longer be simulated for this sale.");
  }

  static SaleApiException shipmentUnavailable() {
    return new SaleApiException(
        HttpStatus.CONFLICT,
        "SHIPMENT_UNAVAILABLE",
        "BR-SALE-004",
        "Shipment can no longer be recorded for this sale.");
  }

  private static ProblemDetail problem(
      HttpStatus status, String code, String ruleId, String detail) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setType(
        URI.create("urn:collectors-auction-platform:problem:" + code.toLowerCase(Locale.ROOT)));
    problem.setProperty("code", code);
    problem.setProperty("ruleId", ruleId);
    problem.setProperty("fieldErrors", List.of());
    return problem;
  }
}
