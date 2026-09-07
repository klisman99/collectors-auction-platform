package io.github.klisman99.collectorsauctionplatform.identity;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;

@Configuration
class IdentityConfiguration {

  @Bean
  PasswordEncoder accountPasswordEncoder() {
    return Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8();
  }

  @Bean
  AuthenticationManager authenticationManager(
      RegularAccountUserDetailsService regularAccountUserDetailsService,
      PasswordEncoder accountPasswordEncoder) {
    DaoAuthenticationProvider provider =
        new DaoAuthenticationProvider(regularAccountUserDetailsService);
    provider.setPasswordEncoder(accountPasswordEncoder);
    return new ProviderManager(provider);
  }
}
