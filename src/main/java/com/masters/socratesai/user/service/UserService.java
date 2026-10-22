package com.masters.socratesai.user.service;

import com.masters.socratesai.security.SecurityUserDetails;
import com.masters.socratesai.submission.model.StudentProblemProgress;
import com.masters.socratesai.submission.repo.StudentProblemProgressRepository;
import com.masters.socratesai.interaction.model.InteractionLog;
import com.masters.socratesai.interaction.repo.InteractionLogRepository;
import com.masters.socratesai.session.model.StudentTaskSession;
import com.masters.socratesai.session.repo.StudentTaskSessionRepository;
import com.masters.socratesai.task.model.Task;
import com.masters.socratesai.task.repo.TaskRepository;
import com.masters.socratesai.user.dto.StudentProgressResponse;
import com.masters.socratesai.user.dto.UpdateProfileRequest;
import com.masters.socratesai.user.dto.UpdateSettingsRequest;
import com.masters.socratesai.user.dto.UserProfileResponse;
import com.masters.socratesai.user.model.User;
import com.masters.socratesai.user.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.crossstore.ChangeSetPersister;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final InteractionLogRepository interactionLogRepository;
    private final StudentTaskSessionRepository sessionRepository;
    private final TaskRepository taskRepository;
    private final StudentProblemProgressRepository progressRepository;

    public UserProfileResponse getMyProfile(SecurityUserDetails currentUser) throws ChangeSetPersister.NotFoundException {
        User user = getUserEntity(currentUser.getId());
        return mapToResponse(user);
    }

    @Transactional(readOnly = true)
    public StudentProgressResponse getMyProgress(SecurityUserDetails currentUser) throws ChangeSetPersister.NotFoundException {
        User user = getUserEntity(currentUser.getId());
        List<InteractionLog> logs = interactionLogRepository.findByStudentIdOrderByCreatedAtDesc(user.getId());
        List<StudentTaskSession> sessions = sessionRepository.findByStudentIdOrderByStartedAtDesc(user.getId());

        List<StudentProblemProgress> judged = progressRepository.findByStudentIdOrderByLastActivityAtDesc(user.getId());
        Set<Long> taskIds = judged.stream().map(StudentProblemProgress::getProblemId).collect(Collectors.toSet());
        Map<Long, Task> tasksById = taskRepository.findAllById(taskIds).stream()
                .collect(Collectors.toMap(Task::getId, Function.identity()));
        int attemptedTasks = judged.size();
        int solvedTasks = (int) judged.stream().filter(item -> item.getAcceptedSubmissionId() != null).count();
        int totalAttempts = judged.stream().mapToInt(StudentProblemProgress::getFormalAttempts).sum();
        int activeSessions = (int) sessions.stream()
                .filter(session -> session.getEndedAt() == null)
                .count();
        int helpfulFeedback = (int) logs.stream()
                .filter(log -> Boolean.TRUE.equals(log.getFeedbackHelpful()))
                .count();
        int resolvedInteractions = (int) logs.stream()
                .filter(log -> Boolean.TRUE.equals(log.getResolvedAfterFeedback()))
                .count();
        double meanLatencyMs = logs.stream()
                .map(InteractionLog::getTotalLatencyMs)
                .filter(value -> value != null)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0.0);

        List<StudentProgressResponse.TaskProgress> taskProgress = judged.stream()
                .map(item -> toTaskProgress(item, tasksById.get(item.getProblemId()))).toList();

        return StudentProgressResponse.builder()
                .profile(mapToResponse(user))
                .summary(StudentProgressResponse.Summary.builder()
                        .attemptedTasks(attemptedTasks)
                        .solvedTasks(solvedTasks)
                        .activeSessions(activeSessions)
                        .totalAttempts(totalAttempts)
                        .totalFeedback(logs.size())
                        .helpfulFeedback(helpfulFeedback)
                        .resolvedInteractions(resolvedInteractions)
                        .solveRate(attemptedTasks == 0 ? 0.0 : (double) solvedTasks / attemptedTasks)
                        .meanLatencyMs(meanLatencyMs)
                        .lastActivityAt(judged.isEmpty() ? null : judged.get(0).getLastActivityAt())
                        .build())
                .feedbackActions(countBy(logs.stream().map(InteractionLog::getFeedbackAction)))
                .errorTypes(countBy(logs.stream().map(InteractionLog::getErrorType)))
                .tasks(taskProgress)
                .recentActivity(logs.stream()
                        .limit(12)
                        .map(log -> toRecentActivity(log, log.getProblemId() == null ? null : tasksById.get(log.getProblemId())))
                        .toList())
                .build();
    }

    @Transactional
    public UserProfileResponse updateProfile(SecurityUserDetails currentUser, UpdateProfileRequest request) throws ChangeSetPersister.NotFoundException {
        User user = getUserEntity(currentUser.getId());

        if (request.getFullName() != null) user.setFullName(request.getFullName());
        if (request.getUniversity() != null) user.setUniversity(request.getUniversity());
        if (request.getGroupName() != null) user.setGroupName(request.getGroupName());

        return mapToResponse(user);
    }

    @Transactional
    public UserProfileResponse updateSettings(SecurityUserDetails currentUser, UpdateSettingsRequest request) throws ChangeSetPersister.NotFoundException {
        User user = getUserEntity(currentUser.getId());

        if (request.getDarkMode() != null) user.getSettings().setDarkMode(request.getDarkMode());
        if (request.getEmailNotifications() != null) user.getSettings().setEmailNotifications(request.getEmailNotifications());
        if (request.getPreferredLanguage() != null) user.getSettings().setPreferredLanguage(request.getPreferredLanguage());

        return mapToResponse(user);
    }

    private User getUserEntity(Long id) throws ChangeSetPersister.NotFoundException {
        return userRepository.findById(id)
                .orElseThrow(ChangeSetPersister.NotFoundException::new);
    }

    private UserProfileResponse mapToResponse(User user) {
        return UserProfileResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole())
                .university(user.getUniversity())
                .groupName(user.getGroupName())
                .darkMode(user.getSettings().getDarkMode())
                .emailNotifications(user.getSettings().getEmailNotifications())
                .preferredLanguage(user.getSettings().getPreferredLanguage())
                .build();
    }

    private StudentProgressResponse.TaskProgress toTaskProgress(StudentProblemProgress progress, Task task) {
        return StudentProgressResponse.TaskProgress.builder()
                .taskId(progress.getProblemId())
                .title(task == null ? "Task #" + progress.getProblemId() : task.getTitle())
                .topic(task == null ? "Native problem" : task.getTopic())
                .difficulty(task == null ? "N/A" : task.getDifficulty().name())
                .status(progress.getAcceptedSubmissionId() == null ? "IN_PROGRESS" : "SOLVED")
                .openable(task != null && task.getPublishedVersionId() != null)
                .attempts(progress.getFormalAttempts())
                .bestTestsPassed(progress.getBestTestsPassed())
                .lastTestsFailed(progress.getLastTestsFailed())
                .lastActivityAt(progress.getLastActivityAt()).build();
    }

    private StudentProgressResponse.RecentActivity toRecentActivity(InteractionLog log, Task task) {
        return StudentProgressResponse.RecentActivity.builder()
                .taskId(log.getTaskId())
                .taskTitle(task == null ? "Legacy activity #" + log.getTaskId() : task.getTitle())
                .evidenceOrigin(log.getEvidenceOrigin() == null || log.getEvidenceOrigin() == com.masters.socratesai.common.model.EvidenceOrigin.LEGACY_UNKNOWN
                        ? "LEGACY_HEURISTIC_ACTIVITY" : log.getEvidenceOrigin().name())
                .attemptNo(log.getAttemptNo())
                .feedbackAction(log.getFeedbackAction())
                .errorType(log.getErrorType())
                .compileSuccess(log.getCompileSuccess())
                .testsPassed(log.getTestsPassed())
                .testsFailed(log.getTestsFailed())
                .resolvedAfterFeedback(log.getResolvedAfterFeedback())
                .createdAt(log.getCreatedAt())
                .build();
    }

    private Map<String, Long> countBy(Stream<String> values) {
        return values
                .map(value -> value == null || value.isBlank() ? "N/A" : value)
                .collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()));
    }

}
