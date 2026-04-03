package com.masters.socratesai.user.service;

import com.masters.socratesai.security.SecurityUserDetails;
import com.masters.socratesai.user.dto.UpdateProfileRequest;
import com.masters.socratesai.user.dto.UpdateSettingsRequest;
import com.masters.socratesai.user.dto.UserProfileResponse;
import com.masters.socratesai.user.model.User;
import com.masters.socratesai.user.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.crossstore.ChangeSetPersister;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public UserProfileResponse getMyProfile(SecurityUserDetails currentUser) throws ChangeSetPersister.NotFoundException {
        User user = getUserEntity(currentUser.getId());
        return mapToResponse(user);
    }

    @Transactional
    public UserProfileResponse updateProfile(SecurityUserDetails currentUser, UpdateProfileRequest request) throws ChangeSetPersister.NotFoundException {
        User user = getUserEntity(currentUser.getId());

        if (request.getFullName() != null) user.setFullName(request.getFullName());
        if (request.getUniversity() != null) user.setUniversity(request.getUniversity());
        if (request.getGroupName() != null) user.setGroupName(request.getGroupName());

        return mapToResponse(user);
    }

    @Transactional
    public UserProfileResponse updateSettings(SecurityUserDetails currentUser, UpdateSettingsRequest request) throws ChangeSetPersister.NotFoundException {
        User user = getUserEntity(currentUser.getId());

        if (request.getDarkMode() != null) user.getSettings().setDarkMode(request.getDarkMode());
        if (request.getEmailNotifications() != null) user.getSettings().setEmailNotifications(request.getEmailNotifications());
        if (request.getPreferredLanguage() != null) user.getSettings().setPreferredLanguage(request.getPreferredLanguage());

        return mapToResponse(user);
    }

    private User getUserEntity(Long id) throws ChangeSetPersister.NotFoundException {
        return userRepository.findById(id)
                .orElseThrow(ChangeSetPersister.NotFoundException::new);
    }

    private UserProfileResponse mapToResponse(User user) {
        return UserProfileResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole())
                .university(user.getUniversity())
                .groupName(user.getGroupName())
                .darkMode(user.getSettings().getDarkMode())
                .emailNotifications(user.getSettings().getEmailNotifications())
                .preferredLanguage(user.getSettings().getPreferredLanguage())
                .build();
    }
}