package io.github.klisman99.collectorsauctionplatform.identity;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

class InitialAdministratorServiceTests {

    @Test
    void refusesToTreatAnInactiveConfiguredOperationalAccountAsTheBootstrapAdministrator() {
        IdentityMutationGuardRepository mutationGuards = mock(IdentityMutationGuardRepository.class);
        OperationalAccountRepository operationalAccounts = mock(OperationalAccountRepository.class);
        RegularAccountRepository regularAccounts = mock(RegularAccountRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        when(mutationGuards.findForUpdate()).thenReturn(Optional.of(mock(IdentityMutationGuard.class)));
        when(operationalAccounts.findByNormalizedEmailForUpdate("bootstrap@example.com"))
                .thenReturn(Optional.of(OperationalAccount.invite(
                        UUID.randomUUID(),
                        "bootstrap@example.com",
                        OperationalRole.MODERATOR,
                        "unused-password-hash",
                        java.time.Instant.now(),
                        UUID.randomUUID())));

        InitialAdministratorService service = new InitialAdministratorService(
                mutationGuards,
                operationalAccounts,
                regularAccounts,
                passwordEncoder,
                new PasswordPolicy(),
                events,
                Clock.systemUTC());

        assertThatThrownBy(() -> service.ensureConfiguredAdministrator("bootstrap@example.com", "a bootstrap password"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must belong to an active administrator");
        verify(operationalAccounts, never()).saveAndFlush(any(OperationalAccount.class));
    }
}
