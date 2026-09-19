package io.github.klisman99.collectorsauctionplatform.audit;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

final class AuditApiException extends ErrorResponseException {

  private AuditApiException(HttpStatus status, String code, String ruleId, String detail) {
    super(status, problem(status, code, ruleId, detail), null);
  }

  static AuditApiException invalidHistoryPage() {
    return new AuditApiException(
        HttpStatus.BAD_REQUEST,
        "AUDIT_HISTORY_PAGE_INVALID",
        "BR-AUDIT-004",
        "Page must be zero or greater and size must be between 1 and 50.");
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
