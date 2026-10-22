package com.masters.socratesai.task.repo;

import com.masters.socratesai.task.model.Task;
import com.masters.socratesai.common.model.ContentStatus;
import com.masters.socratesai.common.model.ContentVisibility;
import com.masters.socratesai.task.repo.projection.ProblemSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findByPublishedTrue();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Task t where t.id = :id")
    Optional<Task> lockById(Long id);

    @Query("select t from Task t order by t.id desc")
    Page<ProblemSummary> authorCatalog(Pageable pageable);

    @Query("""
        select t from Task t join ProblemVersion v on v.id = t.publishedVersionId
        where t.status = com.masters.socratesai.common.model.ContentStatus.PUBLISHED
        and t.visibility in :visibility and v.referenceVerificationId is not null
        and v.status = com.masters.socratesai.task.model.ProblemVersionStatus.PUBLISHED
        and v.legacySnapshot = false order by t.id desc
        """)
    Page<ProblemSummary> studentCatalog(Collection<ContentVisibility> visibility, Pageable pageable);

    Optional<Task> findByIdAndOwnerId(Long id, Long ownerId);

    Page<ProblemSummary> findAllByOwnerId(Long ownerId, Pageable pageable);

    Page<ProblemSummary> findAllByStatusAndVisibilityIn(ContentStatus status, Collection<ContentVisibility> visibility,
                                                       Pageable pageable);
}
