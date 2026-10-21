package com.masters.socratesai.execution.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

/** Immutable execution input shared by graded submissions and ephemeral runs. */
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Embeddable
public class ExecutionSnapshot {

    @Column(nullable = false, updatable = false)
    private Long problemId;

    @Column(nullable = false, updatable = false)
    private Long problemVersionId;

    @Column(nullable = false, updatable = false, length = 30)
    private String languageKey;

    @Column(nullable = false, updatable = false, length = 100)
    private String runtimeKey;

    @Column(nullable = false, updatable = false, length = 100)
    private String runtimeVersion;

    @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
    private String sourceCode;

    @Column(nullable = false, updatable = false, length = 64)
    private String sourceHash;

    @Column(nullable = false, updatable = false)
    private Integer timeLimitMs;

    @Column(nullable = false, updatable = false)
    private Integer wallTimeLimitMs;

    @Column(nullable = false, updatable = false)
    private Integer memoryLimitKb;

    @Column(nullable = false, updatable = false)
    private Integer outputLimitBytes;
}
