package io.github.klisman99.collectorsauctionplatform.catalog;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

class CatalogApiException extends ErrorResponseException {

  private CatalogApiException(HttpStatus status, String code, String ruleId, String detail) {
    super(status, problem(status, code, ruleId, detail), null);
  }

  static CatalogApiException invalid(String detail) {
    return new CatalogApiException(
        HttpStatus.BAD_REQUEST, "ITEM_VALIDATION_FAILED", "BR-ITEM-004", detail);
  }

  static CatalogApiException forbidden() {
    return new CatalogApiException(
        HttpStatus.FORBIDDEN,
        "CATALOG_MUTATION_FORBIDDEN",
        "BR-AUTH-009",
        "The authenticated account cannot change collectible drafts.");
  }

  static CatalogApiException otherCategory(String detail) {
    return new CatalogApiException(
        HttpStatus.BAD_REQUEST, "ITEM_CATEGORY_INVALID", "BR-ITEM-003", detail);
  }

  static CatalogApiException ownershipNotDeclared(String detail) {
    return new CatalogApiException(
        HttpStatus.BAD_REQUEST, "OWNERSHIP_DECLARATION_REQUIRED", "BR-ITEM-004", detail);
  }

  static CatalogApiException notFound() {
    return new CatalogApiException(
        HttpStatus.NOT_FOUND, "ITEM_NOT_FOUND", null, "The collectible draft was not found.");
  }

  static CatalogApiException invalidImage(String detail) {
    return new CatalogApiException(HttpStatus.BAD_REQUEST, "IMAGE_INVALID", "BR-ITEM-007", detail);
  }

  static CatalogApiException invalidImageCount(String detail) {
    return new CatalogApiException(
        HttpStatus.BAD_REQUEST, "IMAGE_COUNT_INVALID", "BR-ITEM-006", detail);
  }

  static CatalogApiException invalidImageOrder(String detail) {
    return new CatalogApiException(
        HttpStatus.BAD_REQUEST, "IMAGE_ORDER_INVALID", "BR-ITEM-006", detail);
  }

  static CatalogApiException cannotSubmit(String detail) {
    return new CatalogApiException(
        HttpStatus.CONFLICT, "ITEM_SUBMISSION_INVALID", "BR-ITEM-010", detail);
  }

  static CatalogApiException auctionLocked() {
    return new CatalogApiException(
        HttpStatus.CONFLICT,
        "ITEM_LOCKED_BY_AUCTION",
        "BR-ITEM-013",
        "An item attached to a non-terminal auction cannot be changed.");
  }

  static CatalogApiException reviewConflict() {
    return new CatalogApiException(
        HttpStatus.CONFLICT,
        "ITEM_REVIEW_CONFLICT",
        "BR-MOD-004",
        "The moderation decision was already persisted by another reviewer.");
  }

  static CatalogApiException reviewReasonRequired() {
    return new CatalogApiException(
        HttpStatus.BAD_REQUEST,
        "ITEM_REJECTION_REASON_REQUIRED",
        "BR-ITEM-011",
        "Rejection requires a public reason.");
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
