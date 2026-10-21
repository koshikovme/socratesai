package com.masters.socratesai.submission.model;

import com.masters.socratesai.common.model.VersionedEntity;
import com.masters.socratesai.execution.model.ExecutionSnapshot;
import com.masters.socratesai.execution.model.ExecutionStatus;
import com.masters.socratesai.execution.model.ExecutionVerdict;
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
@Table(name = "submissions")
public class Submission extends VersionedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private Long studentId;

    @Embedded
    private ExecutionSnapshot snapshot;

    @Column(updatable = false)
    private Long collectionId;

    @Column(updatable = false)
    private UUID sessionId;

    @Column(nullable = false, updatable = false)
    private Integer attemptNumber;

    @Column(nullable = false, updatable = false)
    private UUID idempotencyKey;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExecutionStatus status = ExecutionStatus.QUEUED;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private ExecutionVerdict verdict;

    @Builder.Default
    @Column(nullable = false)
    private Integer testsTotal = 0;

    @Builder.Default
    @Column(nullable = false)
    private Integer testsPassed = 0;

    @Builder.Default
    @Column(nullable = false)
    private Integer testsFailed = 0;

    private Integer timeConsumedMs;

    private Long memoryConsumedBytes;

    private OffsetDateTime finishedAt;
}
