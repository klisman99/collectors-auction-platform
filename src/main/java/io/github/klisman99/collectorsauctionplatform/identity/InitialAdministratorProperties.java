package io.github.klisman99.collectorsauctionplatform.identity;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("platform.identity.initial-administrator")
record InitialAdministratorProperties(String email, String password) {
}
