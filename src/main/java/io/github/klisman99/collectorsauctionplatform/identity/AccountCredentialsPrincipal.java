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

  @Serial private static final long serialVersionUID = 1L;

  private final UUID accountId;
  private final String normalizedEmail;
  private final String publicHandle;
  private final String passwordHash;
  private final AccountType accountType;
  private final AccountStatus status;
  private final OperationalRole operationalRole;
  private final OperationalStatus operationalStatus;
  private final boolean verified;

  AccountCredentialsPrincipal(RegularAccount account) {
    this.accountId = account.id();
    this.normalizedEmail = account.normalizedEmail();
    this.publicHandle = account.publicHandle();
    this.passwordHash = account.passwordHash();
    this.accountType = AccountType.REGULAR;
    this.status = account.status();
    this.operationalRole = null;
    this.operationalStatus = null;
    this.verified = account.isVerified();
  }

  AccountCredentialsPrincipal(OperationalAccount account) {
    this.accountId = account.id();
    this.normalizedEmail = account.normalizedEmail();
    this.publicHandle = null;
    this.passwordHash = account.passwordHash();
    this.accountType = AccountType.OPERATIONAL;
    this.status = null;
    this.operationalRole = account.role();
    this.operationalStatus = account.status();
    this.verified = account.status() == OperationalStatus.ACTIVE;
  }

  AccountSessionPrincipal sessionPrincipal() {
    return new AccountSessionPrincipal(
        accountId,
        publicHandle,
        accountType,
        status,
        operationalRole,
        operationalStatus,
        verified,
        authorities());
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

  @Override
  public boolean isEnabled() {
    return accountType == AccountType.REGULAR || operationalStatus == OperationalStatus.ACTIVE;
  }

  private List<GrantedAuthority> authorities() {
    List<GrantedAuthority> authorities = new java.util.ArrayList<>();
    if (accountType == AccountType.REGULAR) {
      authorities.add(new SimpleGrantedAuthority("ROLE_REGULAR_ACCOUNT"));
      if (status == AccountStatus.SUSPENDED) {
        authorities.add(new SimpleGrantedAuthority("ACCOUNT_SUSPENDED"));
      }
      if (verified && status == AccountStatus.ACTIVE) {
        authorities.add(new SimpleGrantedAuthority("TRADING_ELIGIBLE"));
      }
    } else {
      authorities.add(new SimpleGrantedAuthority("ROLE_OPERATIONAL_ACCOUNT"));
      authorities.add(new SimpleGrantedAuthority("ROLE_" + operationalRole.name()));
    }
    return List.copyOf(authorities);
  }
}
