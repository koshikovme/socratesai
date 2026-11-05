package com.masters.socratesai.auth.service;

import com.masters.socratesai.auth.dto.AuthResponse;
import com.masters.socratesai.auth.dto.LoginRequest;
import com.masters.socratesai.auth.dto.RegisterRequest;
import com.masters.socratesai.security.JwtService;
import com.masters.socratesai.security.SecurityUserDetails;
import com.masters.socratesai.user.model.User;
import com.masters.socratesai.user.model.UserRole;
import com.masters.socratesai.user.model.UserSettings;
import com.masters.socratesai.user.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import com.masters.socratesai.common.api.ApiException;
import com.masters.socratesai.common.api.ApiErrorCode;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.authentication.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (request.getRole() != null && request.getRole() != UserRole.STUDENT) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST);
        }
        String email = request.getEmail().strip().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ApiException(ApiErrorCode.CONFLICT);
        }

        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName().strip())
                .role(UserRole.STUDENT)
                .settings(UserSettings.builder().build())
                .createdAt(LocalDateTime.now())
                .build();

        userRepository.save(user);

        SecurityUserDetails userDetails = new SecurityUserDetails(user);
        String token = jwtService.generateToken(userDetails);

        return AuthResponse.builder()
                .token(token)
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole())
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        String email = request.getEmail().strip().toLowerCase(Locale.ROOT);
        var authenticated = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        email,
                        request.getPassword()
                )
        );

        if (!(authenticated.getPrincipal() instanceof SecurityUserDetails principal)) throw new ApiException(ApiErrorCode.AUTHENTICATION_REQUIRED);
        // An email can change/reappear between authentication and this read. Bind to its verified account ID.
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ApiException(ApiErrorCode.AUTHENTICATION_REQUIRED));

        String token = jwtService.generateToken(new SecurityUserDetails(user));

        return AuthResponse.builder()
                .token(token)
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole())
                .build();
    }
}
