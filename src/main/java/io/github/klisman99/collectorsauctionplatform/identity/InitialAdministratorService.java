package io.github.klisman99.collectorsauctionplatform.identity;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class InitialAdministratorService {

  private final IdentityMutationGuardRepository mutationGuards;
  private final OperationalAccountRepository operationalAccounts;
  private final RegularAccountRepository regularAccounts;
  private final PasswordEncoder passwordEncoder;
  private final PasswordPolicy passwordPolicy;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  InitialAdministratorService(
      IdentityMutationGuardRepository mutationGuards,
      OperationalAccountRepository operationalAccounts,
      RegularAccountRepository regularAccounts,
      PasswordEncoder passwordEncoder,
      PasswordPolicy passwordPolicy,
      ApplicationEventPublisher events,
      Clock clock) {
    this.mutationGuards = mutationGuards;
    this.operationalAccounts = operationalAccounts;
    this.regularAccounts = regularAccounts;
    this.passwordEncoder = passwordEncoder;
    this.passwordPolicy = passwordPolicy;
    this.events = events;
    this.clock = clock;
  }

  @Transactional
  void ensureConfiguredAdministrator(String email, String password) {
    boolean emailConfigured = email != null && !email.isBlank();
    boolean passwordConfigured = password != null && !password.isBlank();
    if (!emailConfigured && !passwordConfigured) {
      if (operationalAccounts.countByRoleAndStatus(
              OperationalRole.ADMINISTRATOR, OperationalStatus.ACTIVE)
          == 0) {
        throw new IllegalStateException(
            "An active administrator is required before bootstrap credentials can be left unset.");
      }
      return;
    }
    if (!emailConfigured || !passwordConfigured) {
      throw new IllegalStateException(
          "Both platform.identity.initial-administrator.email and password must be configured together.");
    }

    passwordPolicy.validate(password);
    String normalizedEmail = IdentityNormalization.email(email);
    mutationGuards
        .findForUpdate()
        .orElseThrow(
            () -> new IllegalStateException("The identity mutation guard row is missing."));

    if (regularAccounts.existsByNormalizedEmail(normalizedEmail)) {
      throw new IllegalStateException(
          "The configured initial administrator email belongs to a regular account.");
    }
    java.util.Optional<OperationalAccount> configuredAccount =
        operationalAccounts.findByNormalizedEmailForUpdate(normalizedEmail);
    if (configuredAccount.isPresent()) {
      if (!configuredAccount.get().isActiveAdministrator()) {
        throw new IllegalStateException(
            "The configured initial administrator email must belong to an active administrator.");
      }
      return;
    }

    Instant now = Instant.now(clock);
    OperationalAccount account =
        OperationalAccount.initialAdministrator(
            UUID.randomUUID(), normalizedEmail, passwordEncoder.encode(password), now);
    operationalAccounts.saveAndFlush(account);
    events.publishEvent(
        new InitialAdministratorCreated(account.id(), account.normalizedEmail(), now));
  }
}
