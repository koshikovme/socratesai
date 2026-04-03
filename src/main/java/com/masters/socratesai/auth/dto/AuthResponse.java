package com.masters.socratesai.auth.dto;

import com.masters.socratesai.user.model.UserRole;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthResponse {
    private String token;
    private Long userId;
    private String email;
    private String fullName;
    private UserRole role;
}