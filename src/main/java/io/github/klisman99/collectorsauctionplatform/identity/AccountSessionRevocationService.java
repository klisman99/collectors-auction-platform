package io.github.klisman99.collectorsauctionplatform.identity;

import java.util.UUID;

import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AccountSessionRevocationService {

    private final FindByIndexNameSessionRepository<? extends Session> sessions;
    private final RegularAccountRepository regularAccounts;
    private final OperationalAccountRepository operationalAccounts;

    AccountSessionRevocationService(
            FindByIndexNameSessionRepository<? extends Session> sessions,
            RegularAccountRepository regularAccounts,
            OperationalAccountRepository operationalAccounts) {
        this.sessions = sessions;
        this.regularAccounts = regularAccounts;
        this.operationalAccounts = operationalAccounts;
    }

    @Transactional
    void revokeAll(UUID accountId) {
        if (regularAccounts.findByIdForUpdate(accountId).isEmpty()) {
            operationalAccounts.findByIdForUpdate(accountId);
        }
        sessions.findByPrincipalName(accountId.toString())
                .keySet()
                .forEach(sessions::deleteById);
    }
}
