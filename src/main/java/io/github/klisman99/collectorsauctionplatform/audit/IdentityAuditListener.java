package io.github.klisman99.collectorsauctionplatform.audit;

import io.github.klisman99.collectorsauctionplatform.identity.RegularAccountRegistered;
import io.github.klisman99.collectorsauctionplatform.identity.RegularAccountVerified;
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
        auditRecords.save(AuditRecord.systemAction(
                AuditRecord.AuditAction.REGULAR_ACCOUNT_REGISTERED,
                event.accountId(),
                event.occurredAt(),
                "publicHandle=" + event.publicHandle()));
    }

    @ApplicationModuleListener
    void auditVerification(RegularAccountVerified event) {
        auditRecords.save(AuditRecord.accountAction(
                AuditRecord.AuditAction.REGULAR_ACCOUNT_VERIFIED,
                event.accountId(),
                event.occurredAt(),
                "verification=completed"));
    }
}
