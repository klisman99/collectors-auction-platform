package io.github.klisman99.collectorsauctionplatform.identity;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
class AccountUserDetailsService implements UserDetailsService {

    private final RegularAccountRepository regularAccounts;
    private final OperationalAccountRepository operationalAccounts;

    AccountUserDetailsService(
            RegularAccountRepository regularAccounts,
            OperationalAccountRepository operationalAccounts) {
        this.regularAccounts = regularAccounts;
        this.operationalAccounts = operationalAccounts;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        String normalizedEmail = IdentityNormalization.email(email);
        return operationalAccounts.findByNormalizedEmail(normalizedEmail)
                .map(AccountCredentialsPrincipal::new)
                .or(() -> regularAccounts.findByNormalizedEmail(normalizedEmail)
                        .map(AccountCredentialsPrincipal::new))
                .orElseThrow(() -> new UsernameNotFoundException("Account credentials are invalid."));
    }
}
