package io.github.klisman99.collectorsauctionplatform.audit;

import io.github.klisman99.collectorsauctionplatform.catalog.CollectibleSubmitted;
import io.github.klisman99.collectorsauctionplatform.identity.InitialAdministratorCreated;
import io.github.klisman99.collectorsauctionplatform.identity.OperationalAccountActivated;
import io.github.klisman99.collectorsauctionplatform.identity.OperationalAccountDeactivated;
import io.github.klisman99.collectorsauctionplatform.identity.OperationalAccountInvited;
import io.github.klisman99.collectorsauctionplatform.identity.RegularAccountPasswordReset;
import io.github.klisman99.collectorsauctionplatform.identity.RegularAccountReactivated;
import io.github.klisman99.collectorsauctionplatform.identity.RegularAccountRegistered;
import io.github.klisman99.collectorsauctionplatform.identity.RegularAccountSuspended;
import io.github.klisman99.collectorsauctionplatform.identity.RegularAccountVerified;
import io.github.klisman99.collectorsauctionplatform.moderation.ModerationDecisionMade;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
class IdentityAuditListener {

  private final AuditProjectionStore auditRecords;

  IdentityAuditListener(AuditProjectionStore auditRecords) {
    this.auditRecords = auditRecords;
  }

  @ApplicationModuleListener
  void auditRegistration(RegularAccountRegistered event) {
    auditRecords.append(
        AuditRecord.systemAction(
                AuditRecord.AuditAction.REGULAR_ACCOUNT_REGISTERED,
                event.accountId(),
                event.occurredAt(),
                "publicHandle=" + event.publicHandle())
            .participates(event.accountId()));
  }

  @ApplicationModuleListener
  void auditVerification(RegularAccountVerified event) {
    auditRecords.append(
        AuditRecord.accountAction(
                AuditRecord.AuditAction.REGULAR_ACCOUNT_VERIFIED,
                event.accountId(),
                event.occurredAt(),
                "verification=completed")
            .participates(event.accountId()));
  }

  @ApplicationModuleListener
  void auditPasswordReset(RegularAccountPasswordReset event) {
    auditRecords.append(
        AuditRecord.accountAction(
                AuditRecord.AuditAction.REGULAR_ACCOUNT_PASSWORD_RESET,
                event.accountId(),
                event.occurredAt(),
                "password=reset")
            .participates(event.accountId()));
  }

  @ApplicationModuleListener
  void auditRegularAccountSuspension(RegularAccountSuspended event) {
    auditRecords.append(
        AuditRecord.operationalRegularAccountAction(
                AuditRecord.AuditAction.REGULAR_ACCOUNT_SUSPENDED,
                event.actorId(),
                event.accountId(),
                event.occurredAt(),
                administrativeMetadata(
                    "publicHandle=" + event.publicHandle(),
                    "reasonCategory=" + event.reasonCategory(),
                    "publicReason=" + event.publicReason(),
                    "internalNote=" + event.internalNote()))
            .participates(event.accountId())
            .withPublicDetails(
                event.reasonCategory(), event.publicReason(), null, null, event.internalNote()));
  }

  @ApplicationModuleListener
  void auditRegularAccountReactivation(RegularAccountReactivated event) {
    auditRecords.append(
        AuditRecord.operationalRegularAccountAction(
                AuditRecord.AuditAction.REGULAR_ACCOUNT_REACTIVATED,
                event.actorId(),
                event.accountId(),
                event.occurredAt(),
                administrativeMetadata(
                    "publicHandle=" + event.publicHandle(),
                    "reasonCategory=" + event.reasonCategory(),
                    "publicReason=" + event.publicReason(),
                    "internalNote=" + event.internalNote()))
            .participates(event.accountId())
            .withPublicDetails(
                event.reasonCategory(), event.publicReason(), null, null, event.internalNote()));
  }

  @ApplicationModuleListener
  void auditInitialAdministrator(InitialAdministratorCreated event) {
    auditRecords.append(
        AuditRecord.systemOperationalAction(
            AuditRecord.AuditAction.INITIAL_ADMINISTRATOR_CREATED,
            event.accountId(),
            event.occurredAt(),
            "email=" + event.email()));
  }

  @ApplicationModuleListener
  void auditOperationalInvitation(OperationalAccountInvited event) {
    auditRecords.append(
        AuditRecord.operationalAction(
                AuditRecord.AuditAction.OPERATIONAL_ACCOUNT_INVITED,
                event.actorId(),
                event.accountId(),
                event.occurredAt(),
                administrativeMetadata(
                    "email=" + event.email(),
                    "role=" + event.role(),
                    "reasonCategory=" + event.reasonCategory(),
                    "publicReason=" + event.publicReason(),
                    "internalNote=" + event.internalNote()))
            .withPublicDetails(
                event.reasonCategory(), event.publicReason(), null, null, event.internalNote()));
  }

  @ApplicationModuleListener
  void auditOperationalActivation(OperationalAccountActivated event) {
    auditRecords.append(
        AuditRecord.operationalAction(
            AuditRecord.AuditAction.OPERATIONAL_ACCOUNT_ACTIVATED,
            event.accountId(),
            event.accountId(),
            event.occurredAt(),
            "activation=completed"));
  }

  @ApplicationModuleListener
  void auditOperationalDeactivation(OperationalAccountDeactivated event) {
    auditRecords.append(
        AuditRecord.operationalAction(
                AuditRecord.AuditAction.OPERATIONAL_ACCOUNT_DEACTIVATED,
                event.actorId(),
                event.accountId(),
                event.occurredAt(),
                administrativeMetadata(
                    "role=" + event.role(),
                    "reasonCategory=" + event.reasonCategory(),
                    "publicReason=" + event.publicReason(),
                    "internalNote=" + event.internalNote()))
            .withPublicDetails(
                event.reasonCategory(), event.publicReason(), null, null, event.internalNote()));
  }

  @ApplicationModuleListener
  void auditCollectibleSubmission(CollectibleSubmitted event) {
    auditRecords.append(
        AuditRecord.accountCollectibleAction(
                AuditRecord.AuditAction.COLLECTIBLE_SUBMITTED,
                event.ownerId(),
                event.itemId(),
                event.occurredAt(),
                "title=" + event.title())
            .participates(event.ownerId())
            .forItem(event.itemId()));
  }

  @ApplicationModuleListener
  void auditModerationDecision(ModerationDecisionMade event) {
    AuditRecord.AuditAction action =
        event.decision() == ModerationDecisionMade.Decision.APPROVED
            ? AuditRecord.AuditAction.COLLECTIBLE_APPROVED
            : AuditRecord.AuditAction.COLLECTIBLE_REJECTED;
    auditRecords.append(
        AuditRecord.operationalCollectibleAction(
                action,
                event.reviewerId(),
                event.itemId(),
                event.occurredAt(),
                "publicReason=" + (event.publicReason() == null ? "" : event.publicReason()))
            .participates(event.ownerId())
            .forItem(event.itemId())
            .withPublicDetails(null, event.publicReason(), null, null, null));
  }

  private String administrativeMetadata(String... fields) {
    return String.join(";", fields);
  }
}
