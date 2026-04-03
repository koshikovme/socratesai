package com.masters.socratesai.task.repo;

import com.masters.socratesai.task.model.Task;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findByPublishedTrue();
}