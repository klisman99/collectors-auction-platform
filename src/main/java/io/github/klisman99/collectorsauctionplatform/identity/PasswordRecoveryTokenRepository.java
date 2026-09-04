package io.github.klisman99.collectorsauctionplatform.identity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

interface PasswordRecoveryTokenRepository extends JpaRepository<PasswordRecoveryToken, UUID> {

    /**
     * Serializes recovery-token consumption at the database boundary. A
     * recovery link is a one-shot credential and must remain safe across
     * concurrent requests and application instances.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PasswordRecoveryToken> findByTokenDigest(String tokenDigest);

    List<PasswordRecoveryToken> findAllByAccountIdAndUsedAtIsNull(UUID accountId);
}
