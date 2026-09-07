package io.github.klisman99.collectorsauctionplatform.identity;

import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class AccountEligibilityConfiguration {
    @Bean AccountEligibility accountEligibility(RegularAccountRepository accounts) {
        return accountId -> accounts.findById(accountId).map(account -> account.status() == AccountStatus.ACTIVE && account.isVerified()).orElse(false);
    }
}
