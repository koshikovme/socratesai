package com.masters.socratesai.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.ToString;

@Data
public class LoginRequest {
    @Email
    @NotBlank
    private String email;

    @NotBlank
    @ToString.Exclude
    private String password;
}
