package com.masters.socratesai.collection.repo;

import com.masters.socratesai.collection.model.CollectionProblem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.List;

public interface CollectionProblemRepository extends JpaRepository<CollectionProblem, Long> {
    @EntityGraph(attributePaths = "problemVersion")
    List<CollectionProblem> findAllByCollection_IdOrderByPosition(Long collectionId);

    boolean existsByCollection_IdAndProblemIdAndProblemVersion_Id(Long collectionId, Long problemId, Long problemVersionId);
}
