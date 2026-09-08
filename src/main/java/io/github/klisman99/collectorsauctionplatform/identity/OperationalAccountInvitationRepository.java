package io.github.klisman99.collectorsauctionplatform.identity;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface OperationalAccountInvitationRepository
    extends JpaRepository<OperationalAccountInvitation, UUID> {

  Optional<OperationalAccountInvitation> findByTokenDigest(String tokenDigest);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select invitation from OperationalAccountInvitation invitation where invitation.tokenDigest = ?1")
  Optional<OperationalAccountInvitation> findByTokenDigestForUpdate(String tokenDigest);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select invitation from OperationalAccountInvitation invitation "
          + "where invitation.operationalAccountId = ?1 and invitation.usedAt is null")
  List<OperationalAccountInvitation> findUnusedByOperationalAccountIdForUpdate(
      UUID operationalAccountId);
}
