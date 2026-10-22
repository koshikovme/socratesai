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
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

public interface ProblemCollectionRepository extends JpaRepository<ProblemCollection, Long> {
    String READABLE = """
        c.status = com.masters.socratesai.common.model.ContentStatus.PUBLISHED and c.visibility in :visibility
        and exists (select m.id from CollectionProblem m where m.collection = c)
        and not exists (select m.id from CollectionProblem m join m.problemVersion v join v.problem p
            where m.collection = c and (p.status <> com.masters.socratesai.common.model.ContentStatus.PUBLISHED
                or p.publishedVersionId is null or p.visibility not in :visibility or v.visibility not in :visibility
                or v.status <> com.masters.socratesai.task.model.ProblemVersionStatus.PUBLISHED
                or v.legacySnapshot = true or v.referenceVerificationId is null))
        """;
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ProblemCollection c where c.id = :id")
    Optional<ProblemCollection> lockById(Long id);

    @Query("select c from ProblemCollection c order by c.id desc")
    Page<CollectionSummary> authorCatalog(Pageable pageable);

    @Query("select c from ProblemCollection c where " + READABLE + " order by c.id desc")
    Page<CollectionSummary> studentCatalog(Collection<ContentVisibility> visibility, Pageable pageable);

    @Query("select count(c) > 0 from ProblemCollection c where c.id = :id and " + READABLE)
    boolean readable(Long id, Collection<ContentVisibility> visibility);
    Optional<ProblemCollection> findByIdAndOwnerId(Long id, Long ownerId);

    Page<CollectionSummary> findAllByOwnerId(Long ownerId, Pageable pageable);

    Page<CollectionSummary> findAllByStatusAndVisibilityIn(ContentStatus status, Collection<ContentVisibility> visibility,
                                                          Pageable pageable);
}
