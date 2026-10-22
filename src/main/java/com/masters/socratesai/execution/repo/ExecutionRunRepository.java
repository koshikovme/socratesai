package com.masters.socratesai.execution.repo;

import com.masters.socratesai.execution.model.ExecutionRun;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

public interface ExecutionRunRepository extends JpaRepository<ExecutionRun, UUID> {
    Optional<ExecutionRun> findByRequestedByAndIdempotencyKey(Long requestedBy, UUID key);
    Optional<ExecutionRun> findByIdAndRequestedBy(UUID id, Long requestedBy);

    Slice<ExecutionRun> findAllByExpiresAtBeforeOrderByExpiresAt(OffsetDateTime now, Pageable pageable);
}
