package com.masters.socratesai.websocket.config;

import org.springframework.messaging.*;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;
import java.nio.charset.StandardCharsets;

/** Never reflect an exception/failed message containing credentials or source. */
@Component @lombok.RequiredArgsConstructor
public class SafeStompErrorHandler extends StompSubProtocolErrorHandler {
    @org.springframework.beans.factory.annotation.Qualifier("clientOutboundChannel")
    private final org.springframework.beans.factory.ObjectProvider<MessageChannel> outbound;

    public void reject(String sessionId) {
        var frame = handleClientMessageProcessingError(null, null);
        var headers = StompHeaderAccessor.wrap(frame); headers.setSessionId(sessionId);
        outbound.getObject().send(MessageBuilder.createMessage(frame.getPayload(), headers.getMessageHeaders()));
    }
    @Override public Message<byte[]> handleClientMessageProcessingError(Message<byte[]> client, Throwable error) {
        var headers = StompHeaderAccessor.create(StompCommand.ERROR);
        headers.setMessage("Request rejected");
        return MessageBuilder.createMessage("{\"code\":\"REQUEST_REJECTED\",\"message\":\"Request rejected\"}".getBytes(StandardCharsets.UTF_8), headers.getMessageHeaders());
    }
}
