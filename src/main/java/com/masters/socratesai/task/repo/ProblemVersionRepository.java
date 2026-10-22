package com.masters.socratesai.task.repo;

import com.masters.socratesai.task.model.ProblemVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import com.masters.socratesai.task.model.ProblemVersionStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import java.util.Optional;

public interface ProblemVersionRepository extends JpaRepository<ProblemVersion, Long> {
    Optional<ProblemVersion> findByIdAndProblem_Id(Long id, Long problemId);

    Optional<ProblemVersion> findFirstByProblem_IdAndStatusOrderByVersionNumberDesc(Long problemId,
                                                                                 ProblemVersionStatus status);

    Slice<ProblemVersion> findAllByProblem_IdOrderByVersionNumberDesc(Long problemId, Pageable pageable);
    Optional<ProblemVersion> findFirstByProblem_IdOrderByVersionNumberDesc(Long problemId);
    long countByProblem_Id(Long problemId);
}
