package com.masters.socratesai.auth.dto;

import com.masters.socratesai.user.model.UserRole;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;

@Data
@Builder
public class AuthResponse {
    @ToString.Exclude private String token;
    private Long userId;
    private String email;
    private String fullName;
    private UserRole role;
}
