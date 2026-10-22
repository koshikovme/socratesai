package com.masters.socratesai.websocket.config;

import com.masters.socratesai.common.api.*;
import com.masters.socratesai.mentor.nativecontext.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import java.time.Clock;
import java.util.*;

/** Never stores a bearer token; principal and signed expiry are bound at CONNECT. */
@Component @RequiredArgsConstructor
public class MentorSocketSessions {
    private final MentorJwtAuthentication authentication;
    private final MentorRealtimeProperties properties;
    private final Clock clock;
    private final Map<String, MentorAuthentication> sessions = new HashMap<>();
    private final Map<Long, Window> windows = new HashMap<>();
    private final Map<String, Set<String>> subscriptions = new HashMap<>();
    public synchronized MentorAuthentication connect(String id, String authorization) {
        var authenticated = authentication.authenticate(authorization);
        if (id == null || sessions.containsKey(id)) throw new ApiException(ApiErrorCode.FORBIDDEN);
        reap();
        if (sessions.size() >= properties.getMaxSessions() || sessions.values().stream()
                .filter(item -> item.getIdentity().getStudentId().equals(authenticated.getIdentity().getStudentId())).count() >= properties.getSessionsPerUser())
            throw new ApiException(ApiErrorCode.CAPACITY_EXCEEDED);
        sessions.put(id, authenticated); return authenticated;
    }
    public synchronized MentorAuthentication require(String id) {
        var session = sessions.get(id);
        if (session == null) throw new ApiException(ApiErrorCode.AUTHENTICATION_REQUIRED);
        try { return authentication.refresh(session.getIdentity()); }
        catch (ApiException ex) { sessions.remove(id); throw ex; }
    }
    public synchronized boolean active(String id, MentorIdentity identity) {
        var session = sessions.get(id);
        return session != null && session.getIdentity().getStudentId().equals(identity.getStudentId()) && session.getIdentity().getExpiresAt().isAfter(clock.instant());
    }
    public synchronized void rateSession(String id) {
        var session = sessions.get(id);
        if (session == null || !session.getIdentity().getExpiresAt().isAfter(clock.instant())) throw new ApiException(ApiErrorCode.AUTHENTICATION_REQUIRED);
        rate(session.getIdentity().getStudentId());
    }
    public synchronized void subscribe(String session, String subscription) {
        if (subscription == null || subscription.length() > 120) throw new ApiException(ApiErrorCode.INVALID_REQUEST);
        var ids = subscriptions.computeIfAbsent(session, ignored -> new HashSet<>());
        if (!ids.isEmpty() && !ids.contains(subscription)) throw new ApiException(ApiErrorCode.CAPACITY_EXCEEDED);
        ids.add(subscription);
    }
    public synchronized void unsubscribe(String session, String subscription) { var ids = subscriptions.get(session); if (ids != null) ids.remove(subscription); }
    public synchronized void rate(Long studentId) {
        long second = clock.millis() / 1000;
        var window = windows.get(studentId);
        if (window == null || window.second() != second) window = new Window(second, 0);
        if (window.count() >= properties.getMessagesPerSecond()) throw new ApiException(ApiErrorCode.CAPACITY_EXCEEDED);
        windows.put(studentId, new Window(second, window.count() + 1));
    }
    public synchronized void disconnect(String id) { sessions.remove(id); subscriptions.remove(id); }
    @EventListener public void disconnected(SessionDisconnectEvent event) { disconnect(event.getSessionId()); }
    @Scheduled(fixedDelay = 60000) public synchronized void reap() {
        sessions.entrySet().removeIf(entry -> !entry.getValue().getIdentity().getExpiresAt().isAfter(clock.instant()));
        subscriptions.keySet().retainAll(sessions.keySet());
        long second = clock.millis() / 1000; windows.entrySet().removeIf(entry -> entry.getValue().second() < second);
    }
    private record Window(long second, int count) {}
}
