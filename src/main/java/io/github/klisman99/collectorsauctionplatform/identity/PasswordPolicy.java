package io.github.klisman99.collectorsauctionplatform.identity;

import org.springframework.stereotype.Component;

@Component
class PasswordPolicy {

    void validate(String password) {
        int characterCount = password.codePointCount(0, password.length());
        if (characterCount < 12 || characterCount > 128) {
            throw IdentityApiException.invalidPasswordLength();
        }
    }
}
