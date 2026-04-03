package com.masters.socratesai.session.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "student_task_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentTaskSession {

    @Id
    @Column(name = "session_id", nullable = false, updatable = false)
    private UUID sessionId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "task_id", nullable = false, length = 100)
    private Long taskId;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "ended_at")
    private OffsetDateTime endedAt;

    @Column(name = "final_status", length = 30)
    private String finalStatus;

    @Column(name = "total_attempts", nullable = false)
    private Integer totalAttempts = 0;

    @Column(name = "total_feedback_count", nullable = false)
    private Integer totalFeedbackCount = 0;

    @PrePersist
    public void prePersist() {
        if (sessionId == null) sessionId = UUID.randomUUID();
        if (startedAt == null) startedAt = OffsetDateTime.now();
        if (totalAttempts == null) totalAttempts = 0;
        if (totalFeedbackCount == null) totalFeedbackCount = 0;
    }
}