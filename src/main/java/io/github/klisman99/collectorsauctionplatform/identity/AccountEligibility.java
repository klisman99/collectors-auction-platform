package io.github.klisman99.collectorsauctionplatform.identity;

import java.util.UUID;

public interface AccountEligibility {
    boolean canEditCatalog(UUID accountId);
}
