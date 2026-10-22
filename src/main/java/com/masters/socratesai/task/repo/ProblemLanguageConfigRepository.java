package com.masters.socratesai.task.repo;

import com.masters.socratesai.task.model.ProblemLanguageConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ProblemLanguageConfigRepository extends JpaRepository<ProblemLanguageConfig, Long> {
    List<ProblemLanguageConfig> findAllByProblemVersion_IdOrderByLanguageKey(Long problemVersionId);

    Optional<ProblemLanguageConfig> findByProblemVersion_IdAndLanguageKey(Long problemVersionId, String languageKey);
    void deleteAllByProblemVersion_Id(Long problemVersionId);
}
