package io.github.klisman99.collectorsauctionplatform.identity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface OperationalAccountInvitationRepository extends JpaRepository<OperationalAccountInvitation, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select invitation from OperationalAccountInvitation invitation where invitation.tokenDigest = ?1")
    Optional<OperationalAccountInvitation> findByTokenDigestForUpdate(String tokenDigest);

    List<OperationalAccountInvitation> findAllByOperationalAccountIdAndUsedAtIsNull(UUID operationalAccountId);
}
