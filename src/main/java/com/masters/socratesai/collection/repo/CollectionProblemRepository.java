package com.masters.socratesai.collection.repo;

import com.masters.socratesai.collection.model.CollectionProblem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import com.masters.socratesai.collection.dto.CollectionProblemResponse;
import java.util.List;

public interface CollectionProblemRepository extends JpaRepository<CollectionProblem, Long> {
    @Query("""
        select new com.masters.socratesai.collection.dto.CollectionProblemResponse(m.problemId, v.id, m.position, v.title)
        from CollectionProblem m join m.problemVersion v where m.collection.id = :collectionId order by m.position
        """)
    List<CollectionProblemResponse> findDetails(Long collectionId);
    @EntityGraph(attributePaths = "problemVersion")
    List<CollectionProblem> findAllByCollection_IdOrderByPosition(Long collectionId);

    boolean existsByCollection_IdAndProblemIdAndProblemVersion_Id(Long collectionId, Long problemId, Long problemVersionId);
    boolean existsByProblemId(Long problemId);
    void deleteAllByCollection_Id(Long collectionId);
}
