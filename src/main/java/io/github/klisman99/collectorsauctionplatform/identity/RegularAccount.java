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
@Table(name = "regular_accounts")
class RegularAccount {

    @Id
    private UUID id;

    @Column(name = "normalized_email", nullable = false, updatable = false)
    private String normalizedEmail;

    @Column(name = "public_handle", nullable = false, updatable = false)
    private String publicHandle;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus status;

    @Column(name = "registered_at", nullable = false, updatable = false)
    private Instant registeredAt;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    protected RegularAccount() {
    }

    private RegularAccount(
            UUID id,
            String normalizedEmail,
            String publicHandle,
            String passwordHash,
            Instant registeredAt) {
        this.id = id;
        this.normalizedEmail = normalizedEmail;
        this.publicHandle = publicHandle;
        this.passwordHash = passwordHash;
        this.status = AccountStatus.PENDING_VERIFICATION;
        this.registeredAt = registeredAt;
    }

    static RegularAccount register(
            UUID id,
            String normalizedEmail,
            String publicHandle,
            String passwordHash,
            Instant registeredAt) {
        return new RegularAccount(id, normalizedEmail, publicHandle, passwordHash, registeredAt);
    }

    void verify(Instant verifiedAt) {
        if (status != AccountStatus.PENDING_VERIFICATION) {
            throw IdentityApiException.invalidVerificationToken();
        }
        status = AccountStatus.ACTIVE;
        this.verifiedAt = verifiedAt;
    }

    UUID id() {
        return id;
    }

    String normalizedEmail() {
        return normalizedEmail;
    }

    String publicHandle() {
        return publicHandle;
    }

    String passwordHash() {
        return passwordHash;
    }

    AccountStatus status() {
        return status;
    }

    boolean isVerified() {
        return verifiedAt != null;
    }
}
