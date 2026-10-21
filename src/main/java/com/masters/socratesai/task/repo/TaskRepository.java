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

public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findByPublishedTrue();

    Optional<Task> findByIdAndOwnerId(Long id, Long ownerId);

    Page<ProblemSummary> findAllByOwnerId(Long ownerId, Pageable pageable);

    Page<ProblemSummary> findAllByStatusAndVisibilityIn(ContentStatus status, Collection<ContentVisibility> visibility,
                                                       Pageable pageable);
}
