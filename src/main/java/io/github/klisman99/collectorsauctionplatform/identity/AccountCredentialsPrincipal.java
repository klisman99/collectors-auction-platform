package io.github.klisman99.collectorsauctionplatform.identity;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

class AccountCredentialsPrincipal implements UserDetails, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID accountId;
    private final String normalizedEmail;
    private final String publicHandle;
    private final String passwordHash;
    private final AccountStatus status;
    private final boolean verified;

    AccountCredentialsPrincipal(RegularAccount account) {
        this.accountId = account.id();
        this.normalizedEmail = account.normalizedEmail();
        this.publicHandle = account.publicHandle();
        this.passwordHash = account.passwordHash();
        this.status = account.status();
        this.verified = account.isVerified();
    }

    AccountSessionPrincipal sessionPrincipal() {
        return new AccountSessionPrincipal(accountId, publicHandle, status, verified, authorities());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities();
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return normalizedEmail;
    }

    private List<GrantedAuthority> authorities() {
        List<GrantedAuthority> authorities = new java.util.ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_REGULAR_ACCOUNT"));
        if (verified && status == AccountStatus.ACTIVE) {
            authorities.add(new SimpleGrantedAuthority("TRADING_ELIGIBLE"));
        }
        return List.copyOf(authorities);
    }
}
