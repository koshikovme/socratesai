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
import org.apache.coyote.BadRequestException;
import org.springframework.security.authentication.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthResponse register(RegisterRequest request) throws BadRequestException {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email already exists");
        }

        UserRole role = request.getRole() == null ? UserRole.STUDENT : request.getRole();

        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .role(role)
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

    public AuthResponse login(LoginRequest request) throws BadRequestException {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadRequestException("Invalid credentials"));

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