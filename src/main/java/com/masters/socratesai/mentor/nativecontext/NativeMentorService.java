package com.masters.socratesai.mentor.nativecontext;

import com.masters.socratesai.mentor.feedback.*;
import com.masters.socratesai.mentor.model.FeedbackAction;
import com.masters.socratesai.mentor.policy.MentorPolicyService;
import com.masters.socratesai.websocket.dto.CodeUpdateMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** No transaction spans inference or an optional language-model call. */
@Service @RequiredArgsConstructor
public class NativeMentorService {
    private final NativeMentorStore store;
    private final MentorPolicyService policy;
    private final FeedbackGenerationService generation;
    private final FeedbackTemplateService templates;
    public ComputedMentorFeedback compute(MentorIdentity actor, CodeUpdateMessage message) {
        var prepared = store.prepare(actor, message); var evidence = prepared.getEvidence(); var analyzer = evidence.features(message.getCode());
        long start = System.nanoTime();
        var selected = policy.decideNative(analyzer, prepared.getHistory(), prepared.getFormalAttempts(), message.getCode(), message.getLanguageKey(), evidence.isExecutionKnown());
        int policyMs = (int) ((System.nanoTime() - start) / 1000000);
        var decision = selected.decision();
        var action = !evidence.isExecutionKnown() && decision.action() == FeedbackAction.NO_FEEDBACK ? FeedbackAction.CONCEPTUAL_HINT : decision.action();
        start = System.nanoTime();
        var generated = "ML".equals(selected.mode()) && evidence.isExecutionKnown()
                ? generation.generateWithMetadata(action, analyzer, message.getCode().substring(0, Math.min(5000, message.getCode().length())), prepared.getPublicStatement())
                : new GeneratedFeedback(templates.generate(action, analyzer), "template");
        int feedbackMs = (int) ((System.nanoTime() - start) / 1000000);
        var feedback = NativeMentorFeedback.builder().sessionId(prepared.getSessionId()).editorSessionId(message.getEditorSessionId()).problemId(message.getProblemId())
                .problemVersionId(message.getProblemVersionId()).languageKey(message.getLanguageKey()).codeRevision(message.getCodeRevision()).codeHash(prepared.getSourceHash())
                .evidenceOrigin(evidence.getOrigin()).executionKnown(evidence.isExecutionKnown()).compileSuccess(evidence.getCompileSuccess()).testsPassed(evidence.getTestsPassed())
                .testsFailed(evidence.getTestsFailed()).verdict(evidence.getVerdict()).action(action.name()).feedbackText(generated.text()).feedbackSource(generated.source())
                .policyVersion(decision.policyVersion()).policyMode(selected.mode()).fallbackReason(selected.fallbackReason()).mentorState(decision.mentorState())
                .mentorStateConfidence(decision.confidence()).build();
        return new ComputedMentorFeedback(prepared, feedback, policyMs, feedbackMs);
    }
    public NativeMentorFeedback save(MentorIdentity actor, CodeUpdateMessage message, ComputedMentorFeedback computed) {
        return store.save(actor, message, computed.getPrepared(), computed.getFeedback(), computed.getPolicyMs(), computed.getFeedbackMs());
    }
}
