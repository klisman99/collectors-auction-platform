package io.github.klisman99.collectorsauctionplatform.identity;

import java.util.Locale;

final class IdentityNormalization {

    private IdentityNormalization() {
    }

    static String email(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    static String publicHandle(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
