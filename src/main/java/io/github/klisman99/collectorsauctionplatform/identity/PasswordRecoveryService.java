package io.github.klisman99.collectorsauctionplatform.identity;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class PasswordRecoveryService {

    private static final Duration TOKEN_LIFETIME = Duration.ofHours(1);

    private final RegularAccountRepository accounts;
    private final PasswordRecoveryTokenRepository recoveryTokens;
    private final PasswordEncoder passwordEncoder;
    private final VerificationTokenGenerator tokenGenerator;
    private final AccountSessionRevocationService sessions;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    PasswordRecoveryService(
            RegularAccountRepository accounts,
            PasswordRecoveryTokenRepository recoveryTokens,
            PasswordEncoder passwordEncoder,
            VerificationTokenGenerator tokenGenerator,
            AccountSessionRevocationService sessions,
            ApplicationEventPublisher events,
            Clock clock) {
        this.accounts = accounts;
        this.recoveryTokens = recoveryTokens;
        this.passwordEncoder = passwordEncoder;
        this.tokenGenerator = tokenGenerator;
        this.sessions = sessions;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    void request(String email) {
        String normalizedEmail = IdentityNormalization.email(email);
        accounts.findByNormalizedEmail(normalizedEmail).ifPresent(account -> {
            Instant now = Instant.now(clock);
            invalidateExistingTokens(account.id(), now);

            String recoveryToken = tokenGenerator.generate();
            recoveryTokens.saveAndFlush(new PasswordRecoveryToken(
                    UUID.randomUUID(),
                    account.id(),
                    tokenGenerator.digest(recoveryToken),
                    now.plus(TOKEN_LIFETIME)));
            events.publishEvent(new PasswordRecoveryRequested(
                    account.id(),
                    account.normalizedEmail(),
                    account.publicHandle(),
                    recoveryToken,
                    now));
        });
    }

    @Transactional
    void reset(String rawToken, String password) {
        validatePasswordLength(password);
        Instant now = Instant.now(clock);
        PasswordRecoveryToken token = recoveryTokens.findByTokenDigest(tokenGenerator.digest(rawToken))
                .filter(candidate -> candidate.isUsableAt(now))
                .orElseThrow(IdentityApiException::invalidPasswordRecoveryToken);
        RegularAccount account = accounts.findById(token.accountId())
                .orElseThrow(IdentityApiException::invalidPasswordRecoveryToken);

        account.changePassword(passwordEncoder.encode(password));
        token.markUsed(now);
        invalidateExistingTokens(account.id(), now);
        sessions.revokeAll(account.id());
        events.publishEvent(new RegularAccountPasswordReset(account.id(), now));
    }

    private void invalidateExistingTokens(UUID accountId, Instant now) {
        recoveryTokens.findAllByAccountIdAndUsedAtIsNull(accountId)
                .forEach(token -> token.markUsed(now));
    }

    private void validatePasswordLength(String password) {
        int characterCount = password.codePointCount(0, password.length());
        if (characterCount < 12 || characterCount > 128) {
            throw IdentityApiException.invalidPasswordLength();
        }
    }
}
