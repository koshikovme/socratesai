package com.masters.socratesai.mentor.service;

import com.masters.socratesai.analyzer.dto.AnalyzerResult;
import com.masters.socratesai.interaction.model.InteractionLog;
import com.masters.socratesai.interaction.service.InteractionLogService;
import com.masters.socratesai.mentor.dto.MentorRequest;
import com.masters.socratesai.mentor.dto.MentorResponse;
import com.masters.socratesai.mentor.dto.StudentContextDto;
import com.masters.socratesai.mentor.feedback.FeedbackGenerationService;
import com.masters.socratesai.mentor.model.FeedbackAction;
import com.masters.socratesai.mentor.policy.MentorPolicyService;
import com.masters.socratesai.mentor.policy.PolicyDecision;
import com.masters.socratesai.session.service.StudentTaskSessionService;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class MentorService {

    private final StudentContextService studentContextService;
    private final MentorPolicyService mentorPolicyService;
    private final FeedbackGenerationService feedbackGenerationService;
    private final InteractionLogService interactionLogService;
    private final StudentTaskSessionService sessionService;

    public MentorService(
            StudentContextService studentContextService,
            MentorPolicyService mentorPolicyService,
            FeedbackGenerationService feedbackGenerationService,
            InteractionLogService interactionLogService,
            StudentTaskSessionService sessionService
    ) {
        this.studentContextService = studentContextService;
        this.mentorPolicyService = mentorPolicyService;
        this.feedbackGenerationService = feedbackGenerationService;
        this.interactionLogService = interactionLogService;
        this.sessionService = sessionService;
    }

    public MentorResponse mentor(MentorRequest request) {
        UUID sessionId = request.getSessionId();
        if (sessionId == null) {
            sessionId = sessionService.getOrCreateActiveSession(
                    request.getStudentId(),
                    request.getTaskId()
            ).getSessionId();
        }

        AnalyzerResult analyzer = request.getAnalyzerResult();

        StudentContextDto context = studentContextService.buildContext(
                request.getStudentId(),
                sessionId,
                request.getTaskId(),
                analyzer.getErrorType()
        );

        long policyStart = System.currentTimeMillis();
        PolicyDecision policyDecision = mentorPolicyService.decide(analyzer, context, request.getAttemptNo());
        FeedbackAction action = policyDecision.action();
        int policyTimeMs = (int) (System.currentTimeMillis() - policyStart);

        long feedbackStart = System.currentTimeMillis();
        String feedbackText = feedbackGenerationService.generate(
                action,
                analyzer,
                request.getCode(),
                null
        );
        int feedbackTimeMs = (int) (System.currentTimeMillis() - feedbackStart);

        InteractionLog savedLog = interactionLogService.saveInteraction(
                sessionId,
                request.getStudentId(),
                request.getTaskId(),
                request.getAttemptNo(),
                request.getCode(),
                analyzer,
                context,
                action,
                policyDecision.policyVersion(),
                feedbackText,
                feedbackGenerationService.getSource(),
                policyTimeMs,
                feedbackTimeMs
        );

        sessionService.incrementFeedbackCount(sessionId);

        MentorResponse response = new MentorResponse();
        response.setInteractionId(savedLog.getInteractionId());
        response.setAction(action.name());
        response.setFeedbackText(feedbackText);
        response.setErrorType(analyzer.getErrorType());
        response.setSuspiciousRegion(analyzer.getSuspiciousRegion());
        response.setCompileSuccess(analyzer.isCompileSuccess());
        response.setTestsPassed(analyzer.getTestsPassed());
        response.setTestsFailed(analyzer.getTestsFailed());
        response.setAnalysisTimeMs(analyzer.getAnalysisTimeMs());
        response.setSessionId(sessionId);

        return response;
    }
}
