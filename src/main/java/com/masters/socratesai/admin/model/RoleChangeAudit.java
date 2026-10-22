package com.masters.socratesai.admin.model;

import com.masters.socratesai.user.model.UserRole;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Immutable;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Entity @Table(name = "role_change_audit") @Immutable
@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class RoleChangeAudit {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private Long actorId;
    private Long targetId;
    @Enumerated(EnumType.STRING) private UserRole previousRole;
    @Enumerated(EnumType.STRING) private UserRole newRole;
    @Column(length = 500) private String reason;
    private String action;
    private OffsetDateTime createdAt;

    @PrePersist void timestamp() { if (createdAt == null) createdAt = OffsetDateTime.now(ZoneOffset.UTC); }
}
