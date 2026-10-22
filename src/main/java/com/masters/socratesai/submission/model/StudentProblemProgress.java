package com.masters.socratesai.submission.model;

import com.masters.socratesai.common.model.VersionedEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity @Table(name = "student_problem_progress")
@Getter @Setter @SuperBuilder @NoArgsConstructor @AllArgsConstructor
public class StudentProblemProgress extends VersionedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, updatable = false) private Long studentId;
    @Column(nullable = false, updatable = false) private Long problemId;
    @Builder.Default @Column(nullable = false) private Integer formalAttempts = 0;
    private UUID acceptedSubmissionId;
    private OffsetDateTime acceptedAt;
    @Column(nullable = false) private OffsetDateTime lastActivityAt;
    @Builder.Default @Column(nullable = false) private Integer bestTestsPassed = 0;
    @Builder.Default @Column(nullable = false) private Integer lastTestsFailed = 0;
}
