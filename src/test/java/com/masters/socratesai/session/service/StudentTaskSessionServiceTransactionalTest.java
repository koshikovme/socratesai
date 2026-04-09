package com.masters.socratesai.session.service;

import com.masters.socratesai.session.model.StudentTaskSession;
import com.masters.socratesai.session.repo.StudentTaskSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import com.masters.socratesai.support.JpaIntegrationTestConfig;

import static org.assertj.core.api.Assertions.assertThat;

@SpringJUnitConfig(JpaIntegrationTestConfig.class)
@Transactional
class StudentTaskSessionServiceTransactionalTest {

    @Autowired
    private StudentTaskSessionService service;

    @Autowired
    private StudentTaskSessionRepository repository;

    @Test
    void shouldReuseExistingActiveSession() {
        StudentTaskSession first = service.getOrCreateActiveSession(11L, 22L);
        StudentTaskSession second = service.getOrCreateActiveSession(11L, 22L);

        assertThat(second.getSessionId()).isEqualTo(first.getSessionId());
        assertThat(repository.findByStudentIdAndTaskIdAndEndedAtIsNull(11L, 22L))
                .isPresent()
                .get()
                .extracting(StudentTaskSession::getSessionId)
                .isEqualTo(first.getSessionId());
    }

    @Test
    void shouldPersistAttemptCountersAndFinalStatus() {
        StudentTaskSession session = service.getOrCreateActiveSession(30L, 40L);
        UUID sessionId = session.getSessionId();

        service.incrementAttempts(sessionId);
        service.incrementFeedbackCount(sessionId);
        service.finishSession(sessionId, "RESOLVED");

        StudentTaskSession updated = repository.findById(sessionId).orElseThrow();
        assertThat(updated.getTotalAttempts()).isEqualTo(1);
        assertThat(updated.getTotalFeedbackCount()).isEqualTo(1);
        assertThat(updated.getFinalStatus()).isEqualTo("RESOLVED");
        assertThat(updated.getEndedAt()).isNotNull();
    }
}
