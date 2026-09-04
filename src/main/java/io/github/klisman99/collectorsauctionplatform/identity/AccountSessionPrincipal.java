package io.github.klisman99.collectorsauctionplatform.identity;

import java.io.Serial;
import java.io.Serializable;
import java.security.Principal;
import java.util.Collection;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;

record AccountSessionPrincipal(
        UUID accountId,
        String publicHandle,
        AccountStatus status,
        boolean verified,
        Collection<? extends GrantedAuthority> authorities) implements Principal, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    boolean isVerified() {
        return verified;
    }

    boolean canTrade() {
        return verified && status == AccountStatus.ACTIVE;
    }

    @Override
    public String getName() {
        return accountId.toString();
    }
}
