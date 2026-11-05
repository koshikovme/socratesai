package com.masters.socratesai.user.dto;

import com.masters.socratesai.user.model.UserRole;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.masters.socratesai.auth.dto.AuthResponse;
import java.time.LocalDateTime;

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
    private LocalDateTime createdAt;
    private String profileRevision;
    @JsonInclude(JsonInclude.Include.NON_NULL) @ToString.Exclude
    private AuthResponse authentication;
}
