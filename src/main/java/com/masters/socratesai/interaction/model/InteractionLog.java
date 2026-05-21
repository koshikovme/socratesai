package com.masters.socratesai.interaction.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "interaction_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InteractionLog {

    @Id
    @Column(name = "interaction_id", nullable = false, updatable = false)
    private UUID interactionId;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "attempt_no", nullable = false)
    private Integer attemptNo;

    @Column(name = "error_type", length = 50)
    private String errorType;

    @Column(name = "severity", length = 20)
    private String severity;

    @Column(name = "same_error_count", nullable = false)
    private Integer sameErrorCount;

    @Column(name = "total_errors_seen", nullable = false)
    private Integer totalErrorsSeen;

    @Column(name = "compile_success")
    private Boolean compileSuccess;

    @Column(name = "tests_passed")
    private Integer testsPassed;

    @Column(name = "tests_failed")
    private Integer testsFailed;

    @Column(name = "last_feedback_action", length = 50)
    private String lastFeedbackAction;

    @Column(name = "last_feedback_success")
    private Boolean lastFeedbackSuccess;

    @Column(name = "total_feedback_count_in_session")
    private Integer totalFeedbackCountInSession;

    @Column(name = "feedback_action", nullable = false, length = 50)
    private String feedbackAction;

    @Column(name = "feedback_text", columnDefinition = "TEXT")
    private String feedbackText;

    @Column(name = "feedback_source", nullable = false, length = 30)
    private String feedbackSource;

    @Column(name = "policy_version", length = 50)
    private String policyVersion;

    @Column(name = "feedback_version", length = 50)
    private String feedbackVersion;

    @Column(name = "analysis_time_ms", nullable = false)
    private Integer analysisTimeMs;

    @Column(name = "policy_time_ms", nullable = false)
    private Integer policyTimeMs;

    @Column(name = "feedback_time_ms", nullable = false)
    private Integer feedbackTimeMs;

    @Column(name = "total_latency_ms", nullable = false)
    private Integer totalLatencyMs;

    @Column(name = "fixed_after_ms")
    private Integer fixedAfterMs;

    @Column(name = "resolved_after_feedback")
    private Boolean resolvedAfterFeedback;

    @Column(name = "feedback_helpful")
    private Boolean feedbackHelpful;

    @Column(name = "feedback_rating")
    private Integer feedbackRating;

    @Column(name = "student_comment", columnDefinition = "TEXT")
    private String studentComment;

    @Column(name = "repeated_same_error_after_feedback")
    private Boolean repeatedSameErrorAfterFeedback;

    @Column(name = "direct_solution_violation", nullable = false)
    private Boolean directSolutionViolation;

    @Column(name = "code_hash", length = 128)
    private String codeHash;

    @Column(name = "code_lines")
    private Integer codeLines;

    @Column(name = "has_suspicious_region", nullable = false)
    private Boolean hasSuspiciousRegion;

    @Column(name = "suspicious_region", length = 255)
    private String suspiciousRegion;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (interactionId == null) interactionId = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (sameErrorCount == null) sameErrorCount = 0;
        if (totalErrorsSeen == null) totalErrorsSeen = 0;
        if (totalFeedbackCountInSession == null) totalFeedbackCountInSession = 0;
        if (analysisTimeMs == null) analysisTimeMs = 0;
        if (policyTimeMs == null) policyTimeMs = 0;
        if (feedbackTimeMs == null) feedbackTimeMs = 0;
        if (totalLatencyMs == null) totalLatencyMs = 0;
        if (feedbackSource == null) feedbackSource = "template";
        if (directSolutionViolation == null) directSolutionViolation = false;
        if (hasSuspiciousRegion == null) hasSuspiciousRegion = false;
    }
}
