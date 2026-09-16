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

  private final AuditRecordRepository auditRecords;

  IdentityAuditListener(AuditRecordRepository auditRecords) {
    this.auditRecords = auditRecords;
  }

  @ApplicationModuleListener
  void auditRegistration(RegularAccountRegistered event) {
    auditRecords.save(
        AuditRecord.systemAction(
            AuditRecord.AuditAction.REGULAR_ACCOUNT_REGISTERED,
            event.accountId(),
            event.occurredAt(),
            "publicHandle=" + event.publicHandle()));
  }

  @ApplicationModuleListener
  void auditVerification(RegularAccountVerified event) {
    auditRecords.save(
        AuditRecord.accountAction(
            AuditRecord.AuditAction.REGULAR_ACCOUNT_VERIFIED,
            event.accountId(),
            event.occurredAt(),
            "verification=completed"));
  }

  @ApplicationModuleListener
  void auditPasswordReset(RegularAccountPasswordReset event) {
    auditRecords.save(
        AuditRecord.accountAction(
            AuditRecord.AuditAction.REGULAR_ACCOUNT_PASSWORD_RESET,
            event.accountId(),
            event.occurredAt(),
            "password=reset"));
  }

  @ApplicationModuleListener
  void auditRegularAccountSuspension(RegularAccountSuspended event) {
    auditRecords.save(
        AuditRecord.operationalRegularAccountAction(
            AuditRecord.AuditAction.REGULAR_ACCOUNT_SUSPENDED,
            event.actorId(),
            event.accountId(),
            event.occurredAt(),
            administrativeMetadata(
                "publicHandle=" + event.publicHandle(),
                "reasonCategory=" + event.reasonCategory(),
                "publicReason=" + event.publicReason(),
                "internalNote=" + event.internalNote())));
  }

  @ApplicationModuleListener
  void auditRegularAccountReactivation(RegularAccountReactivated event) {
    auditRecords.save(
        AuditRecord.operationalRegularAccountAction(
            AuditRecord.AuditAction.REGULAR_ACCOUNT_REACTIVATED,
            event.actorId(),
            event.accountId(),
            event.occurredAt(),
            administrativeMetadata(
                "publicHandle=" + event.publicHandle(),
                "reasonCategory=" + event.reasonCategory(),
                "publicReason=" + event.publicReason(),
                "internalNote=" + event.internalNote())));
  }

  @ApplicationModuleListener
  void auditInitialAdministrator(InitialAdministratorCreated event) {
    auditRecords.save(
        AuditRecord.systemOperationalAction(
            AuditRecord.AuditAction.INITIAL_ADMINISTRATOR_CREATED,
            event.accountId(),
            event.occurredAt(),
            "email=" + event.email()));
  }

  @ApplicationModuleListener
  void auditOperationalInvitation(OperationalAccountInvited event) {
    auditRecords.save(
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
                "internalNote=" + event.internalNote())));
  }

  @ApplicationModuleListener
  void auditOperationalActivation(OperationalAccountActivated event) {
    auditRecords.save(
        AuditRecord.operationalAction(
            AuditRecord.AuditAction.OPERATIONAL_ACCOUNT_ACTIVATED,
            event.accountId(),
            event.accountId(),
            event.occurredAt(),
            "activation=completed"));
  }

  @ApplicationModuleListener
  void auditOperationalDeactivation(OperationalAccountDeactivated event) {
    auditRecords.save(
        AuditRecord.operationalAction(
            AuditRecord.AuditAction.OPERATIONAL_ACCOUNT_DEACTIVATED,
            event.actorId(),
            event.accountId(),
            event.occurredAt(),
            administrativeMetadata(
                "role=" + event.role(),
                "reasonCategory=" + event.reasonCategory(),
                "publicReason=" + event.publicReason(),
                "internalNote=" + event.internalNote())));
  }

  @ApplicationModuleListener
  void auditCollectibleSubmission(CollectibleSubmitted event) {
    auditRecords.save(
        AuditRecord.accountCollectibleAction(
            AuditRecord.AuditAction.COLLECTIBLE_SUBMITTED,
            event.ownerId(),
            event.itemId(),
            event.occurredAt(),
            "title=" + event.title()));
  }

  @ApplicationModuleListener
  void auditModerationDecision(ModerationDecisionMade event) {
    AuditRecord.AuditAction action =
        event.decision() == ModerationDecisionMade.Decision.APPROVED
            ? AuditRecord.AuditAction.COLLECTIBLE_APPROVED
            : AuditRecord.AuditAction.COLLECTIBLE_REJECTED;
    auditRecords.save(
        AuditRecord.operationalCollectibleAction(
            action,
            event.reviewerId(),
            event.itemId(),
            event.occurredAt(),
            "publicReason=" + (event.publicReason() == null ? "" : event.publicReason())));
  }

  private String administrativeMetadata(String... fields) {
    return String.join(";", fields);
  }
}
