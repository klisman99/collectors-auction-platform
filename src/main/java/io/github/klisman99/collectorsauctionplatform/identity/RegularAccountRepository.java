package io.github.klisman99.collectorsauctionplatform.identity;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface RegularAccountRepository extends JpaRepository<RegularAccount, UUID> {

    boolean existsByNormalizedEmail(String normalizedEmail);

    boolean existsByPublicHandle(String publicHandle);

    Optional<RegularAccount> findByNormalizedEmail(String normalizedEmail);
}
