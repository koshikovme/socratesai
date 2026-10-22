package com.masters.socratesai.execution.model;

import com.masters.socratesai.common.model.VersionedEntity;
import java.time.OffsetDateTime;
import java.util.UUID;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "execution_runs")
public class ExecutionRun extends VersionedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private Long requestedBy;

    @Column(updatable = false)
    private UUID idempotencyKey;

    @Embedded
    private ExecutionSnapshot snapshot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 30)
    private ExecutionMode mode;

    @Column(nullable = false)
    private OffsetDateTime expiresAt;
}
