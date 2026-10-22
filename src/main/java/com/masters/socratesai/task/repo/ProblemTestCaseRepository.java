package com.masters.socratesai.task.repo;

import com.masters.socratesai.task.model.ProblemTestCase;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProblemTestCaseRepository extends JpaRepository<ProblemTestCase, Long> {
    List<ProblemTestCase> findAllByProblemVersion_IdOrderByPosition(Long problemVersionId);

    List<ProblemTestCase> findAllByProblemVersion_IdAndHiddenFalseOrderByPosition(Long problemVersionId);

    long countByProblemVersion_Id(Long problemVersionId);
    void deleteAllByProblemVersion_Id(Long problemVersionId);
}
