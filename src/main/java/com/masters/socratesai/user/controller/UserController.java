package com.masters.socratesai.user.controller;

import com.masters.socratesai.security.SecurityUserDetails;
import com.masters.socratesai.user.dto.StudentProgressResponse;
import com.masters.socratesai.user.dto.UpdateProfileRequest;
import com.masters.socratesai.user.dto.UpdateSettingsRequest;
import com.masters.socratesai.user.dto.UserProfileResponse;
import com.masters.socratesai.user.service.UserService;
import com.masters.socratesai.user.service.AccountProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.crossstore.ChangeSetPersister;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AccountProfileService profileService;

    @GetMapping
    public UserProfileResponse getMyProfile(@AuthenticationPrincipal SecurityUserDetails currentUser) throws ChangeSetPersister.NotFoundException {
        return userService.getMyProfile(currentUser);
    }

    @GetMapping("/progress")
    public StudentProgressResponse getMyProgress(@AuthenticationPrincipal SecurityUserDetails currentUser) throws ChangeSetPersister.NotFoundException {
        return userService.getMyProgress(currentUser);
    }

    @PutMapping("/profile")
    public UserProfileResponse updateProfile(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody UpdateProfileRequest request
    ) throws ChangeSetPersister.NotFoundException {
        return profileService.update(currentUser, request);
    }

    @PutMapping("/settings")
    public UserProfileResponse updateSettings(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody UpdateSettingsRequest request
    ) throws ChangeSetPersister.NotFoundException {
        return profileService.settings(currentUser, request);
    }
}
