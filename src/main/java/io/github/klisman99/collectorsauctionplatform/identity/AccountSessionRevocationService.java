package io.github.klisman99.collectorsauctionplatform.identity;

import java.util.UUID;

import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AccountSessionRevocationService {

    private final FindByIndexNameSessionRepository<? extends Session> sessions;
    private final RegularAccountRepository accounts;

    AccountSessionRevocationService(
            FindByIndexNameSessionRepository<? extends Session> sessions,
            RegularAccountRepository accounts) {
        this.sessions = sessions;
        this.accounts = accounts;
    }

    @Transactional
    void revokeAll(UUID accountId) {
        accounts.findByIdForUpdate(accountId);
        sessions.findByPrincipalName(accountId.toString())
                .keySet()
                .forEach(sessions::deleteById);
    }
}
