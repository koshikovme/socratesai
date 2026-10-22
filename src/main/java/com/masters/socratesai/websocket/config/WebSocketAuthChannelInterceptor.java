package com.masters.socratesai.websocket.config;

import com.masters.socratesai.common.api.*;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component @RequiredArgsConstructor
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private final MentorSocketSessions sessions;
    private final SafeStompErrorHandler errors;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        try { return authorize(message); }
        catch (RuntimeException ex) {
            var accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
            if (accessor != null) {
                sessions.disconnect(accessor.getSessionId());
                try { errors.reject(accessor.getSessionId()); } catch (RuntimeException closedTransport) { /* fail closed without reflecting the failed frame */ }
            }
            return null;
        }
    }

    private Message<?> authorize(Message<?> message) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) throw new ApiException(ApiErrorCode.FORBIDDEN);
        var command = accessor.getCommand(); var id = accessor.getSessionId();
        var authorization = accessor.getNativeHeader("Authorization");
        if (accessor.isMutable()) {
            accessor.removeNativeHeader("Authorization"); accessor.removeNativeHeader("login"); accessor.removeNativeHeader("passcode");
        }
        if (command == StompCommand.CONNECT || command == StompCommand.STOMP) {
            var headers = authorization;
            if (headers == null || headers.size() != 1) throw new ApiException(ApiErrorCode.AUTHENTICATION_REQUIRED);
            var authenticated = sessions.connect(id, headers.getFirst());
            accessor.setUser(authenticated.getPrincipal());
        } else if (command == StompCommand.DISCONNECT) { sessions.disconnect(id); }
        else {
            if (command == StompCommand.SEND || command == StompCommand.SUBSCRIBE) sessions.rateSession(id);
            var authenticated = sessions.require(id); accessor.setUser(authenticated.getPrincipal());
            if (command == StompCommand.SEND) {
                if (!"/app/code.update".equals(accessor.getDestination())) throw new ApiException(ApiErrorCode.FORBIDDEN);
                if (message.getPayload() instanceof byte[] bytes && bytes.length > 131072) throw new ApiException(ApiErrorCode.INVALID_REQUEST);
            } else if (command == StompCommand.SUBSCRIBE) {
                if (!"/user/queue/feedback".equals(accessor.getDestination())) throw new ApiException(ApiErrorCode.FORBIDDEN);
                sessions.subscribe(id, accessor.getSubscriptionId());
            } else if (command == StompCommand.UNSUBSCRIBE) sessions.unsubscribe(id, accessor.getSubscriptionId());
            else if (command != null) throw new ApiException(ApiErrorCode.FORBIDDEN);
        }
        if (accessor.isMutable()) {
            accessor.removeNativeHeader("Authorization"); accessor.removeNativeHeader("login"); accessor.removeNativeHeader("passcode");
        }
        return message;
    }
}
