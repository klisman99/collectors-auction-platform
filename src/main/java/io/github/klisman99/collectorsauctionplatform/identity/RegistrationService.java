package io.github.klisman99.collectorsauctionplatform.identity;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class RegistrationService {

  private static final Duration VERIFICATION_TOKEN_LIFETIME = Duration.ofHours(24);

  private final RegularAccountRepository accounts;
  private final EmailVerificationTokenRepository verificationTokens;
  private final PasswordEncoder passwordEncoder;
  private final PasswordPolicy passwordPolicy;
  private final VerificationTokenGenerator tokenGenerator;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  RegistrationService(
      RegularAccountRepository accounts,
      EmailVerificationTokenRepository verificationTokens,
      PasswordEncoder passwordEncoder,
      PasswordPolicy passwordPolicy,
      VerificationTokenGenerator tokenGenerator,
      ApplicationEventPublisher events,
      Clock clock) {
    this.accounts = accounts;
    this.verificationTokens = verificationTokens;
    this.passwordEncoder = passwordEncoder;
    this.passwordPolicy = passwordPolicy;
    this.tokenGenerator = tokenGenerator;
    this.events = events;
    this.clock = clock;
  }

  @Transactional
  RegistrationResult register(String email, String handle, String password) {
    passwordPolicy.validate(password);

    String normalizedEmail = IdentityNormalization.email(email);
    String publicHandle = IdentityNormalization.publicHandle(handle);
    assertIdentifiersAvailable(normalizedEmail, publicHandle);

    Instant now = Instant.now(clock);
    RegularAccount account =
        RegularAccount.register(
            UUID.randomUUID(),
            normalizedEmail,
            publicHandle,
            passwordEncoder.encode(password),
            now);
    String verificationToken = tokenGenerator.generate();
    EmailVerificationToken token =
        new EmailVerificationToken(
            UUID.randomUUID(),
            account.id(),
            tokenGenerator.digest(verificationToken),
            now.plus(VERIFICATION_TOKEN_LIFETIME));

    try {
      accounts.saveAndFlush(account);
      verificationTokens.saveAndFlush(token);
    } catch (DataIntegrityViolationException exception) {
      throw resolveRegistrationConflict(exception);
    }

    events.publishEvent(
        new RegularAccountRegistered(
            account.id(),
            account.normalizedEmail(),
            account.publicHandle(),
            verificationToken,
            now));
    return new RegistrationResult(account.publicHandle());
  }

  @Transactional
  VerificationResult verify(String rawToken) {
    Instant now = Instant.now(clock);
    EmailVerificationToken token =
        verificationTokens
            .findByTokenDigest(tokenGenerator.digest(rawToken))
            .filter(candidate -> candidate.isUsableAt(now))
            .orElseThrow(IdentityApiException::invalidVerificationToken);
    RegularAccount account =
        accounts
            .findById(token.accountId())
            .orElseThrow(IdentityApiException::invalidVerificationToken);

    account.verify(now);
    token.markUsed(now);
    events.publishEvent(new RegularAccountVerified(account.id(), now));
    return new VerificationResult(account.publicHandle());
  }

  private void assertIdentifiersAvailable(String normalizedEmail, String publicHandle) {
    if (accounts.existsByNormalizedEmail(normalizedEmail)) {
      throw IdentityApiException.emailAlreadyRegistered();
    }
    if (accounts.existsByPublicHandle(publicHandle)) {
      throw IdentityApiException.publicHandleUnavailable();
    }
  }

  record RegistrationResult(String publicHandle) {}

  record VerificationResult(String publicHandle) {}

  private IdentityApiException resolveRegistrationConflict(
      DataIntegrityViolationException exception) {
    String message = exception.getMessage();
    if (message != null && message.contains("regular_accounts_normalized_email_unique")) {
      return IdentityApiException.emailAlreadyRegistered();
    }
    return IdentityApiException.publicHandleUnavailable();
  }
}
