package com.masters.socratesai.session.service;

import com.masters.socratesai.session.model.StudentTaskSession;
import com.masters.socratesai.session.repo.StudentTaskSessionRepository;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

@Service
public class StudentTaskSessionService {

    private final StudentTaskSessionRepository repository;
    private final ConcurrentHashMap<String, Object> creationLocks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Object> sessionLocks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, UUID> activeSessionCache = new ConcurrentHashMap<>();

    public StudentTaskSessionService(StudentTaskSessionRepository repository) {
        this.repository = repository;
    }

    public StudentTaskSession getOrCreateActiveSession(Long studentId, Long taskId) {
        String lockKey = studentId + ":" + taskId;
        synchronized (creationLocks.computeIfAbsent(lockKey, ignored -> new Object())) {
            UUID cachedSessionId = activeSessionCache.get(lockKey);
            if (cachedSessionId != null) {
                StudentTaskSession cached = repository.findById(cachedSessionId)
                        .filter(session -> session.getEndedAt() == null)
                        .orElse(null);
                if (cached != null) {
                    return cached;
                }
                activeSessionCache.remove(lockKey, cachedSessionId);
            }

            StudentTaskSession existing = repository.findByStudentIdAndTaskIdAndEndedAtIsNull(studentId, taskId)
                    .orElse(null);
            if (existing != null) {
                activeSessionCache.put(lockKey, existing.getSessionId());
                return existing;
            }

            StudentTaskSession created = repository.saveAndFlush(
                            StudentTaskSession.builder()
                                    .sessionId(UUID.randomUUID())
                                    .studentId(studentId)
                                    .taskId(taskId)
                                    .startedAt(OffsetDateTime.now())
                                    .finalStatus("IN_PROGRESS")
                                    .totalAttempts(0)
                                    .totalFeedbackCount(0)
                                    .build()
                    );
            activeSessionCache.put(lockKey, created.getSessionId());
            return created;
        }
    }

    public void incrementAttempts(UUID sessionId) {
        synchronized (sessionLocks.computeIfAbsent(sessionId, ignored -> new Object())) {
            StudentTaskSession session = repository.findById(sessionId)
                    .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
            session.setTotalAttempts(session.getTotalAttempts() + 1);
            repository.save(session);
        }
    }

    public void incrementFeedbackCount(UUID sessionId) {
        synchronized (sessionLocks.computeIfAbsent(sessionId, ignored -> new Object())) {
            StudentTaskSession session = repository.findById(sessionId)
                    .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
            session.setTotalFeedbackCount(session.getTotalFeedbackCount() + 1);
            repository.save(session);
        }
    }

    public void finishSession(UUID sessionId, String finalStatus) {
        synchronized (sessionLocks.computeIfAbsent(sessionId, ignored -> new Object())) {
            StudentTaskSession session = repository.findById(sessionId)
                    .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
            session.setEndedAt(OffsetDateTime.now());
            session.setFinalStatus(finalStatus);
            repository.save(session);
            activeSessionCache.remove(session.getStudentId() + ":" + session.getTaskId(), session.getSessionId());
        }
    }
}
