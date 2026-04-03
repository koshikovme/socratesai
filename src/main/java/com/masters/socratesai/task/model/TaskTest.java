package com.masters.socratesai.task.model;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "task_tests")
public class TaskTest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id")
    private Task task;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String inputData;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String expectedOutput;

    @Column(nullable = false)
    private Boolean hidden;
}