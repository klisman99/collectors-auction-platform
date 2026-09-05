package io.github.klisman99.collectorsauctionplatform.identity;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class OperationalAccountService {

    private static final Duration INVITATION_LIFETIME = Duration.ofHours(24);

    private final IdentityMutationGuardRepository mutationGuards;
    private final OperationalAccountRepository operationalAccounts;
    private final OperationalAccountInvitationRepository invitations;
    private final RegularAccountRepository regularAccounts;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final VerificationTokenGenerator tokenGenerator;
    private final AccountSessionRevocationService sessions;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    OperationalAccountService(
            IdentityMutationGuardRepository mutationGuards,
            OperationalAccountRepository operationalAccounts,
            OperationalAccountInvitationRepository invitations,
            RegularAccountRepository regularAccounts,
            PasswordEncoder passwordEncoder,
            PasswordPolicy passwordPolicy,
            VerificationTokenGenerator tokenGenerator,
            AccountSessionRevocationService sessions,
            ApplicationEventPublisher events,
            Clock clock) {
        this.mutationGuards = mutationGuards;
        this.operationalAccounts = operationalAccounts;
        this.invitations = invitations;
        this.regularAccounts = regularAccounts;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.tokenGenerator = tokenGenerator;
        this.sessions = sessions;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    InvitationResult invite(
            UUID actorId,
            String email,
            String role,
            String reasonCategory,
            String publicReason,
            String internalNote) {
        lockIdentityMutations();
        String normalizedEmail = IdentityNormalization.email(email);
        OperationalRole operationalRole = parseRole(role);
        AdministrativeReasonCategory category = parseReasonCategory(reasonCategory);
        String normalizedPublicReason = requiredReason(publicReason);
        String normalizedInternalNote = optionalNote(internalNote);

        if (regularAccounts.existsByNormalizedEmail(normalizedEmail)
                || operationalAccounts.findByNormalizedEmailForUpdate(normalizedEmail).isPresent()) {
            throw IdentityApiException.operationalEmailAlreadyRegistered();
        }

        Instant now = Instant.now(clock);
        OperationalAccount account = OperationalAccount.invite(
                UUID.randomUUID(), normalizedEmail, operationalRole, now, actorId);
        String rawToken = tokenGenerator.generate();
        OperationalAccountInvitation invitation = new OperationalAccountInvitation(
                UUID.randomUUID(),
                account.id(),
                tokenGenerator.digest(rawToken),
                now.plus(INVITATION_LIFETIME),
                actorId,
                category,
                normalizedPublicReason,
                normalizedInternalNote);

        try {
            operationalAccounts.saveAndFlush(account);
            invitations.saveAndFlush(invitation);
        } catch (DataIntegrityViolationException exception) {
            throw IdentityApiException.operationalEmailAlreadyRegistered();
        }

        events.publishEvent(new OperationalAccountInvited(
                account.id(),
                actorId,
                account.normalizedEmail(),
                account.role().name(),
                rawToken,
                invitation.expiresAt(),
                category.name(),
                normalizedPublicReason,
                normalizedInternalNote,
                now));
        return InvitationResult.from(account);
    }

    @Transactional
    ActivationResult activate(String rawToken, String password) {
        passwordPolicy.validate(password);
        Instant now = Instant.now(clock);
        OperationalAccountInvitation invitation = invitations.findByTokenDigestForUpdate(tokenGenerator.digest(rawToken))
                .filter(candidate -> candidate.isUsableAt(now))
                .orElseThrow(IdentityApiException::invalidOperationalActivationToken);
        OperationalAccount account = operationalAccounts.findByIdForUpdate(invitation.operationalAccountId())
                .orElseThrow(IdentityApiException::invalidOperationalActivationToken);

        account.activate(passwordEncoder.encode(password), now);
        invitation.markUsed(now);
        events.publishEvent(new OperationalAccountActivated(account.id(), now));
        return ActivationResult.from(account);
    }

    @Transactional(readOnly = true)
    java.util.List<OperationalAccountView> list() {
        return operationalAccounts.findAllByOrderByInvitedAtDesc().stream()
                .map(OperationalAccountView::from)
                .toList();
    }

    @Transactional
    void deactivate(
            UUID actorId,
            UUID accountId,
            String reasonCategory,
            String publicReason,
            String internalNote) {
        lockIdentityMutations();
        AdministrativeReasonCategory category = parseReasonCategory(reasonCategory);
        String normalizedPublicReason = requiredReason(publicReason);
        String normalizedInternalNote = optionalNote(internalNote);
        OperationalAccount account = operationalAccounts.findByIdForUpdate(accountId)
                .orElseThrow(IdentityApiException::operationalAccountNotFound);

        if (account.isActiveAdministrator()
                && operationalAccounts.countByRoleAndStatus(OperationalRole.ADMINISTRATOR, OperationalStatus.ACTIVE) <= 1) {
            throw IdentityApiException.lastAdministrator();
        }

        Instant now = Instant.now(clock);
        account.deactivate(now);
        invitations.findAllByOperationalAccountIdAndUsedAtIsNull(account.id()).forEach(invitation -> invitation.markUsed(now));
        sessions.revokeAll(account.id());
        events.publishEvent(new OperationalAccountDeactivated(
                account.id(),
                actorId,
                account.role().name(),
                category.name(),
                normalizedPublicReason,
                normalizedInternalNote,
                now));
    }

    private void lockIdentityMutations() {
        mutationGuards.findForUpdate().orElseThrow(() -> new IllegalStateException(
                "The identity mutation guard row is missing."));
    }

    private OperationalRole parseRole(String value) {
        try {
            return OperationalRole.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (RuntimeException exception) {
            throw IdentityApiException.invalidOperationalRole();
        }
    }

    private AdministrativeReasonCategory parseReasonCategory(String value) {
        try {
            return AdministrativeReasonCategory.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (RuntimeException exception) {
            throw IdentityApiException.invalidAdministrativeReason();
        }
    }

    private String requiredReason(String value) {
        if (value == null || value.isBlank()) {
            throw IdentityApiException.invalidAdministrativeReason();
        }
        return value.trim();
    }

    private String optionalNote(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    record InvitationResult(UUID id, String email, String role, String status) {

        static InvitationResult from(OperationalAccount account) {
            return new InvitationResult(account.id(), account.normalizedEmail(), account.role().name(), account.status().name());
        }
    }

    record ActivationResult(UUID id, String email, String role, String status) {

        static ActivationResult from(OperationalAccount account) {
            return new ActivationResult(account.id(), account.normalizedEmail(), account.role().name(), account.status().name());
        }
    }

    record OperationalAccountView(
            UUID id,
            String email,
            String role,
            String status,
            Instant invitedAt,
            Instant activatedAt,
            Instant deactivatedAt) {

        static OperationalAccountView from(OperationalAccount account) {
            return new OperationalAccountView(
                    account.id(),
                    account.normalizedEmail(),
                    account.role().name(),
                    account.status().name(),
                    account.invitedAt(),
                    account.activatedAt(),
                    account.deactivatedAt());
        }
    }
}
