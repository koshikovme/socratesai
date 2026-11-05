package com.masters.socratesai.auth.service;

import com.masters.socratesai.auth.dto.LoginRequest;
import com.masters.socratesai.security.*;
import com.masters.socratesai.user.model.*;
import com.masters.socratesai.user.repo.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.*;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthIdentityTest {
    @Test void emailReassignmentDuringLoginCannotSwitchTheAuthenticatedAccount() {
        var users = mock(UserRepository.class); var manager = mock(AuthenticationManager.class); var jwt = mock(JwtService.class);
        var original = User.builder().id(7L).email("before@example.test").fullName("Original").passwordHash("hash").role(UserRole.STUDENT).build();
        var principal = new SecurityUserDetails(original);
        when(manager.authenticate(any())).thenReturn(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        // The verified owner changed their email after password verification. Another user may now own the old email.
        original.setEmail("after@example.test"); when(users.findById(7L)).thenReturn(Optional.of(original));
        when(jwt.generateToken(any())).thenReturn("synthetic-token");
        var request = new LoginRequest(); request.setEmail("BEFORE@example.test"); request.setPassword("fixture-password");
        var response = new AuthService(users, mock(PasswordEncoder.class), manager, jwt).login(request);
        assertThat(response.getUserId()).isEqualTo(7L);
        assertThat(response.getEmail()).isEqualTo("after@example.test");
        verify(users, never()).findByEmailIgnoreCase(anyString());
        verify(jwt).generateToken(argThat(identity -> identity.getId().equals(7L) && identity.getUsername().equals("after@example.test")));
    }
}
