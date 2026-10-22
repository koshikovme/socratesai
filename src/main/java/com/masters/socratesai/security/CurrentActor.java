package com.masters.socratesai.security;

import com.masters.socratesai.common.api.ApiErrorCode;
import com.masters.socratesai.common.api.ApiException;
import com.masters.socratesai.user.model.UserRole;
import com.masters.socratesai.user.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@RequiredArgsConstructor
public class CurrentActor {
    private final UserRepository users;

    public Actor require() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof SecurityUserDetails principal)) {
            throw new ApiException(ApiErrorCode.AUTHENTICATION_REQUIRED);
        }
        return users.findById(principal.getId()).map(user -> new Actor(user.getId(), user.getRole()))
                .orElseThrow(() -> new ApiException(ApiErrorCode.AUTHENTICATION_REQUIRED));
    }

    public Actor staff() {
        Actor actor = require();
        if (actor.role() == UserRole.STUDENT) throw new ApiException(ApiErrorCode.FORBIDDEN);
        return actor;
    }

    public Actor admin() {
        Actor actor = require();
        if (!actor.admin()) throw new ApiException(ApiErrorCode.FORBIDDEN);
        return actor;
    }

    public void owned(Actor actor, Long ownerId) {
        if (!actor.admin() && !Objects.equals(actor.id(), ownerId)) throw new ApiException(ApiErrorCode.NOT_FOUND);
    }

    public record Actor(Long id, UserRole role) {
        public boolean admin() { return role == UserRole.ADMIN; }
    }
}
