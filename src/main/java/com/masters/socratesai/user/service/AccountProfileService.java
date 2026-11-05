package com.masters.socratesai.user.service;

import com.masters.socratesai.auth.dto.AuthResponse;
import com.masters.socratesai.common.api.*;
import com.masters.socratesai.security.*;
import com.masters.socratesai.user.dto.*;
import com.masters.socratesai.user.model.User;
import com.masters.socratesai.user.repo.UserRepository;
import jakarta.persistence.EntityManager;
import java.util.Locale;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class AccountProfileService {
    private final UserRepository users;
    private final EntityManager entities;
    private final PasswordEncoder passwords;
    private final JwtService jwt;

    @Transactional
    public UserProfileResponse update(SecurityUserDetails actor, UpdateProfileRequest request) {
        User user = locked(actor);
        if (!Objects.equals(request.getProfileRevision(), UserProfileMapper.revision(user))) throw new ApiException(ApiErrorCode.CONFLICT);
        String email = request.getEmail().strip().toLowerCase(Locale.ROOT);
        boolean changedEmail = !email.equals(user.getEmail());
        if (changedEmail) {
            if (request.getCurrentPassword() == null || !passwords.matches(request.getCurrentPassword(), user.getPasswordHash())) {
                throw new ApiException(ApiErrorCode.PROFILE_REAUTHENTICATION_REQUIRED);
            }
            if (users.existsByEmailIgnoreCaseAndIdNot(email, user.getId())) throw new ApiException(ApiErrorCode.EMAIL_UNAVAILABLE);
            user.setEmail(email);
        }
        user.setFullName(request.getFullName().strip());
        user.setUniversity(optional(request.getUniversity()));
        user.setGroupName(optional(request.getGroupName()));
        users.flush(); // Unique-email races become a safe 409 before issuing a replacement token.
        var response = UserProfileMapper.response(user);
        if (changedEmail) response.setAuthentication(AuthResponse.builder().token(jwt.generateToken(new SecurityUserDetails(user)))
                .userId(user.getId()).email(user.getEmail()).fullName(user.getFullName()).role(user.getRole()).build());
        return response;
    }

    @Transactional
    public UserProfileResponse settings(SecurityUserDetails actor, UpdateSettingsRequest request) {
        User user = locked(actor);
        if (request.getDarkMode() != null) user.getSettings().setDarkMode(request.getDarkMode());
        if (request.getEmailNotifications() != null) user.getSettings().setEmailNotifications(request.getEmailNotifications());
        if (request.getPreferredLanguage() != null) user.getSettings().setPreferredLanguage(request.getPreferredLanguage());
        return UserProfileMapper.response(user);
    }

    private User locked(SecurityUserDetails actor) {
        User user = users.lockById(actor.getId()).orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND));
        // Authentication may already have loaded this entity before the lock was obtained.
        entities.refresh(user);
        return user;
    }

    private String optional(String value) { return value == null || value.isBlank() ? null : value.strip(); }
}
