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
@Table(name = "operational_accounts")
class OperationalAccount {

    private static final String INVITED_PASSWORD_SENTINEL = "{invited}";

    @Id
    private UUID id;

    @Column(name = "normalized_email", nullable = false, updatable = false)
    private String normalizedEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private OperationalRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OperationalStatus status;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "invited_at", nullable = false, updatable = false)
    private Instant invitedAt;

    @Column(name = "invited_by", updatable = false)
    private UUID invitedBy;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "deactivated_at")
    private Instant deactivatedAt;

    protected OperationalAccount() {
    }

    private OperationalAccount(
            UUID id,
            String normalizedEmail,
            OperationalRole role,
            String passwordHash,
            Instant invitedAt,
            UUID invitedBy,
            OperationalStatus status) {
        this.id = id;
        this.normalizedEmail = normalizedEmail;
        this.role = role;
        this.passwordHash = passwordHash;
        this.invitedAt = invitedAt;
        this.invitedBy = invitedBy;
        this.status = status;
    }

    static OperationalAccount invite(
            UUID id,
            String normalizedEmail,
            OperationalRole role,
            Instant invitedAt,
            UUID invitedBy) {
        return new OperationalAccount(
                id,
                normalizedEmail,
                role,
                INVITED_PASSWORD_SENTINEL,
                invitedAt,
                invitedBy,
                OperationalStatus.INVITED);
    }

    static OperationalAccount initialAdministrator(
            UUID id,
            String normalizedEmail,
            String passwordHash,
            Instant createdAt) {
        return new OperationalAccount(
                id,
                normalizedEmail,
                OperationalRole.ADMINISTRATOR,
                passwordHash,
                createdAt,
                null,
                OperationalStatus.ACTIVE);
    }

    void activate(String passwordHash, Instant activatedAt) {
        if (status != OperationalStatus.INVITED) {
            throw IdentityApiException.invalidOperationalActivationToken();
        }
        this.passwordHash = passwordHash;
        this.status = OperationalStatus.ACTIVE;
        this.activatedAt = activatedAt;
    }

    void deactivate(Instant deactivatedAt) {
        if (status == OperationalStatus.DEACTIVATED) {
            throw IdentityApiException.operationalAccountNotActive();
        }
        this.status = OperationalStatus.DEACTIVATED;
        this.deactivatedAt = deactivatedAt;
    }

    UUID id() {
        return id;
    }

    String normalizedEmail() {
        return normalizedEmail;
    }

    OperationalRole role() {
        return role;
    }

    OperationalStatus status() {
        return status;
    }

    String passwordHash() {
        return passwordHash;
    }

    Instant invitedAt() {
        return invitedAt;
    }

    UUID invitedBy() {
        return invitedBy;
    }

    Instant activatedAt() {
        return activatedAt;
    }

    Instant deactivatedAt() {
        return deactivatedAt;
    }

    boolean isActiveAdministrator() {
        return role == OperationalRole.ADMINISTRATOR && status == OperationalStatus.ACTIVE;
    }
}
