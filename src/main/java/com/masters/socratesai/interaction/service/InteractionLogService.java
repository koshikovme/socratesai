package com.masters.socratesai.interaction.service;

import com.masters.socratesai.analyzer.dto.AnalyzerResult;
import com.masters.socratesai.interaction.dto.InteractionResultResponse;
import com.masters.socratesai.interaction.model.InteractionLog;
import com.masters.socratesai.interaction.repo.InteractionLogRepository;
import com.masters.socratesai.mentor.dto.StudentContextDto;
import com.masters.socratesai.mentor.model.FeedbackAction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class InteractionLogService {

    private final InteractionLogRepository repository;

    public InteractionLogService(InteractionLogRepository repository) {
        this.repository = repository;
    }

    public InteractionLog saveInteraction(
            UUID sessionId,
            Long studentId,
            Long taskId,
            Integer attemptNo,
            String code,
            AnalyzerResult analyzerResult,
            StudentContextDto context,
            FeedbackAction action,
            String policyVersion,
            String feedbackText,
            String feedbackSource,
            int policyTimeMs,
            int feedbackTimeMs
    ) {
        int totalLatency = analyzerResult.getAnalysisTimeMs() + policyTimeMs + feedbackTimeMs;

        InteractionLog log = InteractionLog.builder()
                .sessionId(sessionId)
                .studentId(studentId)
                .taskId(taskId)
                .attemptNo(attemptNo)
                .errorType(analyzerResult.getErrorType())
                .severity(analyzerResult.getSeverity())
                .sameErrorCount(context.getSameErrorCount())
                .totalErrorsSeen(context.getTotalErrorsSeen())
                .compileSuccess(analyzerResult.isCompileSuccess())
                .testsPassed(analyzerResult.getTestsPassed())
                .testsFailed(analyzerResult.getTestsFailed())
                .lastFeedbackAction(context.getLastFeedbackAction())
                .lastFeedbackSuccess(context.getLastFeedbackSuccess())
                .totalFeedbackCountInSession(context.getTotalFeedbackCountInSession())
                .feedbackAction(action.name())
                .feedbackText(feedbackText)
                .feedbackSource(feedbackSource)
                .policyVersion(policyVersion)
                .feedbackVersion("template-v1")
                .analysisTimeMs(analyzerResult.getAnalysisTimeMs())
                .policyTimeMs(policyTimeMs)
                .feedbackTimeMs(feedbackTimeMs)
                .totalLatencyMs(totalLatency)
                .resolvedAfterFeedback(false)
                .directSolutionViolation(false)
                .codeHash(hash(code))
                .codeLines(analyzerResult.getCodeLines())
                .hasSuspiciousRegion(hasText(analyzerResult.getSuspiciousRegion()))
                .suspiciousRegion(analyzerResult.getSuspiciousRegion())
                .build();

        return repository.save(log);
    }

    @Transactional
    public InteractionResultResponse updateInteractionResult(
            UUID interactionId,
            Boolean resolvedAfterFeedback,
            Integer fixedAfterMs
    ) {
        InteractionLog log = repository.findById(interactionId)
                .orElseThrow(() -> new IllegalArgumentException("Interaction not found: " + interactionId));

        log.setResolvedAfterFeedback(resolvedAfterFeedback);
        log.setFixedAfterMs(fixedAfterMs);

        return InteractionResultResponse.builder()
                .interactionId(log.getInteractionId())
                .resolvedAfterFeedback(log.getResolvedAfterFeedback())
                .fixedAfterMs(log.getFixedAfterMs())
                .message("Interaction result updated successfully")
                .build();
    }

    public StudentContextDto buildStudentContext(UUID sessionId, Long studentId, Long taskId, String currentErrorType) {
        List<InteractionLog> recent = repository.findTop20BySessionIdOrderByCreatedAtDesc(sessionId);

        int sameErrorCount = 0;
        int totalErrorsSeen = recent.size();
        String lastFeedbackAction = null;
        Boolean lastFeedbackSuccess = null;
        int totalFeedbackCountInSession = recent.size();

        for (InteractionLog log : recent) {
            if (lastFeedbackAction == null) {
                lastFeedbackAction = log.getFeedbackAction();
                lastFeedbackSuccess = log.getResolvedAfterFeedback();
            }
            if (currentErrorType != null && currentErrorType.equals(log.getErrorType())) {
                sameErrorCount++;
            } else {
                break;
            }
        }

        StudentContextDto dto = new StudentContextDto();
        dto.setSameErrorCount(sameErrorCount + 1);
        dto.setTotalErrorsSeen(totalErrorsSeen);
        dto.setLastFeedbackAction(lastFeedbackAction);
        dto.setLastFeedbackSuccess(lastFeedbackSuccess);
        dto.setTotalFeedbackCountInSession(totalFeedbackCountInSession);
        return dto;
    }

    @Transactional(readOnly = true)
    public byte[] exportPolicyDataset(boolean resolvedOnly) {
        List<InteractionLog> rows = resolvedOnly
                ? repository.findByResolvedAfterFeedbackTrueOrderByCreatedAtAsc()
                : repository.findAllByOrderByCreatedAtAsc();

        StringBuilder csv = new StringBuilder();
        csv.append(String.join(",",
                "interaction_id",
                "student_id",
                "task_id",
                "policy_version",
                "created_at",
                "error_type",
                "severity",
                "compile_success",
                "tests_passed",
                "tests_failed",
                "same_error_count",
                "total_errors_seen",
                "attempt_no",
                "last_feedback_action",
                "last_feedback_success",
                "has_suspicious_region",
                "code_lines",
                "total_feedback_count_in_session",
                "feedback_action",
                "resolved_after_feedback",
                "fixed_after_ms",
                "suspicious_region"
        )).append('\n');

        for (InteractionLog row : rows) {
            csv.append(toCsvRow(row)).append('\n');
        }

        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String hash(String value) {
        if (value == null) return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash code", e);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String toCsvRow(InteractionLog row) {
        return String.join(",",
                escape(row.getInteractionId()),
                escape(row.getStudentId()),
                escape(row.getTaskId()),
                escape(row.getPolicyVersion()),
                escape(row.getCreatedAt()),
                escape(row.getErrorType()),
                escape(row.getSeverity()),
                escape(row.getCompileSuccess()),
                escape(row.getTestsPassed()),
                escape(row.getTestsFailed()),
                escape(row.getSameErrorCount()),
                escape(row.getTotalErrorsSeen()),
                escape(row.getAttemptNo()),
                escape(row.getLastFeedbackAction()),
                escape(row.getLastFeedbackSuccess()),
                escape(row.getHasSuspiciousRegion()),
                escape(row.getCodeLines()),
                escape(row.getTotalFeedbackCountInSession()),
                escape(row.getFeedbackAction()),
                escape(row.getResolvedAfterFeedback()),
                escape(row.getFixedAfterMs()),
                escape(row.getSuspiciousRegion())
        );
    }

    private String escape(Object value) {
        if (value == null) {
            return "";
        }

        String text = String.valueOf(value);
        String escaped = text.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n") || escaped.contains("\r")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }
}
