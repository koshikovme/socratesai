package com.masters.socratesai.execution.repo;

import com.masters.socratesai.execution.model.ExecutionJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExecutionJobRepository extends JpaRepository<ExecutionJob, UUID> {
    Optional<ExecutionJob> findBySubmissionId(UUID submissionId);

    Optional<ExecutionJob> findByRunId(UUID runId);

    /** Caller must persist a new lease/generation in the same short transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    @Query(value = """
            SELECT * FROM execution_jobs
            WHERE deadline_at > :now AND attempt_count < max_attempts AND (
                (status = 'QUEUED' AND next_attempt_at <= :now)
                OR (status IN ('DISPATCHING', 'RUNNING') AND lease_expires_at <= :now))
            ORDER BY next_attempt_at, created_at, id
            LIMIT :limit FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<ExecutionJob> lockNextJobs(@Param("now") OffsetDateTime now, @Param("limit") int limit);
}
