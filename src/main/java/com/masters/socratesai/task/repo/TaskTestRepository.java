package com.masters.socratesai.task.repo;

import com.masters.socratesai.task.model.Task;
import com.masters.socratesai.task.model.TaskTest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskTestRepository extends JpaRepository<TaskTest, Long> {
}