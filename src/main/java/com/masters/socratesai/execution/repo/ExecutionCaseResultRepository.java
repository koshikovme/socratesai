package com.masters.socratesai.execution.repo;

import com.masters.socratesai.execution.model.ExecutionCaseResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.UUID;

public interface ExecutionCaseResultRepository extends JpaRepository<ExecutionCaseResult, Long> {
    @Query("""
        select new com.masters.socratesai.mentor.nativecontext.MentorCaseEvidence(r.status, r.verdict)
        from ExecutionCaseResult r where r.job.id = :jobId and r.generation = 0 order by r.testCase.position
        """)
    List<com.masters.socratesai.mentor.nativecontext.MentorCaseEvidence> mentorEvidence(UUID jobId);
    @Modifying @Query("delete from ExecutionCaseResult r where r.job.id = :jobId")
    void deleteCheckpoints(UUID jobId);
    @EntityGraph(attributePaths = "testCase")
    List<ExecutionCaseResult> findAllByJob_IdAndGenerationOrderByTestCase_Position(UUID jobId, Integer generation);
}
