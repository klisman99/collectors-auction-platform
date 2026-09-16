package io.github.klisman99.collectorsauctionplatform.accountadministration;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

class AccountAdministrationApiException extends ErrorResponseException {

  private AccountAdministrationApiException(
      HttpStatus status, String code, String ruleId, String detail) {
    super(status, problem(status, code, ruleId, detail), null);
  }

  static AccountAdministrationApiException invalidSuspensionReason() {
    return new AccountAdministrationApiException(
        HttpStatus.BAD_REQUEST,
        "REGULAR_ACCOUNT_SUSPENSION_REASON_INVALID",
        "BR-AUDIT-005",
        "A categorized public reason is required for account suspension.");
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
