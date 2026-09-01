package io.github.klisman99.collectorsauctionplatform.platform.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
class SecurityConfiguration {

    private final ApiProblemWriter problemWriter;

    SecurityConfiguration(ApiProblemWriter problemWriter) {
        this.problemWriter = problemWriter;
    }

    @Bean
    SecurityFilterChain applicationSecurity(HttpSecurity http) throws Exception {
        CookieCsrfTokenRepository csrfTokens = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfTokens.setCookiePath("/");

        return http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/v1/**", "/v3/api-docs/**", "/actuator/health/**").permitAll()
                        .anyRequest().denyAll())
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokens))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> problemWriter.write(
                                request,
                                response,
                                org.springframework.http.HttpStatus.UNAUTHORIZED,
                                "AUTHENTICATION_REQUIRED",
                                "Authentication is required to access this resource."))
                        .accessDeniedHandler((request, response, exception) -> problemWriter.write(
                                request,
                                response,
                                org.springframework.http.HttpStatus.FORBIDDEN,
                                "ACCESS_DENIED",
                                "The authenticated account cannot access this resource.")))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .headers(Customizer.withDefaults())
                .build();
    }
}
