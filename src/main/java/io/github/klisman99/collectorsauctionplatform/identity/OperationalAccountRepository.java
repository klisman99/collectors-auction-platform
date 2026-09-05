package io.github.klisman99.collectorsauctionplatform.identity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface OperationalAccountRepository extends JpaRepository<OperationalAccount, UUID> {

    Optional<OperationalAccount> findByNormalizedEmail(String normalizedEmail);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from OperationalAccount account where account.normalizedEmail = ?1")
    Optional<OperationalAccount> findByNormalizedEmailForUpdate(String normalizedEmail);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from OperationalAccount account where account.id = ?1")
    Optional<OperationalAccount> findByIdForUpdate(UUID accountId);

    List<OperationalAccount> findAllByOrderByInvitedAtDesc();

    long countByRoleAndStatus(OperationalRole role, OperationalStatus status);
}
