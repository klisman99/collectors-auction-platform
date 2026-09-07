package io.github.klisman99.collectorsauctionplatform.identity;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface PasswordRecoveryTokenRepository extends JpaRepository<PasswordRecoveryToken, UUID> {

  Optional<PasswordRecoveryToken> findByTokenDigest(String tokenDigest);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select token from PasswordRecoveryToken token where token.tokenDigest = ?1")
  Optional<PasswordRecoveryToken> findByTokenDigestForUpdate(String tokenDigest);

  List<PasswordRecoveryToken> findAllByAccountIdAndUsedAtIsNull(UUID accountId);
}
