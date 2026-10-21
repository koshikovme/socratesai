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
@Table(name = "problem_language_configs")
public class ProblemLanguageConfig extends VersionedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "problem_version_id", nullable = false)
    private ProblemVersion problemVersion;

    @Column(nullable = false, length = 30)
    private String languageKey;

    @Column(nullable = false, length = 100)
    private String runtimeKey;

    @Column(nullable = false, length = 100)
    private String runtimeVersion;

    @Builder.Default
    @Column(nullable = false)
    private Boolean runtimeVerified = false;

    @Column(columnDefinition = "TEXT")
    private String starterCode;

    @Column(length = 120)
    private String entryPoint;

    @Column(nullable = false)
    private Integer timeLimitMs;

    @Column(nullable = false)
    private Integer wallTimeLimitMs;

    @Column(nullable = false)
    private Integer memoryLimitKb;

    @Column(nullable = false)
    private Integer outputLimitBytes;
}
