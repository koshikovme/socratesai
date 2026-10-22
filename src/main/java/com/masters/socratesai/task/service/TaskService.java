package com.masters.socratesai.task.service;

import com.masters.socratesai.common.api.*;
import com.masters.socratesai.common.model.ContentVisibility;
import com.masters.socratesai.task.dto.*;
import com.masters.socratesai.task.model.OutputComparison;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/** Compatibility facade. All native permissions and publication gates remain mandatory. */
@Service @Validated @RequiredArgsConstructor
public class TaskService {
    private final TeacherProblemService teacher;
    private final StudentProblemService student;

    public TaskResponse createTask(@Valid CreateTaskRequest request) {
        if (Boolean.TRUE.equals(request.getPublished())) throw new ApiException(ApiErrorCode.INVALID_REQUEST);
        var language = new ProblemLanguageRequest(request.getLanguage(), request.getStarterCode(), 2000, 5000, 262144, 65536);
        var cases = request.getTests() == null ? List.<ProblemTestCaseRequest>of() : java.util.stream.IntStream.range(0, request.getTests().size())
                .mapToObj(index -> {
                    var test = request.getTests().get(index);
                    return new ProblemTestCaseRequest(index, test.getInputData(), test.getExpectedOutput(), Boolean.TRUE.equals(test.getHidden()));
                }).toList();
        return author(teacher.create(new ProblemDraftRequest(0L, request.getTitle(), request.getTopic(), request.getDifficulty(),
                request.getDescription(), "", "", "", OutputComparison.IGNORE_TRAILING_LINE_ENDINGS,
                ContentVisibility.AUTHENTICATED, List.of(language), cases)));
    }

    public List<TaskResponse> getPublicTasks() {
        return student.list(0, 50).items().stream().map(summary -> student(student.get(summary.id(), null))).toList();
    }
    public TaskResponse getTaskForStudent(Long id) { return student(student.get(id, null)); }
    public TaskResponse getTaskForTeacher(Long id) { return author(teacher.get(id)); }

    private TaskResponse author(ProblemAuthorResponse response) {
        var version = response.version();
        var first = version.languages().stream().findFirst().orElse(null);
        return TaskResponse.builder().id(response.id()).title(version.title()).topic(version.topic()).difficulty(version.difficulty())
                .description(version.statement()).language(first == null ? "unspecified" : first.languageKey())
                .starterCode(first == null ? null : first.starterCode()).published(response.publishedVersionId() != null)
                .tests(version.tests().stream().map(test -> TaskResponse.TaskTestResponse.builder().id(test.id()).inputData(test.inputData())
                        .expectedOutput(test.expectedOutput()).hidden(test.hidden()).build()).toList()).build();
    }

    private TaskResponse student(ProblemResponse response) {
        var first = response.languages().stream().findFirst().orElse(null);
        return TaskResponse.builder().id(response.id()).title(response.title()).topic(response.topic()).difficulty(response.difficulty())
                .description(response.statement()).language(first == null ? "unspecified" : first.languageKey())
                .starterCode(first == null ? null : first.starterCode()).published(true)
                .tests(response.samples().stream().map(sample -> TaskResponse.TaskTestResponse.builder().inputData(sample.inputData())
                        .expectedOutput(sample.expectedOutput()).hidden(false).build()).toList()).build();
    }
}
