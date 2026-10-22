package com.masters.socratesai.auth.dto;

import com.masters.socratesai.user.model.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;

@Data
public class RegisterRequest {
    @Email
    @NotBlank
    @Size(max = 120)
    private String email;

    @NotBlank
    @Size(min = 6, max = 100)
    @ToString.Exclude
    private String password;

    @NotBlank
    @Size(max = 120)
    private String fullName;

    private UserRole role;

    @JsonIgnore
    @AssertTrue(message = "public registration only permits STUDENT")
    public boolean isPublicStudentRole() { return role == null || role == UserRole.STUDENT; }
}
