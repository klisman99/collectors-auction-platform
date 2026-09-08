package io.github.klisman99.collectorsauctionplatform.identity;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

class IdentityApiException extends ErrorResponseException {

  private IdentityApiException(HttpStatus status, String code, String ruleId, String detail) {
    super(status, problem(status, code, ruleId, detail), null);
  }

  static IdentityApiException emailAlreadyRegistered() {
    return new IdentityApiException(
        HttpStatus.CONFLICT,
        "EMAIL_ALREADY_REGISTERED",
        "BR-AUTH-001",
        "An account already uses that email address.");
  }

  static IdentityApiException publicHandleUnavailable() {
    return new IdentityApiException(
        HttpStatus.CONFLICT,
        "PUBLIC_HANDLE_UNAVAILABLE",
        "BR-AUTH-002",
        "That public handle is unavailable.");
  }

  static IdentityApiException invalidPasswordLength() {
    return new IdentityApiException(
        HttpStatus.BAD_REQUEST,
        "PASSWORD_LENGTH_INVALID",
        "BR-AUTH-003",
        "Password must contain between 12 and 128 characters.");
  }

  static IdentityApiException invalidVerificationToken() {
    return new IdentityApiException(
        HttpStatus.BAD_REQUEST,
        "VERIFICATION_TOKEN_INVALID",
        "BR-AUTH-005",
        "The verification token is invalid, expired, or already used.");
  }

  static IdentityApiException invalidPasswordRecoveryToken() {
    return new IdentityApiException(
        HttpStatus.BAD_REQUEST,
        "PASSWORD_RECOVERY_TOKEN_INVALID",
        "BR-AUTH-005",
        "The password recovery token is invalid, expired, or already used.");
  }

  static IdentityApiException invalidCredentials() {
    return new IdentityApiException(
        HttpStatus.UNAUTHORIZED,
        "INVALID_CREDENTIALS",
        null,
        "The email address or password is incorrect.");
  }

  static IdentityApiException loginRateLimited() {
    return new IdentityApiException(
        HttpStatus.TOO_MANY_REQUESTS,
        "LOGIN_RATE_LIMIT_EXCEEDED",
        "BR-AUTH-015",
        "Too many sign-in attempts. Try again in one minute.");
  }

  static IdentityApiException passwordRecoveryRateLimited() {
    return new IdentityApiException(
        HttpStatus.TOO_MANY_REQUESTS,
        "PASSWORD_RECOVERY_RATE_LIMIT_EXCEEDED",
        "BR-AUTH-015",
        "Too many password recovery requests. Try again in one hour.");
  }

  static IdentityApiException operationalEmailAlreadyRegistered() {
    return new IdentityApiException(
        HttpStatus.CONFLICT,
        "OPERATIONAL_EMAIL_ALREADY_REGISTERED",
        "BR-AUTH-011",
        "An operational or regular account already uses that email address.");
  }

  static IdentityApiException invalidOperationalRole() {
    return new IdentityApiException(
        HttpStatus.BAD_REQUEST,
        "OPERATIONAL_ROLE_INVALID",
        "BR-AUTH-011",
        "Operational account role must be MODERATOR or ADMINISTRATOR.");
  }

  static IdentityApiException invalidAdministrativeReason() {
    return new IdentityApiException(
        HttpStatus.BAD_REQUEST,
        "ADMINISTRATIVE_REASON_INVALID",
        "BR-AUDIT-005",
        "A categorized public reason is required for this administrative action.");
  }

  static IdentityApiException invalidOperationalActivationToken() {
    return new IdentityApiException(
        HttpStatus.BAD_REQUEST,
        "OPERATIONAL_ACTIVATION_TOKEN_INVALID",
        "BR-AUTH-011",
        "The operational activation token is invalid, expired, or already used.");
  }

  static IdentityApiException operationalAccountNotActive() {
    return new IdentityApiException(
        HttpStatus.CONFLICT,
        "OPERATIONAL_ACCOUNT_NOT_ACTIVE",
        "BR-AUTH-012",
        "The operational account is not active.");
  }

  static IdentityApiException operationalAccountNotFound() {
    return new IdentityApiException(
        HttpStatus.NOT_FOUND,
        "OPERATIONAL_ACCOUNT_NOT_FOUND",
        "BR-AUTH-013",
        "The operational account does not exist.");
  }

  static IdentityApiException lastAdministrator() {
    return new IdentityApiException(
        HttpStatus.CONFLICT,
        "LAST_ADMINISTRATOR_PROTECTED",
        "BR-AUTH-014",
        "The platform must retain at least one active administrator.");
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
