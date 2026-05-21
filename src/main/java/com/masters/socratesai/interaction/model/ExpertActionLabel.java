package com.masters.socratesai.interaction.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "expert_action_labels",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_expert_label_interaction_reviewer",
                columnNames = {"interaction_id", "reviewer_id"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpertActionLabel {

    @Id
    @Column(name = "label_id", nullable = false, updatable = false)
    private UUID labelId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "interaction_id", nullable = false)
    private InteractionLog interaction;

    @Column(name = "reviewer_id", nullable = false)
    private Long reviewerId;

    @Column(name = "target_feedback_action", nullable = false, length = 50)
    private String targetFeedbackAction;

    @Column(name = "confidence")
    private Integer confidence;

    @Column(name = "rationale", columnDefinition = "TEXT")
    private String rationale;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (labelId == null) labelId = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
