package io.github.klisman99.collectorsauctionplatform.identity;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface RegularAccountRepository extends JpaRepository<RegularAccount, UUID> {

  boolean existsByNormalizedEmail(String normalizedEmail);

  boolean existsByPublicHandle(String publicHandle);

  Optional<RegularAccount> findByNormalizedEmail(String normalizedEmail);

  java.util.List<RegularAccount> findAllByOrderByRegisteredAtDesc();

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select account from RegularAccount account where account.normalizedEmail = ?1")
  Optional<RegularAccount> findByNormalizedEmailForUpdate(String normalizedEmail);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select account from RegularAccount account where account.id = ?1")
  Optional<RegularAccount> findByIdForUpdate(UUID accountId);
}
