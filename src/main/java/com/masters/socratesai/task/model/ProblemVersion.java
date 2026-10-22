package com.masters.socratesai.task.model;

import com.masters.socratesai.common.model.VersionedEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "problem_versions")
public class ProblemVersion extends VersionedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "problem_id", nullable = false, updatable = false)
    private Task problem;

    @Column(nullable = false, updatable = false)
    private Integer versionNumber;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProblemVersionStatus status = ProblemVersionStatus.DRAFT;

    @Column(nullable = false, length = 180)
    private String title;

    @Column(nullable = false, length = 120)
    private String topic;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskDifficulty difficulty;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String statement;

    @Builder.Default
    @Column(nullable = false, columnDefinition = "TEXT")
    private String inputFormat = "";

    @Builder.Default
    @Column(nullable = false, columnDefinition = "TEXT")
    private String outputFormat = "";

    @Builder.Default
    @Column(nullable = false, columnDefinition = "TEXT")
    private String constraintsText = "";

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "comparison_mode", nullable = false, length = 40)
    private OutputComparison comparisonMode = OutputComparison.IGNORE_TRAILING_LINE_ENDINGS;

    @Builder.Default
    @Column(nullable = false, updatable = false)
    private Boolean legacySnapshot = false;

    private java.time.OffsetDateTime publishedAt;

    private java.util.UUID referenceVerificationId;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private com.masters.socratesai.common.model.ContentVisibility visibility = com.masters.socratesai.common.model.ContentVisibility.AUTHENTICATED;
}
