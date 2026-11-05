package com.masters.socratesai.mentor.nativecontext;

import com.masters.socratesai.mentor.feedback.port.NativeHintPort;
import com.masters.socratesai.mentor.model.FeedbackAction;
import com.masters.socratesai.mentor.policy.MentorPolicyService;
import com.masters.socratesai.common.model.EvidenceOrigin;
import com.masters.socratesai.execution.model.ExecutionVerdict;
import com.masters.socratesai.websocket.dto.CodeUpdateMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** No transaction spans inference or an optional language-model call. */
@Service @RequiredArgsConstructor
public class NativeMentorService {
    private final NativeMentorStore store;
    private final MentorPolicyService policy;
    private final NativeHintPort hints;
    public ComputedMentorFeedback compute(MentorIdentity actor, CodeUpdateMessage message) {
        var prepared = store.prepare(actor, message); var evidence = prepared.getEvidence(); var analyzer = evidence.features(message.getCode());
        if (message.getCode().isBlank() || message.getCodeRevision() == 0) {
            var feedback = base(prepared, message).action(FeedbackAction.GUIDING_QUESTION.name()).policyMode("READY")
                    .feedbackSource("onboarding").policyVersion("native-ai-v2")
                    .feedbackText("Write your logic in solve, then pause or click Ask mentor for an AI hint. You do not need to run samples first.").build();
            return new ComputedMentorFeedback(prepared, feedback, 0, 0);
        }
        if (evidence.isExecutionKnown() && evidence.getOrigin() == EvidenceOrigin.JUDGED_SUBMISSION && evidence.getVerdict() == ExecutionVerdict.ACCEPTED) {
            var feedback = base(prepared, message).action(FeedbackAction.NO_FEEDBACK.name()).policyMode("JUDGED")
                    .feedbackSource("execution").policyVersion("judged-feedback-v1").mentorState("accepted")
                    .feedbackText("This submission passed the complete test suite. You can review its clarity or move to another problem.").build();
            return new ComputedMentorFeedback(prepared, feedback, 0, 0);
        }
        long start = System.nanoTime();
        var selected = policy.decideNative(analyzer, prepared.getHistory(), prepared.getFormalAttempts(), message.getCode(), message.getLanguageKey(), evidence.isExecutionKnown());
        int policyMs = (int) ((System.nanoTime() - start) / 1000000);
        var decision = selected.decision();
        start = System.nanoTime();
        var generated = hints.generateNativeHint(actor.getStudentId(), message.getLanguageKey(),
                message.getCode(), prepared.getPublicStatement(), evidence, selected);
        boolean classified = "ML".equals(selected.mode());
        var action = classified && decision.action() != FeedbackAction.NO_FEEDBACK ? decision.action() : generated.action();
        int feedbackMs = (int) ((System.nanoTime() - start) / 1000000);
        var feedback = base(prepared, message)
                .action(action.name()).feedbackText(generated.hint()).feedbackSource("gemini")
                .policyVersion(decision.policyVersion()).policyMode(selected.mode()).fallbackReason(selected.fallbackReason())
                .mentorState(classified ? decision.mentorState() : generated.mentorState())
                .mentorStateConfidence(classified ? decision.confidence() : null).build();
        return new ComputedMentorFeedback(prepared, feedback, policyMs, feedbackMs);
    }

    private NativeMentorFeedback.NativeMentorFeedbackBuilder base(PreparedMentorContext prepared, CodeUpdateMessage message) {
        var evidence = prepared.getEvidence();
        return NativeMentorFeedback.builder().sessionId(prepared.getSessionId()).editorSessionId(message.getEditorSessionId()).problemId(message.getProblemId())
                .problemVersionId(message.getProblemVersionId()).languageKey(message.getLanguageKey()).codeRevision(message.getCodeRevision()).codeHash(prepared.getSourceHash())
                .evidenceOrigin(evidence.getOrigin()).executionKnown(evidence.isExecutionKnown()).compileSuccess(evidence.getCompileSuccess()).testsPassed(evidence.getTestsPassed())
                .testsFailed(evidence.getTestsFailed()).verdict(evidence.getVerdict());
    }
    public NativeMentorFeedback save(MentorIdentity actor, CodeUpdateMessage message, ComputedMentorFeedback computed) {
        return store.save(actor, message, computed.getPrepared(), computed.getFeedback(), computed.getPolicyMs(), computed.getFeedbackMs());
    }
}
