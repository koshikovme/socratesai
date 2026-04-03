package com.masters.socratesai.user.dto;

import com.masters.socratesai.user.model.UserRole;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserProfileResponse {
    private Long id;
    private String email;
    private String fullName;
    private UserRole role;
    private String university;
    private String groupName;
    private Boolean darkMode;
    private Boolean emailNotifications;
    private String preferredLanguage;
}