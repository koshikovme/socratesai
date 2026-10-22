package com.masters.socratesai.execution.model;

import com.masters.socratesai.common.model.VersionedEntity;
import com.masters.socratesai.task.model.ProblemTestCase;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "execution_case_results")
public class ExecutionCaseResult extends VersionedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false, updatable = false)
    private ExecutionJob job;

    @Column(nullable = false, updatable = false)
    private Long problemVersionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "test_case_id", nullable = false, updatable = false)
    private ProblemTestCase testCase;

    @Builder.Default
    @Column(nullable = false, updatable = false)
    private Integer generation = 0;

    /** Stable checkpoints use generation zero; job.generation fences lease owners. */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DispatchState dispatchState = DispatchState.PENDING;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CaseExecutionStatus status = CaseExecutionStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private ExecutionVerdict verdict;

    @Column(length = 200)
    private String providerToken;

    private java.time.OffsetDateTime providerDeadlineAt;

    @Column(columnDefinition = "TEXT")
    private String compileOutput;

    @Column(columnDefinition = "TEXT")
    private String stdout;

    @Column(columnDefinition = "TEXT")
    private String stderr;

    private Integer exitCode;

    private Integer timeConsumedMs;

    private Long memoryConsumedBytes;
}
