package com.masters.socratesai.websocket;

import com.masters.socratesai.mentor.nativecontext.*;
import com.masters.socratesai.websocket.config.*;
import org.junit.jupiter.api.*;
import org.springframework.messaging.*;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import java.time.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NativeSocketSecurityTest {
    private final MentorJwtAuthentication authentication = mock(MentorJwtAuthentication.class);
    private final MentorRealtimeProperties properties = new MentorRealtimeProperties();
    private final Clock clock = mock(Clock.class);
    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-22T00:00:00Z"));
    private final MentorIdentity identity = MentorIdentity.builder().studentId(7L).username("owner@example.test").expiresAt(now.get().plusSeconds(60)).build();
    private final MentorAuthentication authenticated = new MentorAuthentication(identity, new UsernamePasswordAuthenticationToken("owner", null, java.util.List.of()));
    private final SafeStompErrorHandler errors = mock(SafeStompErrorHandler.class);
    private MentorSocketSessions sessions;
    private WebSocketAuthChannelInterceptor interceptor;
    private final MessageChannel channel = mock(MessageChannel.class);
    @BeforeEach void setUp() {
        when(clock.instant()).thenAnswer(call -> now.get()); when(clock.millis()).thenAnswer(call -> now.get().toEpochMilli());
        when(authentication.authenticate("Bearer valid")).thenReturn(authenticated); when(authentication.refresh(identity)).thenReturn(authenticated);
        sessions = new MentorSocketSessions(authentication, properties, clock); interceptor = new WebSocketAuthChannelInterceptor(sessions, errors);
    }
    @Test void clientPrincipalAndLaterAuthorizationCannotOverrideTheConnectIdentity() {
        var connect = frame(StompCommand.CONNECT, "s"); connect.setUser(new UsernamePasswordAuthenticationToken("victim", "client-secret"));
        connect.setNativeHeader("Authorization", "Bearer valid"); assertThat(send(connect)).isNotNull();
        assertThat(connect.getUser().getName()).isEqualTo("owner"); assertThat(connect.getFirstNativeHeader("Authorization")).isNull();
        var update = frame(StompCommand.SEND, "s"); update.setDestination("/app/code.update"); update.setNativeHeader("Authorization", "Bearer victim-token");
        update.setUser(new UsernamePasswordAuthenticationToken("victim", "secret")); assertThat(send(update)).isNotNull();
        assertThat(update.getUser().getName()).isEqualTo("owner"); verify(authentication, never()).authenticate("Bearer victim-token");
    }
    @Test void anonymousExpiredAndForeignSubscriptionsAreRejected() {
        var anonymous = frame(StompCommand.SUBSCRIBE, "unknown"); anonymous.setDestination("/user/queue/feedback"); assertThat(send(anonymous)).isNull();
        connect(); var foreign = frame(StompCommand.SUBSCRIBE, "s"); foreign.setDestination("/user/victim/queue/feedback"); foreign.setSubscriptionId("0");
        assertThat(send(foreign)).isNull(); verify(errors).reject("s");
        connect(); now.set(now.get().plusSeconds(61)); var expired = frame(StompCommand.SEND, "s"); expired.setDestination("/app/code.update"); assertThat(send(expired)).isNull();
    }
    @Test void rateAndSubscriptionBudgetsApplyAcrossTheAuthenticatedAccount() {
        properties.setMessagesPerSecond(2); connect();
        var sub = frame(StompCommand.SUBSCRIBE, "s"); sub.setDestination("/user/queue/feedback"); sub.setSubscriptionId("0"); assertThat(send(sub)).isNotNull();
        var send = frame(StompCommand.SEND, "s"); send.setDestination("/app/code.update"); assertThat(send(send)).isNotNull();
        assertThat(send(frame(StompCommand.SEND, "s"))).isNull(); verify(errors).reject("s");
        connect(); var second = frame(StompCommand.CONNECT, "another"); second.setNativeHeader("Authorization", "Bearer valid"); assertThat(send(second)).isNotNull();
        var rateLimited = frame(StompCommand.SEND, "another"); rateLimited.setDestination("/app/code.update"); assertThat(send(rateLimited)).isNull();
    }
    private void connect() { var a = frame(StompCommand.CONNECT, "s"); a.setNativeHeader("Authorization", "Bearer valid"); assertThat(send(a)).isNotNull(); }
    private StompHeaderAccessor frame(StompCommand command, String id) { var a = StompHeaderAccessor.create(command); a.setSessionId(id); a.setLeaveMutable(true); return a; }
    private Message<?> send(StompHeaderAccessor a) { return interceptor.preSend(MessageBuilder.createMessage(new byte[0], a.getMessageHeaders()), channel); }
}
