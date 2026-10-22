package com.masters.socratesai.execution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.*;

/** Process-local notifications are advisory; the committed HTTP status is durable. */
@Component @RequiredArgsConstructor @Slf4j
public class JudgingEvents {
    private final ApplicationEventPublisher publisher;
    public void afterCommit(SubmissionJudged event) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                try { publisher.publishEvent(event); }
                catch (RuntimeException ex) { log.warn("Judging notification failed submissionId={}", event.submissionId()); }
            }
        });
    }
}
