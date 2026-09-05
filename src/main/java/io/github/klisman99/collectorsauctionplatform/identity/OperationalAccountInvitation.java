package io.github.klisman99.collectorsauctionplatform.identity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "operational_account_invitations")
class OperationalAccountInvitation {

    @Id
    private UUID id;

    @Column(name = "operational_account_id", nullable = false, updatable = false)
    private UUID operationalAccountId;

    @Column(name = "token_digest", nullable = false, updatable = false)
    private String tokenDigest;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "invited_by", nullable = false, updatable = false)
    private UUID invitedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_category", nullable = false, updatable = false)
    private AdministrativeReasonCategory reasonCategory;

    @Column(name = "public_reason", nullable = false, updatable = false)
    private String publicReason;

    @Column(name = "internal_note", updatable = false)
    private String internalNote;

    protected OperationalAccountInvitation() {
    }

    OperationalAccountInvitation(
            UUID id,
            UUID operationalAccountId,
            String tokenDigest,
            Instant expiresAt,
            UUID invitedBy,
            AdministrativeReasonCategory reasonCategory,
            String publicReason,
            String internalNote) {
        this.id = id;
        this.operationalAccountId = operationalAccountId;
        this.tokenDigest = tokenDigest;
        this.expiresAt = expiresAt;
        this.invitedBy = invitedBy;
        this.reasonCategory = reasonCategory;
        this.publicReason = publicReason;
        this.internalNote = internalNote;
    }

    boolean isUsableAt(Instant now) {
        return usedAt == null && now.isBefore(expiresAt);
    }

    void markUsed(Instant usedAt) {
        this.usedAt = usedAt;
    }

    UUID operationalAccountId() {
        return operationalAccountId;
    }

    Instant expiresAt() {
        return expiresAt;
    }
}
