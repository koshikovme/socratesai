package com.masters.socratesai.admin.dto;

import com.masters.socratesai.user.model.UserRole;
import jakarta.validation.constraints.*;

public record ChangeRoleRequest(@NotNull UserRole expectedRole, @NotNull UserRole role,
                                @NotBlank @Size(max = 500) String reason) {}
