package io.github.klisman99.collectorsauctionplatform.auctions;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

class AuctionApiException extends ErrorResponseException {

  private AuctionApiException(HttpStatus status, String code, String ruleId, String detail) {
    super(status, problem(status, code, ruleId, detail), null);
  }

  static AuctionApiException invalidTerms(String ruleId, String detail) {
    return new AuctionApiException(HttpStatus.BAD_REQUEST, "AUCTION_TERMS_INVALID", ruleId, detail);
  }

  static AuctionApiException accountIneligible() {
    return new AuctionApiException(
        HttpStatus.FORBIDDEN,
        "AUCTION_SCHEDULING_FORBIDDEN",
        "BR-AUTH-004",
        "Only a verified active owner can schedule an auction.");
  }

  static AuctionApiException itemNotFound() {
    return new AuctionApiException(
        HttpStatus.NOT_FOUND,
        "ITEM_NOT_FOUND",
        "BR-ITEM-014",
        "The collectible item was not found.");
  }

  static AuctionApiException itemNotApproved() {
    return new AuctionApiException(
        HttpStatus.CONFLICT,
        "ITEM_NOT_AUCTION_ELIGIBLE",
        "BR-ITEM-014",
        "Only an approved item can be scheduled.");
  }

  static AuctionApiException itemAlreadyScheduled() {
    return new AuctionApiException(
        HttpStatus.CONFLICT,
        "ITEM_ALREADY_HAS_ACTIVE_AUCTION",
        "BR-ITEM-015",
        "The item already belongs to a non-terminal auction.");
  }

  static AuctionApiException notFound() {
    return new AuctionApiException(
        HttpStatus.NOT_FOUND, "AUCTION_NOT_FOUND", null, "The auction was not found.");
  }

  static AuctionApiException notEditable() {
    return new AuctionApiException(
        HttpStatus.CONFLICT,
        "AUCTION_TERMS_NOT_EDITABLE",
        "BR-AUC-009",
        "Auction terms can be changed only by the seller before start.");
  }

  static AuctionApiException reserveCannotIncrease() {
    return new AuctionApiException(
        HttpStatus.BAD_REQUEST,
        "AUCTION_RESERVE_INCREASE_FORBIDDEN",
        "BR-AUC-009",
        "Reserve may be reduced or removed before start, but never increased.");
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
