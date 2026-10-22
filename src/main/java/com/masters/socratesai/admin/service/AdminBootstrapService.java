package com.masters.socratesai.admin.service;

import com.masters.socratesai.admin.model.RoleChangeAudit;
import com.masters.socratesai.admin.repo.*;
import com.masters.socratesai.user.model.*;
import com.masters.socratesai.user.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@Service @RequiredArgsConstructor
public class AdminBootstrapService {
    private final AdminBootstrapProperties properties;
    private final AdministrationStateRepository administration;
    private final UserRepository users;
    private final RoleChangeAuditRepository audits;
    private final PasswordEncoder passwords;

    @Transactional
    public void initialize() {
        if (!properties.isEnabled()) return;
        var state = administration.lockState();
        if (state == null) throw new IllegalStateException("Administrative bootstrap schema unavailable");
        if (state.isBootstrapCompleted()) return;
        if (users.countByRole(UserRole.ADMIN) > 0) { state.setBootstrapCompleted(true); return; }
        String email = properties.getEmail();
        String password = properties.getPassword();
        String name = properties.getFullName();
        if (email == null || email.length() > 120 || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")
                || password == null || password.length() < 16 || password.isBlank()
                || password.getBytes(StandardCharsets.UTF_8).length > 72
                || name == null || name.isBlank() || name.length() > 120 || users.existsByEmail(email)) {
            throw new IllegalStateException("Invalid administrative bootstrap configuration");
        }
        var admin = users.saveAndFlush(User.builder().email(email).fullName(name).passwordHash(passwords.encode(password))
                .role(UserRole.ADMIN).settings(UserSettings.builder().build()).createdAt(LocalDateTime.now()).build());
        audits.save(RoleChangeAudit.builder().targetId(admin.getId()).newRole(UserRole.ADMIN).action("BOOTSTRAP")
                .reason("Operator-controlled initial administrator provisioning").build());
        state.setBootstrapCompleted(true);
    }
}
