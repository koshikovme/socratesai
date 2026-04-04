package com.masters.socratesai.task.service;

import com.masters.socratesai.task.dto.CreateTaskRequest;
import com.masters.socratesai.task.dto.CreateTaskTestRequest;
import com.masters.socratesai.task.dto.TaskResponse;
import com.masters.socratesai.task.model.TaskDifficulty;
import com.masters.socratesai.task.repo.TaskRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.Transactional;

import com.masters.socratesai.support.JpaIntegrationTestConfig;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringJUnitConfig(JpaIntegrationTestConfig.class)
@Transactional
class TaskServiceTransactionalTest {

    @Autowired
    private TaskService taskService;

    @Autowired
    private TaskRepository taskRepository;

    @Test
    void shouldPersistTaskAndNestedTests() {
        CreateTaskRequest request = new CreateTaskRequest();
        request.setTitle("Two Sum");
        request.setTopic("Arrays");
        request.setDifficulty(TaskDifficulty.EASY);
        request.setLanguage("java");
        request.setDescription("Find two indices.");
        request.setStarterCode("class Solution {}");
        request.setPublished(true);
        request.setTests(List.of(test("1 2", "3", false), test("5 7", "12", true)));

        TaskResponse response = taskService.createTask(request);

        assertThat(response.getId()).isNotNull();
        assertThat(taskRepository.findById(response.getId())).isPresent();
        assertThat(taskRepository.findById(response.getId()).orElseThrow().getTests()).hasSize(2);
    }

    @Test
    void shouldHideTeacherOnlyTestsForStudentView() throws Exception {
        CreateTaskRequest request = new CreateTaskRequest();
        request.setTitle("FizzBuzz");
        request.setTopic("Loops");
        request.setDifficulty(TaskDifficulty.EASY);
        request.setLanguage("java");
        request.setDescription("Return fizz buzz values.");
        request.setStarterCode("class Solution {}");
        request.setPublished(true);
        request.setTests(List.of(test("3", "Fizz", false), test("15", "FizzBuzz", true)));

        TaskResponse created = taskService.createTask(request);
        TaskResponse studentView = taskService.getTaskForStudent(created.getId());
        TaskResponse teacherView = taskService.getTaskForTeacher(created.getId());

        assertThat(studentView.getTests()).hasSize(1);
        assertThat(teacherView.getTests()).hasSize(2);
    }

    private CreateTaskTestRequest test(String input, String output, boolean hidden) {
        CreateTaskTestRequest request = new CreateTaskTestRequest();
        request.setInputData(input);
        request.setExpectedOutput(output);
        request.setHidden(hidden);
        return request;
    }
}
