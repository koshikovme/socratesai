package com.masters.socratesai.task.model;

import com.masters.socratesai.common.model.ContentStatus;
import com.masters.socratesai.common.model.ContentVisibility;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/** The native Problem aggregate retains its Task/table identity for existing API callers. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "tasks")
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 180)
    private String title;

    @Column(nullable = false, length = 120)
    private String topic;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskDifficulty difficulty;

    @Column(nullable = false, length = 30)
    private String language;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String starterCode;

    @Column(nullable = false)
    private Boolean published;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private Long ownerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContentStatus status;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContentVisibility visibility = ContentVisibility.AUTHENTICATED;

    private Long publishedVersionId;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private Long lockVersion;

    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<TaskTest> tests = new ArrayList<>();

    @PrePersist
    private void initializeProblemMetadata() {
        if (status == null) status = Boolean.TRUE.equals(published) ? ContentStatus.PUBLISHED : ContentStatus.DRAFT;
        if (visibility == null) visibility = ContentVisibility.AUTHENTICATED;
        if (updatedAt == null) updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    @PreUpdate
    private void updateTimestamp() {
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
}
