package com.masters.socratesai.websocket.service;

import com.masters.socratesai.common.api.*;
import com.masters.socratesai.mentor.nativecontext.*;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.*;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component @RequiredArgsConstructor
public class MentorSocketSender {
    private final SimpMessagingTemplate messaging;
    public void feedback(MentorIdentity actor, String sessionId, NativeMentorFeedback feedback) { send(actor, sessionId, feedback); }
    public void error(MentorIdentity actor, String sessionId, Throwable failure) {
        var code = failure instanceof ApiException safe ? safe.getCode() : ApiErrorCode.INTERNAL_ERROR;
        send(actor, sessionId, new ApiError(code.name(), code.getMessage(), UUID.randomUUID().toString()));
    }
    private void send(MentorIdentity actor, String sessionId, Object body) {
        var headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        headers.setSessionId(sessionId); headers.setLeaveMutable(true);
        messaging.convertAndSendToUser(actor.getUsername(), "/queue/feedback", body, headers.getMessageHeaders());
    }
}
