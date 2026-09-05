package io.github.klisman99.collectorsauctionplatform.identity;

import jakarta.persistence.LockModeType;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface IdentityMutationGuardRepository extends JpaRepository<IdentityMutationGuard, Short> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select guard from IdentityMutationGuard guard")
    Optional<IdentityMutationGuard> findForUpdate();
}
