package com.masters.socratesai.collection.model;

import com.masters.socratesai.common.model.VersionedEntity;
import com.masters.socratesai.task.model.ProblemVersion;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "collection_problems")
public class CollectionProblem extends VersionedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "collection_id", nullable = false, updatable = false)
    private ProblemCollection collection;

    @Column(nullable = false)
    private Long problemId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "problem_version_id", nullable = false)
    private ProblemVersion problemVersion;

    @Column(nullable = false)
    private Integer position;
}
