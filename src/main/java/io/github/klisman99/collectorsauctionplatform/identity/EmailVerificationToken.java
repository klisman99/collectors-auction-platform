package io.github.klisman99.collectorsauctionplatform.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "email_verification_tokens")
class EmailVerificationToken {

  @Id private UUID id;

  @Column(name = "account_id", nullable = false, updatable = false)
  private UUID accountId;

  @Column(name = "token_digest", nullable = false, updatable = false)
  private String tokenDigest;

  @Column(name = "expires_at", nullable = false, updatable = false)
  private Instant expiresAt;

  @Column(name = "used_at")
  private Instant usedAt;

  protected EmailVerificationToken() {}

  EmailVerificationToken(UUID id, UUID accountId, String tokenDigest, Instant expiresAt) {
    this.id = id;
    this.accountId = accountId;
    this.tokenDigest = tokenDigest;
    this.expiresAt = expiresAt;
  }

  boolean isUsableAt(Instant now) {
    return usedAt == null && now.isBefore(expiresAt);
  }

  void markUsed(Instant usedAt) {
    this.usedAt = usedAt;
  }

  UUID accountId() {
    return accountId;
  }
}
