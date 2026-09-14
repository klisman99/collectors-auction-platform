package io.github.klisman99.collectorsauctionplatform.bidding;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

final class BiddingApiException extends ErrorResponseException {

  private BiddingApiException(
      HttpStatus status, String code, String ruleId, String detail, BidCommandResult result) {
    super(status, problem(status, code, ruleId, detail, result), null);
  }

  static BiddingApiException from(BidCommandResult result) {
    return switch (result.status()) {
      case RATE_LIMITED ->
          new BiddingApiException(
              HttpStatus.TOO_MANY_REQUESTS,
              result.code(),
              "BR-AUTH-015",
              "Bidding is limited to ten commands per second for this auction.",
              result);
      case IDEMPOTENCY_CONFLICT ->
          new BiddingApiException(
              HttpStatus.CONFLICT,
              result.code(),
              "BR-BID-007",
              "The idempotency key was already used with a different amount.",
              result);
      case REJECTED -> rejected(result);
      default -> throw new IllegalArgumentException("The bid result is not an API failure.");
    };
  }

  private static BiddingApiException rejected(BidCommandResult result) {
    return switch (result.code()) {
      case "BID_AMOUNT_TOO_LOW" ->
          new BiddingApiException(
              HttpStatus.UNPROCESSABLE_ENTITY,
              result.code(),
              "BR-BID-003",
              "The bid does not meet the required amount.",
              result);
      case "BIDDER_INELIGIBLE" ->
          new BiddingApiException(
              HttpStatus.FORBIDDEN,
              result.code(),
              "BR-BID-005",
              "The account is not eligible to bid on this auction.",
              result);
      case "BIDDER_UNVERIFIED" ->
          new BiddingApiException(
              HttpStatus.FORBIDDEN,
              result.code(),
              "BR-AUTH-004",
              "Email verification is required before placing a bid.",
              result);
      case "BIDDER_INACTIVE" ->
          new BiddingApiException(
              HttpStatus.FORBIDDEN,
              result.code(),
              "BR-AUTH-009",
              "A suspended account cannot place bids.",
              result);
      case "BID_AUCTION_NOT_FOUND" ->
          new BiddingApiException(
              HttpStatus.NOT_FOUND,
              result.code(),
              "BR-BID-001",
              "The auction does not exist.",
              result);
      default ->
          new BiddingApiException(
              HttpStatus.CONFLICT,
              result.code(),
              "BR-BID-001",
              "The auction is not accepting bids.",
              result);
    };
  }

  private static ProblemDetail problem(
      HttpStatus status, String code, String ruleId, String detail, BidCommandResult result) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setType(
        URI.create("urn:collectors-auction-platform:problem:" + code.toLowerCase(Locale.ROOT)));
    problem.setProperty("code", code);
    problem.setProperty("ruleId", ruleId);
    problem.setProperty("fieldErrors", List.of());
    problem.setProperty("requiredAmountCents", result.requiredAmountCents());
    return problem;
  }
}
