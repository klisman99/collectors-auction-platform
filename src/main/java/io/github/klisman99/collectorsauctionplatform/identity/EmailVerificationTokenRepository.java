package io.github.klisman99.collectorsauctionplatform.identity;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, UUID> {

    /**
     * Serializes verification attempts for a digest.  This is deliberately a
     * database lock: the token is a one-shot credential and application-level
     * synchronization would not protect multiple application instances.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<EmailVerificationToken> findByTokenDigest(String tokenDigest);
}
