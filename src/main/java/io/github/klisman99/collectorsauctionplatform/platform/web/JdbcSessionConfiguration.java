package io.github.klisman99.collectorsauctionplatform.platform.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.FlushMode;
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

@Configuration
@EnableJdbcHttpSession(
        maxInactiveIntervalInSeconds = 30 * 24 * 60 * 60,
        flushMode = FlushMode.IMMEDIATE)
class JdbcSessionConfiguration {

    @Bean
    CookieSerializer sessionCookieSerializer(@Value("${server.servlet.session.cookie.secure:false}") boolean secureCookie) {
        DefaultCookieSerializer cookies = new DefaultCookieSerializer();
        cookies.setCookieName("JSESSIONID");
        cookies.setUseHttpOnlyCookie(true);
        cookies.setSameSite("Lax");
        cookies.setUseSecureCookie(secureCookie);
        return cookies;
    }
}
