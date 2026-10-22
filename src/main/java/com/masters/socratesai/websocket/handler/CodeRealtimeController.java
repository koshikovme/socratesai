package com.masters.socratesai.websocket.handler;

import com.masters.socratesai.mentor.nativecontext.NativeMentorCoordinator;
import com.masters.socratesai.websocket.config.MentorSocketSessions;
import com.masters.socratesai.websocket.dto.CodeUpdateMessage;
import com.masters.socratesai.websocket.dto.FeedbackWsMessage;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;

@Controller @RequiredArgsConstructor
public class CodeRealtimeController {
    private final NativeMentorCoordinator coordinator;
    private final MentorSocketSessions sessions;
    private final com.masters.socratesai.websocket.service.MentorSocketSender sender;

    @MessageMapping("/code.update")
    public void processCode(@Payload CodeUpdateMessage message, SimpMessageHeaderAccessor headers) {
        var actor = sessions.require(headers.getSessionId()).getIdentity();
        try {
            coordinator.submit(actor, headers.getSessionId(), message).whenComplete((feedback, error) -> {
                if (!coordinator.current(actor, headers.getSessionId(), message)) return;
                if (error == null) sender.feedback(actor, headers.getSessionId(), feedback);
                else if (!(error instanceof com.masters.socratesai.mentor.nativecontext.SupersededMentorSnapshot)) sender.error(actor, headers.getSessionId(), error);
            });
        } catch (RuntimeException ex) { sender.error(actor, headers.getSessionId(), ex); }
    }

    @MessageExceptionHandler
    public void error(Exception ignored, SimpMessageHeaderAccessor headers) {
        var actor = sessions.require(headers.getSessionId()).getIdentity();
        sender.error(actor, headers.getSessionId(), new com.masters.socratesai.common.api.ApiException(com.masters.socratesai.common.api.ApiErrorCode.INVALID_REQUEST));
    }
}
