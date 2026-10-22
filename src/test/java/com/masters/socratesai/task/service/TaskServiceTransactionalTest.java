package com.masters.socratesai.task.service;

import com.masters.socratesai.common.api.ApiException;
import com.masters.socratesai.task.dto.*;
import com.masters.socratesai.task.model.TaskDifficulty;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** The compatibility facade delegates to the native permission/publication boundary. */
class TaskServiceTransactionalTest {
    private final TeacherProblemService teacher = mock(TeacherProblemService.class);
    private final StudentProblemService student = mock(StudentProblemService.class);
    private final TaskService facade = new TaskService(teacher, student);

    @Test void rejectsLegacyDirectPublication() {
        var request = new CreateTaskRequest();
        request.setPublished(true);
        assertThatThrownBy(() -> facade.createTask(request)).isInstanceOf(ApiException.class);
        verifyNoInteractions(teacher, student);
    }

    @Test void privateReadUsesTheSameOwnerGuardAsNativeApi() {
        when(teacher.get(23L)).thenThrow(new ApiException(com.masters.socratesai.common.api.ApiErrorCode.NOT_FOUND));
        assertThatThrownBy(() -> facade.getTaskForTeacher(23L)).isInstanceOf(ApiException.class);
        verify(teacher).get(23L);
    }
}
