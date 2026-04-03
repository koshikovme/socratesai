package com.masters.socratesai.user.controller;

import com.masters.socratesai.security.SecurityUserDetails;
import com.masters.socratesai.user.dto.UpdateProfileRequest;
import com.masters.socratesai.user.dto.UpdateSettingsRequest;
import com.masters.socratesai.user.dto.UserProfileResponse;
import com.masters.socratesai.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.crossstore.ChangeSetPersister;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public UserProfileResponse getMyProfile(@AuthenticationPrincipal SecurityUserDetails currentUser) throws ChangeSetPersister.NotFoundException {
        return userService.getMyProfile(currentUser);
    }

    @PutMapping("/profile")
    public UserProfileResponse updateProfile(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @RequestBody UpdateProfileRequest request
    ) throws ChangeSetPersister.NotFoundException {
        return userService.updateProfile(currentUser, request);
    }

    @PutMapping("/settings")
    public UserProfileResponse updateSettings(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @RequestBody UpdateSettingsRequest request
    ) throws ChangeSetPersister.NotFoundException {
        return userService.updateSettings(currentUser, request);
    }
}