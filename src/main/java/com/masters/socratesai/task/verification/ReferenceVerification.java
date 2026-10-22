package com.masters.socratesai.task.verification;

import com.masters.socratesai.common.model.VersionedEntity;
import com.masters.socratesai.execution.model.*;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity @Table(name = "problem_reference_verifications")
@Getter @Setter @SuperBuilder @NoArgsConstructor @AllArgsConstructor
public class ReferenceVerification extends VersionedEntity {
    @Id private UUID id;
    @Column(nullable = false, updatable = false) private Long problemVersionId;
    @Column(nullable = false, updatable = false) private Long requestedBy;
    @Column(nullable = false, updatable = false, length = 64) private String draftHash;
    @Enumerated(EnumType.STRING) private ExecutionStatus status;
    @Enumerated(EnumType.STRING) private ExecutionVerdict verdict;
    private int testsTotal;
    private int testsPassed;
    @Column(length = 80) private String failureCode;
    private OffsetDateTime deadlineAt;
    private OffsetDateTime validUntil;
    private OffsetDateTime finishedAt;
}
