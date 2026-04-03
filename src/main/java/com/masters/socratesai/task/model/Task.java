package com.masters.socratesai.task.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<TaskTest> tests = new ArrayList<>();
}