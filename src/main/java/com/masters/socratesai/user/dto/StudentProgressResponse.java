package com.masters.socratesai.user.dto;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class StudentProgressResponse {
    private UserProfileResponse profile;
    private Summary summary;
    private Map<String, Long> feedbackActions;
    private Map<String, Long> errorTypes;
    private List<TaskProgress> tasks;
    private List<RecentActivity> recentActivity;

    @Data
    @Builder
    public static class Summary {
        private int attemptedTasks;
        private int solvedTasks;
        private int activeSessions;
        private int totalAttempts;
        private int totalFeedback;
        private int helpfulFeedback;
        private int resolvedInteractions;
        private double solveRate;
        private double meanLatencyMs;
        private OffsetDateTime lastActivityAt;
    }

    @Data
    @Builder
    public static class TaskProgress {
        private Long taskId;
        private String title;
        private String topic;
        private String difficulty;
        private String status;
        private boolean openable;
        private int attempts;
        private int feedbackCount;
        private int bestTestsPassed;
        private int lastTestsFailed;
        private String lastFeedbackAction;
        private String lastErrorType;
        private OffsetDateTime lastActivityAt;
    }

    @Data
    @Builder
    public static class RecentActivity {
        private Long taskId;
        private String taskTitle;
        private Integer attemptNo;
        private String feedbackAction;
        private String errorType;
        private Boolean compileSuccess;
        private Integer testsPassed;
        private Integer testsFailed;
        private Boolean resolvedAfterFeedback;
        private OffsetDateTime createdAt;
    }
}
