package com.masters.socratesai.collection.repo;

import com.masters.socratesai.collection.model.ProblemCollection;
import org.springframework.data.jpa.repository.JpaRepository;
import com.masters.socratesai.common.model.ContentStatus;
import com.masters.socratesai.common.model.ContentVisibility;
import com.masters.socratesai.collection.repo.projection.CollectionSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Collection;
import java.util.Optional;

public interface ProblemCollectionRepository extends JpaRepository<ProblemCollection, Long> {
    Optional<ProblemCollection> findByIdAndOwnerId(Long id, Long ownerId);

    Page<CollectionSummary> findAllByOwnerId(Long ownerId, Pageable pageable);

    Page<CollectionSummary> findAllByStatusAndVisibilityIn(ContentStatus status, Collection<ContentVisibility> visibility,
                                                          Pageable pageable);
}
