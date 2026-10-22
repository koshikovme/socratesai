package com.masters.socratesai.task.repo;

import com.masters.socratesai.task.verification.ReferenceVerification;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface ReferenceVerificationRepository extends JpaRepository<ReferenceVerification, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from ReferenceVerification v where v.id = :id")
    Optional<ReferenceVerification> lockById(UUID id);

    @Modifying
    @Query("""
        update ReferenceVerification v set v.status = com.masters.socratesai.execution.model.ExecutionStatus.SYSTEM_ERROR,
        v.failureCode = 'DEADLINE_EXCEEDED', v.finishedAt = :now, v.updatedAt = :now, v.lockVersion = v.lockVersion + 1
        where v.problemVersionId = :versionId and v.deadlineAt <= :now
        and v.status in (com.masters.socratesai.execution.model.ExecutionStatus.QUEUED,
                         com.masters.socratesai.execution.model.ExecutionStatus.RUNNING)
        """)
    void expire(Long versionId, OffsetDateTime now);

    boolean existsByProblemVersionIdAndStatusIn(Long versionId, java.util.Collection<com.masters.socratesai.execution.model.ExecutionStatus> statuses);
    void deleteAllByProblemVersionId(Long versionId);
}
