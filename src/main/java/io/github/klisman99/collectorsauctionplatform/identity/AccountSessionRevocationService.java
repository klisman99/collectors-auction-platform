package io.github.klisman99.collectorsauctionplatform.identity;

import java.util.UUID;

import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;

@Service
class AccountSessionRevocationService {

    private final FindByIndexNameSessionRepository<? extends Session> sessions;

    AccountSessionRevocationService(FindByIndexNameSessionRepository<? extends Session> sessions) {
        this.sessions = sessions;
    }

    void revokeAll(UUID accountId) {
        sessions.findByPrincipalName(accountId.toString())
                .keySet()
                .forEach(sessions::deleteById);
    }
}
