package com.masters.socratesai.task.service;

import com.masters.socratesai.task.dto.CreateTaskRequest;
import com.masters.socratesai.task.dto.CreateTaskTestRequest;
import com.masters.socratesai.task.dto.TaskResponse;
import com.masters.socratesai.task.model.Task;
import com.masters.socratesai.task.model.TaskTest;
import com.masters.socratesai.task.repo.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.crossstore.ChangeSetPersister;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;

    @Transactional
    public TaskResponse createTask(CreateTaskRequest request) {
        Task task = Task.builder()
                .title(request.getTitle())
                .topic(request.getTopic())
                .difficulty(request.getDifficulty())
                .language(request.getLanguage())
                .description(request.getDescription())
                .starterCode(request.getStarterCode())
                .published(Boolean.TRUE.equals(request.getPublished()))
                .createdAt(LocalDateTime.now())
                .build();

        if (request.getTests() != null) {
            for (CreateTaskTestRequest testRequest : request.getTests()) {
                TaskTest test = TaskTest.builder()
                        .task(task)
                        .inputData(testRequest.getInputData())
                        .expectedOutput(testRequest.getExpectedOutput())
                        .hidden(Boolean.TRUE.equals(testRequest.getHidden()))
                        .build();
                task.getTests().add(test);
            }
        }

        taskRepository.save(task);
        return toResponse(task, true);
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> getPublicTasks() {
        return taskRepository.findByPublishedTrue()
                .stream()
                .map(task -> toResponse(task, false))
                .toList();
    }

    @Transactional(readOnly = true)
    public TaskResponse getTaskForStudent(Long id) throws ChangeSetPersister.NotFoundException {
        Task task = taskRepository.findById(id)
                .orElseThrow(ChangeSetPersister.NotFoundException::new);

        if (!Boolean.TRUE.equals(task.getPublished())) {
            throw new ChangeSetPersister.NotFoundException();
        }

        return toResponse(task, false);
    }

    @Transactional(readOnly = true)
    public TaskResponse getTaskForTeacher(Long id) throws ChangeSetPersister.NotFoundException {
        Task task = taskRepository.findById(id)
                .orElseThrow(ChangeSetPersister.NotFoundException::new);
        return toResponse(task, true);
    }

    private TaskResponse toResponse(Task task, boolean includeHiddenTests) {
        return TaskResponse.builder()
                .id(task.getId())
                .title(task.getTitle())
                .topic(task.getTopic())
                .difficulty(task.getDifficulty())
                .language(task.getLanguage())
                .description(task.getDescription())
                .starterCode(task.getStarterCode())
                .published(task.getPublished())
                .tests(
                        task.getTests().stream()
                                .filter(t -> includeHiddenTests || !Boolean.TRUE.equals(t.getHidden()))
                                .map(t -> TaskResponse.TaskTestResponse.builder()
                                        .id(t.getId())
                                        .inputData(t.getInputData())
                                        .expectedOutput(t.getExpectedOutput())
                                        .hidden(t.getHidden())
                                        .build())
                                .toList()
                )
                .build();
    }
}