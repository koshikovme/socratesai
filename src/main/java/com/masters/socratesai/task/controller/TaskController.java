package com.masters.socratesai.task.controller;

import com.masters.socratesai.task.dto.CreateTaskRequest;
import com.masters.socratesai.task.dto.TaskResponse;
import com.masters.socratesai.task.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.crossstore.ChangeSetPersister;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @GetMapping("/public")
    public List<TaskResponse> getPublicTasks() {
        return taskService.getPublicTasks();
    }

    @GetMapping("/public/{id}")
    public TaskResponse getTaskForStudent(@PathVariable Long id) throws ChangeSetPersister.NotFoundException {
        return taskService.getTaskForStudent(id);
    }

    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    @PostMapping
    public TaskResponse createTask(@RequestBody CreateTaskRequest request) {
        return taskService.createTask(request);
    }

    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    @GetMapping("/{id}")
    public TaskResponse getTaskForTeacher(@PathVariable Long id) throws ChangeSetPersister.NotFoundException {
        return taskService.getTaskForTeacher(id);
    }
}