package com.masters.socratesai.user.service;

import com.masters.socratesai.interaction.model.InteractionLog;
import com.masters.socratesai.interaction.repo.InteractionLogRepository;
import com.masters.socratesai.security.SecurityUserDetails;
import com.masters.socratesai.session.model.StudentTaskSession;
import com.masters.socratesai.session.repo.StudentTaskSessionRepository;
import com.masters.socratesai.task.model.Task;
import com.masters.socratesai.task.model.TaskDifficulty;
import com.masters.socratesai.task.repo.TaskRepository;
import com.masters.socratesai.user.dto.StudentProgressResponse;
import com.masters.socratesai.user.model.User;
import com.masters.socratesai.user.model.UserRole;
import com.masters.socratesai.user.model.UserSettings;
import com.masters.socratesai.user.repo.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserServiceTest {

    private UserRepository userRepository;
    private InteractionLogRepository interactionLogRepository;
    private StudentTaskSessionRepository sessionRepository;
    private TaskRepository taskRepository;
    private UserService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        interactionLogRepository = mock(InteractionLogRepository.class);
        sessionRepository = mock(StudentTaskSessionRepository.class);
        taskRepository = mock(TaskRepository.class);
        service = new UserService(userRepository, interactionLogRepository, sessionRepository, taskRepository);
    }

    @Test
    void shouldAggregateStudentProgressFromInteractionsAndSessions() throws Exception {
        User user = User.builder()
                .id(7L)
                .email("student@example.com")
                .passwordHash("hash")
                .fullName("Student One")
                .role(UserRole.STUDENT)
                .settings(UserSettings.builder().build())
                .createdAt(LocalDateTime.now())
                .build();

        OffsetDateTime now = OffsetDateTime.parse("2026-05-25T10:00:00+05:00");
        InteractionLog accepted = log(7L, 10L, 2, true, 3, 0, "NO_FEEDBACK", "UNKNOWN", now);
        InteractionLog earlierFailed = log(7L, 10L, 1, true, 1, 2, "CONCEPTUAL_HINT", "OFF_BY_ONE", now.minusMinutes(5));
        InteractionLog activeTask = log(7L, 11L, 1, false, 0, 0, "CODE_HIGHLIGHT", "SYNTAX_ERROR", now.minusMinutes(10));

        StudentTaskSession completedSession = StudentTaskSession.builder()
                .sessionId(UUID.randomUUID())
                .studentId(7L)
                .taskId(10L)
                .startedAt(now.minusHours(1))
                .endedAt(now)
                .totalAttempts(2)
                .totalFeedbackCount(2)
                .build();
        StudentTaskSession activeSession = StudentTaskSession.builder()
                .sessionId(UUID.randomUUID())
                .studentId(7L)
                .taskId(11L)
                .startedAt(now.minusMinutes(12))
                .totalAttempts(1)
                .totalFeedbackCount(1)
                .build();

        Task task = Task.builder()
                .id(10L)
                .title("Two Sum")
                .topic("Hash map")
                .difficulty(TaskDifficulty.EASY)
                .language("java")
                .description("Find a pair.")
                .published(true)
                .createdAt(LocalDateTime.now())
                .build();

        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(interactionLogRepository.findByStudentIdOrderByCreatedAtDesc(7L))
                .thenReturn(List.of(accepted, earlierFailed, activeTask));
        when(sessionRepository.findByStudentIdOrderByStartedAtDesc(7L))
                .thenReturn(List.of(activeSession, completedSession));
        when(taskRepository.findAllById(any())).thenReturn(List.of(task));

        StudentProgressResponse progress = service.getMyProgress(new SecurityUserDetails(user));

        assertThat(progress.getSummary().getAttemptedTasks()).isEqualTo(2);
        assertThat(progress.getSummary().getSolvedTasks()).isEqualTo(1);
        assertThat(progress.getSummary().getActiveSessions()).isEqualTo(1);
        assertThat(progress.getSummary().getTotalAttempts()).isEqualTo(3);
        assertThat(progress.getFeedbackActions()).containsEntry("NO_FEEDBACK", 1L);
        assertThat(progress.getErrorTypes()).containsEntry("SYNTAX_ERROR", 1L);
        assertThat(progress.getTasks())
                .extracting(StudentProgressResponse.TaskProgress::getStatus)
                .contains("SOLVED", "IN_PROGRESS");
        assertThat(progress.getRecentActivity()).hasSize(3);
    }

    private InteractionLog log(
            Long studentId,
            Long taskId,
            int attemptNo,
            boolean compileSuccess,
            int testsPassed,
            int testsFailed,
            String action,
            String errorType,
            OffsetDateTime createdAt
    ) {
        InteractionLog log = InteractionLog.builder()
                .interactionId(UUID.randomUUID())
                .sessionId(UUID.randomUUID())
                .studentId(studentId)
                .taskId(taskId)
                .attemptNo(attemptNo)
                .compileSuccess(compileSuccess)
                .testsPassed(testsPassed)
                .testsFailed(testsFailed)
                .feedbackAction(action)
                .errorType(errorType)
                .totalLatencyMs(100)
                .createdAt(createdAt)
                .build();
        log.prePersist();
        return log;
    }
}
