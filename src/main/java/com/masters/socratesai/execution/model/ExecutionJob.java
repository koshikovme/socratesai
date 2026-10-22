package com.masters.socratesai.execution.model;

import com.masters.socratesai.common.model.VersionedEntity;
import java.time.OffsetDateTime;
import java.util.UUID;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "execution_jobs")
public class ExecutionJob extends VersionedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private Long problemVersionId;

    @Column(updatable = false)
    private UUID submissionId;

    @Column(updatable = false)
    private UUID runId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 30)
    private ExecutionMode mode;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExecutionStatus status = ExecutionStatus.QUEUED;

    @Builder.Default
    @Column(nullable = false)
    private Integer attemptCount = 0;

    @Builder.Default
    @Column(nullable = false)
    private Integer maxAttempts = 3;

    @Builder.Default
    @Column(nullable = false)
    private Integer generation = 0;

    @Column(length = 120)
    private String leaseOwner;

    private UUID leaseToken;

    private OffsetDateTime leaseExpiresAt;

    @Column(nullable = false)
    private OffsetDateTime nextAttemptAt;

    @Column(nullable = false)
    private OffsetDateTime deadlineAt;

    private OffsetDateTime startedAt;

    private OffsetDateTime finishedAt;

    @Column(length = 80)
    private String lastErrorCode;

    @Builder.Default
    @Column(nullable = false)
    private Boolean retryAllowed = false;

    @PrePersist
    private void initializeSchedule() {
        if (nextAttemptAt == null) nextAttemptAt = getCreatedAt();
    }
}
