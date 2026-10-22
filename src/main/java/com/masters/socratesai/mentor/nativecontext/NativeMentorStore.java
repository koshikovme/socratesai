package com.masters.socratesai.mentor.nativecontext;

import com.masters.socratesai.common.api.*;
import com.masters.socratesai.common.model.ContextSource;
import com.masters.socratesai.execution.service.ExecutionSnapshots;
import com.masters.socratesai.interaction.model.InteractionLog;
import com.masters.socratesai.interaction.repo.InteractionLogRepository;
import com.masters.socratesai.mentor.dto.StudentContextDto;
import com.masters.socratesai.session.model.StudentTaskSession;
import com.masters.socratesai.session.repo.StudentTaskSessionRepository;
import com.masters.socratesai.submission.repo.StudentProblemProgressRepository;
import com.masters.socratesai.task.repo.ProblemLanguageConfigRepository;
import com.masters.socratesai.task.service.ProblemAccess;
import com.masters.socratesai.user.repo.UserRepository;
import com.masters.socratesai.websocket.dto.CodeUpdateMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class NativeMentorStore {
    private final UserRepository users;
    private final ProblemAccess access;
    private final ProblemLanguageConfigRepository languages;
    private final StudentTaskSessionRepository sessions;
    private final StudentProblemProgressRepository progress;
    private final InteractionLogRepository logs;
    private final MentorEvidenceResolver evidence;
    private final Clock clock;

    @Transactional
    public PreparedMentorContext prepare(MentorIdentity actor, CodeUpdateMessage message) {
        requireActor(actor, true);
        var version = access.publishedAuthenticated(message.getProblemId(), message.getProblemVersionId());
        if (languages.findByProblemVersion_IdAndLanguageKey(version.getId(), message.getLanguageKey()).isEmpty()) throw new ApiException(ApiErrorCode.INVALID_REQUEST);
        var session = sessions.findByStudentIdAndEditorSessionIdAndEndedAtIsNull(actor.getStudentId(), message.getEditorSessionId())
                .orElseGet(() -> sessions.saveAndFlush(StudentTaskSession.builder().studentId(actor.getStudentId()).taskId(message.getProblemId())
                        .problemId(message.getProblemId()).problemVersionId(message.getProblemVersionId()).languageKey(message.getLanguageKey())
                        .editorSessionId(message.getEditorSessionId()).contextSource(ContextSource.NATIVE).finalStatus("IN_PROGRESS").build()));
        if (!Objects.equals(session.getProblemId(), message.getProblemId()) || !Objects.equals(session.getProblemVersionId(), message.getProblemVersionId())
                || !Objects.equals(session.getLanguageKey(), message.getLanguageKey())) throw new ApiException(ApiErrorCode.CONFLICT);
        var hash = ExecutionSnapshots.hash(message.getCode());
        var observed = evidence.resolve(actor.getStudentId(), message, hash);
        var recent = logs.findTop20BySessionIdOrderByCreatedAtDesc(session.getSessionId());
        var history = new StudentContextDto();
        var latest = recent.isEmpty() ? null : recent.getFirst();
        String errorType = observed.features(message.getCode()).getErrorType();
        history.setSameErrorCount((int) recent.stream().takeWhile(log -> errorType.equals(log.getErrorType())).count() + 1);
        history.setTotalErrorsSeen(recent.size()); history.setTotalFeedbackCountInSession(session.getTotalFeedbackCount());
        history.setLastFeedbackAction(latest == null ? null : latest.getFeedbackAction()); history.setLastFeedbackSuccess(latest == null ? null : latest.getResolvedAfterFeedback());
        // A read only lookup: editor activity never creates/increments the formal progress counter.
        int attempts = progress.findByStudentIdAndProblemId(actor.getStudentId(), message.getProblemId()).map(value -> value.getFormalAttempts()).orElse(0);
        return PreparedMentorContext.builder().sessionId(session.getSessionId()).evidence(observed).history(history).formalAttempts(attempts)
                .sourceHash(hash).publicStatement(version.getStatement().substring(0, Math.min(2000, version.getStatement().length()))).build();
    }

    @Transactional
    public NativeMentorFeedback save(MentorIdentity actor, CodeUpdateMessage message, PreparedMentorContext prepared, NativeMentorFeedback feedback,
                                      int policyMs, int feedbackMs) {
        requireActor(actor, false); access.publishedAuthenticated(message.getProblemId(), message.getProblemVersionId());
        var session = sessions.lockById(prepared.getSessionId()).orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND));
        if (!session.getStudentId().equals(actor.getStudentId()) || session.getEndedAt() != null) throw new ApiException(ApiErrorCode.NOT_FOUND);
        var observed = prepared.getEvidence(); var features = observed.features(message.getCode()); var history = prepared.getHistory();
        var log = logs.saveAndFlush(InteractionLog.builder().studentId(actor.getStudentId()).sessionId(session.getSessionId()).taskId(message.getProblemId())
                .problemId(message.getProblemId()).problemVersionId(message.getProblemVersionId()).languageKey(message.getLanguageKey()).contextSource(ContextSource.NATIVE)
                .editorSessionId(message.getEditorSessionId()).codeRevision(message.getCodeRevision()).codeHash(prepared.getSourceHash())
                .evidenceOrigin(observed.getOrigin()).executionKnown(observed.isExecutionKnown()).executionJobId(observed.getJobId()).submissionId(observed.getSubmissionId())
                .attemptNo(prepared.getFormalAttempts()).compileSuccess(observed.getCompileSuccess()).testsPassed(observed.getTestsPassed()).testsFailed(observed.getTestsFailed())
                .errorType(features.getErrorType()).severity(features.getSeverity()).codeLines(features.getCodeLines()).sameErrorCount(history.getSameErrorCount())
                .totalErrorsSeen(history.getTotalErrorsSeen()).lastFeedbackAction(history.getLastFeedbackAction()).lastFeedbackSuccess(history.getLastFeedbackSuccess())
                .totalFeedbackCountInSession(history.getTotalFeedbackCountInSession()).feedbackAction(feedback.getAction()).feedbackText(feedback.getFeedbackText())
                .feedbackSource(feedback.getFeedbackSource()).policyVersion(feedback.getPolicyVersion()).policyFallbackReason(feedback.getFallbackReason())
                .mentorState(feedback.getMentorState()).mentorStateConfidence(feedback.getMentorStateConfidence()).feedbackVersion("native-v1")
                .analysisTimeMs(0).policyTimeMs(policyMs).feedbackTimeMs(feedbackMs).totalLatencyMs(policyMs + feedbackMs).resolvedAfterFeedback(false).build());
        session.setTotalFeedbackCount(session.getTotalFeedbackCount() + 1);
        return feedback.toBuilder().interactionId(log.getInteractionId()).build();
    }

    private void requireActor(MentorIdentity actor, boolean lock) {
        var user = (lock ? users.lockById(actor.getStudentId()) : users.findById(actor.getStudentId())).orElseThrow(() -> new ApiException(ApiErrorCode.AUTHENTICATION_REQUIRED));
        if (!user.getEmail().equals(actor.getUsername()) || !actor.getExpiresAt().isAfter(clock.instant())) throw new ApiException(ApiErrorCode.AUTHENTICATION_REQUIRED);
    }
}
