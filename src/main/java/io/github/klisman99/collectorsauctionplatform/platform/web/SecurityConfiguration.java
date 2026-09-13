package io.github.klisman99.collectorsauctionplatform.platform.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
class SecurityConfiguration {

  private final ApiProblemWriter problemWriter;
  private final AbsoluteSessionExpiryFilter absoluteSessionExpiryFilter;

  SecurityConfiguration(
      ApiProblemWriter problemWriter, AbsoluteSessionExpiryFilter absoluteSessionExpiryFilter) {
    this.problemWriter = problemWriter;
    this.absoluteSessionExpiryFilter = absoluteSessionExpiryFilter;
  }

  @Bean
  SecurityContextRepository securityContextRepository() {
    return new HttpSessionSecurityContextRepository();
  }

  @Bean
  SecurityFilterChain applicationSecurity(
      HttpSecurity http, SecurityContextRepository securityContextRepository) throws Exception {
    CookieCsrfTokenRepository csrfTokens = CookieCsrfTokenRepository.withHttpOnlyFalse();
    csrfTokens.setCookiePath("/");

    return http.authorizeHttpRequests(
            authorize ->
                authorize
                    .requestMatchers(
                        "/v3/api-docs/**",
                        "/actuator/health/**",
                        "/api/v1/csrf",
                        "/api/v1/status",
                        "/api/v1/auth/register",
                        "/api/v1/auth/verify-email",
                        "/api/v1/auth/sign-in",
                        "/api/v1/auth/request-password-recovery",
                        "/api/v1/auth/reset-password",
                        "/api/v1/auth/activate-operational-account",
                        "/api/v1/test/**")
                    .permitAll()
                    .requestMatchers(
                        "/api/v1/auth/session",
                        "/api/v1/auth/sign-out",
                        "/api/v1/auth/revoke-all-sessions")
                    .authenticated()
                    .requestMatchers("/api/v1/catalog/drafts/**")
                    .hasAuthority("TRADING_ELIGIBLE")
                    .requestMatchers("/api/v1/admin/**")
                    .hasAuthority("ROLE_ADMINISTRATOR")
                    .requestMatchers("/api/v1/moderation/**")
                    .hasAnyAuthority("ROLE_MODERATOR", "ROLE_ADMINISTRATOR")
                    .requestMatchers("/api/v1/operations/**")
                    .hasAnyAuthority("ROLE_MODERATOR", "ROLE_ADMINISTRATOR")
                    .requestMatchers(HttpMethod.GET, "/api/v1/auctions/mine")
                    .authenticated()
                    .requestMatchers(HttpMethod.GET, "/api/v1/**")
                    .permitAll()
                    .requestMatchers("/api/v1/**")
                    .hasAuthority("TRADING_ELIGIBLE")
                    .anyRequest()
                    .denyAll())
        .csrf(csrf -> csrf.csrfTokenRepository(csrfTokens))
        .securityContext(
            context ->
                context
                    .securityContextRepository(securityContextRepository)
                    .requireExplicitSave(true))
        .exceptionHandling(
            exceptions ->
                exceptions
                    .authenticationEntryPoint(
                        (request, response, exception) ->
                            problemWriter.write(
                                request,
                                response,
                                org.springframework.http.HttpStatus.UNAUTHORIZED,
                                "AUTHENTICATION_REQUIRED",
                                "Authentication is required to access this resource."))
                    .accessDeniedHandler(
                        (request, response, exception) -> {
                          boolean catalogMutation =
                              request.getRequestURI().startsWith("/api/v1/catalog/drafts");
                          boolean auctionMutation =
                              request.getRequestURI().startsWith("/api/v1/auctions");
                          if (!catalogMutation && !auctionMutation) {
                            problemWriter.write(
                                request,
                                response,
                                org.springframework.http.HttpStatus.FORBIDDEN,
                                "ACCESS_DENIED",
                                "The authenticated account cannot access this resource.");
                            return;
                          }
                          var authentication =
                              org.springframework.security.core.context.SecurityContextHolder
                                  .getContext()
                                  .getAuthentication();
                          boolean suspended =
                              authentication != null
                                  && authentication.getAuthorities().stream()
                                      .anyMatch(
                                          authority ->
                                              authority.getAuthority().equals("ACCOUNT_SUSPENDED"));
                          problemWriter.write(
                              request,
                              response,
                              org.springframework.http.HttpStatus.FORBIDDEN,
                              suspended ? "ACCOUNT_SUSPENDED" : "ACCOUNT_NOT_VERIFIED",
                              suspended ? "BR-AUTH-009" : "BR-AUTH-004",
                              suspended
                                  ? "A suspended account cannot perform marketplace commands."
                                  : "Email verification is required for marketplace commands.");
                        }))
        .sessionManagement(
            session ->
                session
                    .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                    .sessionFixation(fixation -> fixation.newSession()))
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .headers(Customizer.withDefaults())
        .addFilterBefore(absoluteSessionExpiryFilter, SecurityContextHolderFilter.class)
        .build();
  }
}
