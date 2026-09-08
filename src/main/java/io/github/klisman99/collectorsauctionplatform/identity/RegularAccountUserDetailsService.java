package io.github.klisman99.collectorsauctionplatform.identity;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

class RegularAccountUserDetailsService implements UserDetailsService {

  private final RegularAccountRepository accounts;

  RegularAccountUserDetailsService(RegularAccountRepository accounts) {
    this.accounts = accounts;
  }

  @Override
  public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
    return accounts
        .findByNormalizedEmail(IdentityNormalization.email(email))
        .map(AccountCredentialsPrincipal::new)
        .orElseThrow(() -> new UsernameNotFoundException("Account credentials are invalid."));
  }
}
