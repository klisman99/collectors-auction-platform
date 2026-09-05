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
        AccountType accountType,
        AccountStatus status,
        OperationalRole operationalRole,
        OperationalStatus operationalStatus,
        boolean verified,
        Collection<? extends GrantedAuthority> authorities) implements Principal, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    boolean isVerified() {
        return verified;
    }

    boolean canTrade() {
        return accountType == AccountType.REGULAR && verified && status == AccountStatus.ACTIVE;
    }

    String statusName() {
        return accountType == AccountType.OPERATIONAL
                ? operationalStatus.name()
                : status.name();
    }

    String roleName() {
        return operationalRole == null ? null : operationalRole.name();
    }

    @Override
    public String getName() {
        return accountId.toString();
    }
}
