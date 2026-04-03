package com.masters.socratesai.websocket.handler;

import com.masters.socratesai.mentor.dto.MentorAnalyzeRequest;
import com.masters.socratesai.mentor.dto.MentorResponse;
import com.masters.socratesai.mentor.service.MentorWorkflowService;
import com.masters.socratesai.websocket.dto.CodeUpdateMessage;
import com.masters.socratesai.websocket.dto.FeedbackWsMessage;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

@Controller
public class CodeRealtimeController {

    private final MentorWorkflowService mentorWorkflowService;

    public CodeRealtimeController(
            MentorWorkflowService mentorWorkflowService
    ) {
        this.mentorWorkflowService = mentorWorkflowService;
    }

    @MessageMapping("/code.update")
    @SendToUser("/queue/feedback")
    public FeedbackWsMessage processCode(CodeUpdateMessage message) {
        MentorAnalyzeRequest request = new MentorAnalyzeRequest();
        request.setStudentId(message.getStudentId());
        request.setTaskId(message.getTaskId());
        request.setLanguage(message.getLanguage());
        request.setCode(message.getCode());
        request.setAttemptNo(message.getAttemptNo());

        MentorResponse mentorResponse = mentorWorkflowService.analyzeAndMentor(request, false);

        return FeedbackWsMessage.builder()
                .interactionId(mentorResponse.getInteractionId())
                .sessionId(mentorResponse.getSessionId())
                .errorType(mentorResponse.getErrorType())
                .action(mentorResponse.getAction())
                .feedbackText(mentorResponse.getFeedbackText())
                .suspiciousRegion(mentorResponse.getSuspiciousRegion())
                .compileSuccess(mentorResponse.isCompileSuccess())
                .testsPassed(mentorResponse.getTestsPassed())
                .testsFailed(mentorResponse.getTestsFailed())
                .analysisTimeMs(mentorResponse.getAnalysisTimeMs())
                .build();
    }
}
