package com.masters.socratesai.session.service;

import com.masters.socratesai.session.model.StudentTaskSession;
import com.masters.socratesai.session.repo.StudentTaskSessionRepository;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class StudentTaskSessionService {

    private final StudentTaskSessionRepository repository;

    public StudentTaskSessionService(StudentTaskSessionRepository repository) {
        this.repository = repository;
    }

    public StudentTaskSession getOrCreateActiveSession(Long studentId, Long taskId) {
        return repository.findByStudentIdAndTaskIdAndEndedAtIsNull(studentId, taskId)
                .orElseGet(() -> repository.save(
                        StudentTaskSession.builder()
                                .sessionId(UUID.randomUUID())
                                .studentId(studentId)
                                .taskId(taskId)
                                .startedAt(OffsetDateTime.now())
                                .finalStatus("IN_PROGRESS")
                                .totalAttempts(0)
                                .totalFeedbackCount(0)
                                .build()
                ));
    }

    public void incrementAttempts(UUID sessionId) {
        StudentTaskSession session = repository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
        session.setTotalAttempts(session.getTotalAttempts() + 1);
        repository.save(session);
    }

    public void incrementFeedbackCount(UUID sessionId) {
        StudentTaskSession session = repository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
        session.setTotalFeedbackCount(session.getTotalFeedbackCount() + 1);
        repository.save(session);
    }

    public void finishSession(UUID sessionId, String finalStatus) {
        StudentTaskSession session = repository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
        session.setEndedAt(OffsetDateTime.now());
        session.setFinalStatus(finalStatus);
        repository.save(session);
    }
}