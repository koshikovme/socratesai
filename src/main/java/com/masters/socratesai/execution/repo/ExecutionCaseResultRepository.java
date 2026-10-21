package com.masters.socratesai.execution.repo;

import com.masters.socratesai.execution.model.ExecutionCaseResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.List;
import java.util.UUID;

public interface ExecutionCaseResultRepository extends JpaRepository<ExecutionCaseResult, Long> {
    @EntityGraph(attributePaths = "testCase")
    List<ExecutionCaseResult> findAllByJob_IdAndGenerationOrderByTestCase_Position(UUID jobId, Integer generation);
}
