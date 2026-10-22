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
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public interface ExecutionJobRepository extends JpaRepository<ExecutionJob, UUID> {
    @Query(value = """
        SELECT j.* FROM execution_jobs j LEFT JOIN submissions s ON s.id=j.submission_id
        LEFT JOIN execution_runs r ON r.id=j.run_id
        WHERE j.status='FINISHED' AND j.mode IN ('SUBMISSION','SAMPLE_RUN') AND
          ((s.student_id=:studentId AND s.problem_id=:problemId AND s.problem_version_id=:versionId
            AND s.language_key=:language AND s.source_hash=:hash)
          OR (r.requested_by=:studentId AND r.problem_id=:problemId AND r.problem_version_id=:versionId
            AND r.language_key=:language AND r.source_hash=:hash AND r.expires_at>:now))
        ORDER BY j.finished_at DESC, j.id DESC LIMIT 1
        """, nativeQuery = true)
    Optional<ExecutionJob> latestMentorEvidence(Long studentId, Long problemId, Long versionId, String language, String hash, OffsetDateTime now);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from ExecutionJob j where j.id = :id")
    Optional<ExecutionJob> lockById(UUID id);

    long countByStatusIn(java.util.Collection<com.masters.socratesai.execution.model.ExecutionStatus> statuses);

    @Query("select count(j) from ExecutionJob j where j.status in :statuses or j.retryAllowed = true")
    long countAdmitted(java.util.Collection<com.masters.socratesai.execution.model.ExecutionStatus> statuses);

    @Query("""
        select count(j) from ExecutionJob j, Submission s where j.submissionId = s.id and s.studentId = :studentId
        and (j.status in :statuses or j.retryAllowed = true)
        """)
    long countAdmittedByStudent(Long studentId, java.util.Collection<com.masters.socratesai.execution.model.ExecutionStatus> statuses);

    @Query("""
        select count(j) from ExecutionJob j, ExecutionRun r where j.runId = r.id and r.requestedBy = :studentId
        and (j.status in :statuses or j.retryAllowed = true)
        """)
    long countAdmittedRunsByStudent(Long studentId, java.util.Collection<com.masters.socratesai.execution.model.ExecutionStatus> statuses);
    Optional<ExecutionJob> findBySubmissionId(UUID submissionId);

    Optional<ExecutionJob> findByRunId(UUID runId);

    /** Caller must persist a new lease/generation in the same short transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    @Query(value = """
            SELECT * FROM execution_jobs
            WHERE deadline_at > :now AND attempt_count < max_attempts AND (
                (status = 'QUEUED' AND next_attempt_at <= :now)
                OR (status = 'SYSTEM_ERROR' AND retry_allowed AND next_attempt_at <= :now)
                OR (status IN ('DISPATCHING', 'RUNNING') AND lease_expires_at <= :now))
            ORDER BY next_attempt_at, created_at, id
            LIMIT :limit FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<ExecutionJob> lockNextJobs(@Param("now") OffsetDateTime now, @Param("limit") int limit);

    @Query(value = """
        SELECT * FROM execution_jobs WHERE status <> 'FINISHED' AND
          (status <> 'SYSTEM_ERROR' OR retry_allowed) AND
          (deadline_at <= :now OR (attempt_count >= max_attempts AND
            (status NOT IN ('DISPATCHING','RUNNING') OR lease_expires_at <= :now)))
        ORDER BY deadline_at, id LIMIT :limit FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<ExecutionJob> lockExpiredJobs(OffsetDateTime now, int limit);
}
