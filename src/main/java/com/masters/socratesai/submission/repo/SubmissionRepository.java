package com.masters.socratesai.submission.repo;

import com.masters.socratesai.submission.model.Submission;
import org.springframework.data.jpa.repository.JpaRepository;
import com.masters.socratesai.execution.model.ExecutionStatus;
import com.masters.socratesai.submission.dto.SubmissionResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface SubmissionRepository extends JpaRepository<Submission, UUID> {
    Optional<Submission> findByIdAndStudentId(UUID id, Long studentId);

    Optional<Submission> findByStudentIdAndIdempotencyKey(Long studentId, UUID idempotencyKey);

    long countByStudentIdAndStatusIn(Long studentId, Collection<ExecutionStatus> statuses);

    @Query("""
            select new com.masters.socratesai.submission.dto.SubmissionResponse(
                s.id, s.snapshot.problemId, s.snapshot.problemVersionId, s.snapshot.languageKey,
                s.snapshot.runtimeKey, s.snapshot.runtimeVersion, s.attemptNumber, s.status, s.verdict,
                s.testsTotal, s.testsPassed, s.testsFailed, s.timeConsumedMs, s.memoryConsumedBytes,
                s.createdAt, s.finishedAt)
            from Submission s
            where s.studentId = :studentId order by s.createdAt desc, s.id desc
            """)
    Page<SubmissionResponse> findSummariesByStudentId(@Param("studentId") Long studentId, Pageable pageable);
}
