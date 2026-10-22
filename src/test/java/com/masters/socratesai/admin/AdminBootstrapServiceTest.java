package com.masters.socratesai.admin;

import com.masters.socratesai.admin.model.*;
import com.masters.socratesai.admin.repo.*;
import com.masters.socratesai.admin.service.*;
import com.masters.socratesai.user.model.*;
import com.masters.socratesai.user.repo.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminBootstrapServiceTest {
    private final AdminBootstrapProperties properties = new AdminBootstrapProperties();
    private final AdministrationStateRepository stateRepository = mock(AdministrationStateRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final RoleChangeAuditRepository audits = mock(RoleChangeAuditRepository.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final AdminBootstrapService service = new AdminBootstrapService(properties, stateRepository, users, audits, passwords);
    private static final String SECRET = "external-only-bootstrap-fixture-secret";

    @Test void disabledBootstrapHasNoDatabaseSideEffectsOrDefaultCredentials() {
        service.initialize();
        assertThat(properties.getPassword()).isNull();
        verifyNoInteractions(stateRepository, users, audits, passwords);
    }

    @Test void bootstrapCreatesNewAdminAndAuditExactlyOnceWithoutPromotingExistingAccount() {
        var state = configuration();
        when(passwords.encode(SECRET)).thenReturn("encoded-fixture-hash");
        when(users.saveAndFlush(any())).thenAnswer(call -> { User user = call.getArgument(0); user.setId(5L); return user; });
        service.initialize();
        var account = ArgumentCaptor.forClass(User.class);
        verify(users).saveAndFlush(account.capture());
        assertThat(account.getValue().getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(account.getValue().getPasswordHash()).isEqualTo("encoded-fixture-hash");
        var audit = ArgumentCaptor.forClass(RoleChangeAudit.class);
        verify(audits).save(audit.capture());
        assertThat(audit.getValue().getActorId()).isNull();
        assertThat(audit.getValue().getTargetId()).isEqualTo(5L);
        assertThat(audit.getValue().getAction()).isEqualTo("BOOTSTRAP");
        assertThat(audit.getValue().getReason()).doesNotContain(SECRET);
        assertThat(state.isBootstrapCompleted()).isTrue();
        properties.setPassword("a different password must not reset the account");
        service.initialize();
        verify(users, times(1)).saveAndFlush(any());
        verify(audits, times(1)).save(any());
    }

    @Test void missingOrUnsafeCredentialsFailClosedWithFixedMessages() {
        Stream.<Consumer<AdminBootstrapProperties>>of(p -> p.setPassword(null), p -> p.setPassword("short"),
                p -> p.setPassword("я".repeat(40)), p -> p.setEmail("broken-email"), p -> p.setFullName(" "))
                .forEach(change -> {
                    configuration(); change.accept(properties);
                    assertThatThrownBy(service::initialize).isInstanceOf(IllegalStateException.class).hasNoCause().hasMessageNotContaining(SECRET);
                });
        verify(users, never()).saveAndFlush(any());
        verifyNoInteractions(passwords, audits);
    }

    @Test void preexistingEmailCannotBeTakenOverAndExistingAdminDisablesBootstrap() {
        var state = configuration();
        when(users.existsByEmail(properties.getEmail())).thenReturn(true);
        assertThatThrownBy(service::initialize).isInstanceOf(IllegalStateException.class).hasMessageNotContaining(SECRET);
        verify(users, never()).saveAndFlush(any());
        when(users.countByRole(UserRole.ADMIN)).thenReturn(1L);
        service.initialize();
        assertThat(state.isBootstrapCompleted()).isTrue();
        verifyNoInteractions(passwords, audits);
    }

    private AdministrationState configuration() {
        properties.setEnabled(true); properties.setEmail("bootstrap@example.test"); properties.setFullName("Operator fixture"); properties.setPassword(SECRET);
        var state = new AdministrationState(); state.setId(1);
        when(stateRepository.lockState()).thenReturn(state);
        return state;
    }
}
