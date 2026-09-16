package io.github.klisman99.collectorsauctionplatform.identity;

import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Deliberate identity interface for administrative regular-account lifecycle commands. */
@Service
public class RegularAccountAdministration {

  private final RegularAccountRepository accounts;
  private final AccountSessionRevocationService sessions;
  private final ApplicationEventPublisher events;

  RegularAccountAdministration(
      RegularAccountRepository accounts,
      AccountSessionRevocationService sessions,
      ApplicationEventPublisher events) {
    this.accounts = accounts;
    this.sessions = sessions;
    this.events = events;
  }

  @Transactional(readOnly = true)
  public List<RegularAccountView> list() {
    return accounts.findAllByOrderByRegisteredAtDesc().stream()
        .map(RegularAccountView::from)
        .toList();
  }

  @Transactional
  public RegularAccountView suspend(UUID accountId, RegularAccountSuspension suspension) {
    RegularAccount account =
        accounts
            .findByIdForUpdate(accountId)
            .orElseThrow(IdentityApiException::regularAccountNotFound);
    account.suspend();
    sessions.revokeAll(accountId);
    events.publishEvent(
        new RegularAccountSuspended(
            account.id(),
            suspension.actorId(),
            account.publicHandle(),
            suspension.reasonCategory().name(),
            suspension.publicReason(),
            suspension.internalNote(),
            suspension.occurredAt()));
    return RegularAccountView.from(account);
  }

  @Transactional
  public RegularAccountView reactivate(UUID accountId, RegularAccountReactivation reactivation) {
    RegularAccount account =
        accounts
            .findByIdForUpdate(accountId)
            .orElseThrow(IdentityApiException::regularAccountNotFound);
    account.reactivate();
    events.publishEvent(
        new RegularAccountReactivated(
            account.id(),
            reactivation.actorId(),
            account.publicHandle(),
            reactivation.reasonCategory().name(),
            reactivation.publicReason(),
            reactivation.internalNote(),
            reactivation.occurredAt()));
    return RegularAccountView.from(account);
  }

  public record RegularAccountView(
      UUID id, String email, String publicHandle, String status, boolean verified) {

    static RegularAccountView from(RegularAccount account) {
      return new RegularAccountView(
          account.id(),
          account.normalizedEmail(),
          account.publicHandle(),
          account.status().name(),
          account.isVerified());
    }
  }
}
