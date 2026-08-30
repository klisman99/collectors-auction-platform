package io.github.klisman99.collectorsauctionplatform.platform.web;

import org.springframework.http.MediaType;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

@RestController
class CsrfController {

    @GetMapping(path = "/api/v1/csrf", produces = MediaType.APPLICATION_JSON_VALUE)
    CsrfToken csrfToken(@RequestAttribute("_csrf") CsrfToken csrfToken) {
        return csrfToken;
    }
}
