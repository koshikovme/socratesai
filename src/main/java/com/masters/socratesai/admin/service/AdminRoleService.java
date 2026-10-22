package com.masters.socratesai.admin.service;

import com.masters.socratesai.admin.dto.*;
import com.masters.socratesai.admin.model.RoleChangeAudit;
import com.masters.socratesai.admin.repo.*;
import com.masters.socratesai.common.api.*;
import com.masters.socratesai.security.CurrentActor;
import com.masters.socratesai.user.model.UserRole;
import com.masters.socratesai.user.repo.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service @Validated @RequiredArgsConstructor
public class AdminRoleService {
    private final AdministrationStateRepository administration;
    private final UserRepository users;
    private final RoleChangeAuditRepository audits;
    private final CurrentActor actors;

    @Transactional
    public UserRoleResponse change(Long id, @Valid ChangeRoleRequest request) {
        if (administration.lockState() == null) throw new ApiException(ApiErrorCode.CONFLICT);
        var actor = actors.admin();
        var user = users.findById(id).orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND));
        var previous = user.getRole();
        if (previous != request.expectedRole()) throw new ApiException(ApiErrorCode.CONFLICT);
        if (previous == request.role()) return new UserRoleResponse(id, previous);
        if (previous == UserRole.ADMIN && request.role() != UserRole.ADMIN && users.countByRole(UserRole.ADMIN) <= 1) {
            throw new ApiException(ApiErrorCode.CONFLICT);
        }
        user.setRole(request.role());
        audits.save(RoleChangeAudit.builder().actorId(actor.id()).targetId(id).previousRole(previous)
                .newRole(request.role()).reason(request.reason()).action("ROLE_CHANGE").build());
        return new UserRoleResponse(id, user.getRole());
    }
}
