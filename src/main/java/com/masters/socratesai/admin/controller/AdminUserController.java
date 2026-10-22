package com.masters.socratesai.admin.controller;

import com.masters.socratesai.admin.dto.*;
import com.masters.socratesai.admin.service.AdminRoleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/admin/users") @RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {
    private final AdminRoleService service;

    @PatchMapping("/{id}/role")
    public UserRoleResponse change(@PathVariable @Positive Long id, @RequestBody @Valid ChangeRoleRequest request) {
        return service.change(id, request);
    }
}
