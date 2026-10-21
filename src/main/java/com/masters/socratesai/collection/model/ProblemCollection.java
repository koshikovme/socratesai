package com.masters.socratesai.collection.model;

import com.masters.socratesai.common.model.VersionedEntity;
import com.masters.socratesai.common.model.ContentStatus;
import com.masters.socratesai.common.model.ContentVisibility;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "collections")
public class ProblemCollection extends VersionedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long ownerId;

    @Column(nullable = false, length = 180)
    private String title;

    @Builder.Default
    @Column(nullable = false, columnDefinition = "TEXT")
    private String description = "";

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContentStatus status = ContentStatus.DRAFT;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContentVisibility visibility = ContentVisibility.AUTHENTICATED;
}
