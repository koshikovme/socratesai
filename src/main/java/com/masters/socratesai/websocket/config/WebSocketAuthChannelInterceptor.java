package com.masters.socratesai.websocket.config;

import com.masters.socratesai.security.CustomUserDetailsService;
import com.masters.socratesai.security.JwtService;
import com.masters.socratesai.security.SecurityUserDetails;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    public WebSocketAuthChannelInterceptor(
            JwtService jwtService,
            CustomUserDetailsService userDetailsService
    ) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() != StompCommand.CONNECT || accessor.getUser() != null) {
            return message;
        }

        try {
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
                return message;
            }

            String token = authHeader.substring(7);
            String username = jwtService.extractUsername(token);
            if (!StringUtils.hasText(username)) {
                return message;
            }

            SecurityUserDetails userDetails = userDetailsService.loadUserByUsername(username);
            if (!jwtService.isTokenValid(token, userDetails)) {
                return message;
            }

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    userDetails,
                    null,
                    userDetails.getAuthorities()
            );
            accessor.setUser(authentication);
            return message;
        } catch (RuntimeException ex) {
            return message;
        }
    }
}
