package io.github.klisman99.collectorsauctionplatform.identity;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/auth", produces = MediaType.APPLICATION_JSON_VALUE)
class AuthenticationController {

    private final RegistrationService registrationService;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final LoginAttemptRateLimiter loginAttemptRateLimiter;
    private final PasswordRecoveryService passwordRecoveryService;
    private final PasswordRecoveryRateLimiter passwordRecoveryRateLimiter;
    private final AccountSessionRevocationService sessionRevocationService;
    private final SecurityContextHolderStrategy securityContextHolderStrategy = SecurityContextHolder.getContextHolderStrategy();

    AuthenticationController(
            RegistrationService registrationService,
            AuthenticationManager authenticationManager,
            SecurityContextRepository securityContextRepository,
            LoginAttemptRateLimiter loginAttemptRateLimiter,
            PasswordRecoveryService passwordRecoveryService,
            PasswordRecoveryRateLimiter passwordRecoveryRateLimiter,
            AccountSessionRevocationService sessionRevocationService) {
        this.registrationService = registrationService;
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.loginAttemptRateLimiter = loginAttemptRateLimiter;
        this.passwordRecoveryService = passwordRecoveryService;
        this.passwordRecoveryRateLimiter = passwordRecoveryRateLimiter;
        this.sessionRevocationService = sessionRevocationService;
    }

    @PostMapping(path = "/register", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "registerRegularAccount", summary = "Register a regular account and request email verification")
    RegistrationResponse register(@Valid @RequestBody RegistrationRequest request) {
        RegistrationService.RegistrationResult registration = registrationService.register(
                request.email(), request.publicHandle(), request.password());
        return new RegistrationResponse(registration.publicHandle(), "PENDING_VERIFICATION");
    }

    @PostMapping(path = "/verify-email", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "verifyRegularAccountEmail", summary = "Verify a regular account with a single-use email token")
    VerificationResponse verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        RegistrationService.VerificationResult verification = registrationService.verify(request.token());
        return new VerificationResponse(verification.publicHandle(), "ACTIVE");
    }

    @PostMapping(path = "/sign-in", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "signInRegularAccount", summary = "Sign in with a revocable server-side session")
    SessionResponse signIn(
            @Valid @RequestBody SignInRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        String normalizedEmail = IdentityNormalization.email(request.email());
        loginAttemptRateLimiter.recordAttempt(clientIp(servletRequest), normalizedEmail);

        Authentication authenticated;
        try {
            authenticated = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(normalizedEmail, request.password()));
        } catch (AuthenticationException exception) {
            throw IdentityApiException.invalidCredentials();
        }

        AccountCredentialsPrincipal credentials = (AccountCredentialsPrincipal) authenticated.getPrincipal();
        AccountSessionPrincipal sessionPrincipal = credentials.sessionPrincipal();
        Authentication sessionAuthentication = UsernamePasswordAuthenticationToken.authenticated(
                sessionPrincipal,
                null,
                sessionPrincipal.authorities());

        HttpSession previousSession = servletRequest.getSession(false);
        if (previousSession != null) {
            previousSession.invalidate();
        }

        SecurityContext context = securityContextHolderStrategy.createEmptyContext();
        context.setAuthentication(sessionAuthentication);
        securityContextHolderStrategy.setContext(context);
        securityContextRepository.saveContext(context, servletRequest, servletResponse);
        return SessionResponse.from(sessionPrincipal);
    }

    @GetMapping(path = "/session")
    @Operation(operationId = "getAuthenticatedSession", summary = "Read the authenticated account state")
    SessionResponse session(Authentication authentication) {
        return SessionResponse.from((AccountSessionPrincipal) authentication.getPrincipal());
    }

    @PostMapping(path = "/request-password-recovery", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(operationId = "requestPasswordRecovery", summary = "Request a single-use password recovery email")
    PasswordRecoveryResponse requestPasswordRecovery(
            @Valid @RequestBody PasswordRecoveryRequest request,
            HttpServletRequest servletRequest) {
        String normalizedEmail = IdentityNormalization.email(request.email());
        passwordRecoveryRateLimiter.recordAttempt(clientIp(servletRequest), normalizedEmail);
        passwordRecoveryService.request(normalizedEmail);
        return new PasswordRecoveryResponse("RECOVERY_REQUEST_RECEIVED");
    }

    @PostMapping(path = "/reset-password", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "resetPassword", summary = "Consume a password recovery token and change the password")
    PasswordResetResponse resetPassword(
            @Valid @RequestBody PasswordResetRequest request,
            HttpServletRequest servletRequest) {
        passwordRecoveryService.reset(request.token(), request.password());
        invalidateCurrentSession(servletRequest);
        return new PasswordResetResponse("PASSWORD_RESET");
    }

    @PostMapping(path = "/sign-out")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "signOut", summary = "Revoke the current server-side session")
    void signOut(HttpServletRequest servletRequest) {
        HttpSession session = servletRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        securityContextHolderStrategy.clearContext();
    }

    @PostMapping(path = "/revoke-all-sessions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "revokeAllSessions", summary = "Revoke every server-side session for the account")
    void revokeAllSessions(Authentication authentication, HttpServletRequest servletRequest) {
        AccountSessionPrincipal principal = (AccountSessionPrincipal) authentication.getPrincipal();
        sessionRevocationService.revokeAll(principal.accountId());
        invalidateCurrentSession(servletRequest);
    }

    private void invalidateCurrentSession(HttpServletRequest servletRequest) {
        HttpSession session = servletRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        securityContextHolderStrategy.clearContext();
    }

    private String clientIp(HttpServletRequest request) {
        // X-Forwarded-For is only meaningful when set by a trusted proxy.
        // The edge proxy overwrites it with the connected client address;
        // accepting the request value here would let clients rotate keys and
        // bypass the login limiter when reaching the app directly.
        return request.getRemoteAddr();
    }

    record RegistrationRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9_]{3,30}$") String publicHandle,
            @NotNull String password) {

        RegistrationRequest {
            email = email == null ? null : email.trim();
            publicHandle = publicHandle == null ? null : publicHandle.trim();
        }
    }

    record VerifyEmailRequest(@NotBlank @Size(max = 128) String token) {
    }

    record SignInRequest(@NotBlank @Email @Size(max = 254) String email, @NotNull String password) {

        SignInRequest {
            email = email == null ? null : email.trim();
        }
    }

    record PasswordRecoveryRequest(@NotBlank @Email @Size(max = 254) String email) {

        PasswordRecoveryRequest {
            email = email == null ? null : email.trim();
        }
    }

    record PasswordResetRequest(@NotBlank @Size(max = 128) String token, @NotNull String password) {
    }

    @Schema(name = "Registration", description = "The accepted public state of a newly registered regular account.")
    record RegistrationResponse(String publicHandle, String status) {
    }

    @Schema(name = "EmailVerification", description = "The result of consuming an email-verification token.")
    record VerificationResponse(String publicHandle, String status) {
    }

    @Schema(name = "AuthenticatedSession", description = "The current authenticated regular-account session state.")
    record SessionResponse(String publicHandle, String status, boolean verified, boolean canTrade) {

        static SessionResponse from(AccountSessionPrincipal principal) {
            return new SessionResponse(
                    principal.publicHandle(),
                    principal.status().name(),
                    principal.isVerified(),
                    principal.canTrade());
        }
    }

    @Schema(name = "PasswordRecoveryRequestAccepted", description = "A generic response that does not reveal whether an account exists.")
    record PasswordRecoveryResponse(String status) {
    }

    @Schema(name = "PasswordReset", description = "The result of consuming a password recovery token.")
    record PasswordResetResponse(String status) {
    }
}
