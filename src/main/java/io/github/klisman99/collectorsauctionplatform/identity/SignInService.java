package io.github.klisman99.collectorsauctionplatform.identity;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authenticates an account while holding its row lock until the caller has persisted the
 * replacement server-side session.
 */
@Service
class SignInService {

  private final RegularAccountRepository regularAccounts;
  private final OperationalAccountRepository operationalAccounts;
  private final AuthenticationManager authenticationManager;

  SignInService(
      RegularAccountRepository regularAccounts,
      OperationalAccountRepository operationalAccounts,
      AuthenticationManager authenticationManager) {
    this.regularAccounts = regularAccounts;
    this.operationalAccounts = operationalAccounts;
    this.authenticationManager = authenticationManager;
  }

  @Transactional
  AccountSessionPrincipal signIn(
      String normalizedEmail, String password, SessionReplacement sessionReplacement) {

    regularAccounts.findByNormalizedEmailForUpdate(normalizedEmail);
    operationalAccounts.findByNormalizedEmailForUpdate(normalizedEmail);

    Authentication authenticated;
    try {
      authenticated =
          authenticationManager.authenticate(
              UsernamePasswordAuthenticationToken.unauthenticated(normalizedEmail, password));
    } catch (org.springframework.security.core.AuthenticationException exception) {
      throw IdentityApiException.invalidCredentials();
    }

    AccountCredentialsPrincipal credentials =
        (AccountCredentialsPrincipal) authenticated.getPrincipal();
    AccountSessionPrincipal sessionPrincipal = credentials.sessionPrincipal();
    sessionReplacement.replaceWith(sessionPrincipal);
    return sessionPrincipal;
  }

  @FunctionalInterface
  interface SessionReplacement {
    void replaceWith(AccountSessionPrincipal principal);
  }
}
