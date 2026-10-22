package com.masters.socratesai.execution.service;

import com.masters.socratesai.execution.model.*;
import org.junit.jupiter.api.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.*;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class JudgingEventsTest {
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    private final JudgingEvents events = new JudgingEvents(publisher);
    private final SubmissionJudged event = new SubmissionJudged(UUID.randomUUID(), 7L, ExecutionStatus.FINISHED, ExecutionVerdict.ACCEPTED);
    @BeforeEach void begin() { TransactionSynchronizationManager.initSynchronization(); }
    @AfterEach void end() { TransactionSynchronizationManager.clearSynchronization(); }
    @Test void notifiesOnlyAfterCommit() {
        events.afterCommit(event); verifyNoInteractions(publisher);
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(publisher).publishEvent(event);
    }
    @Test void rollbackDoesNotPublishACompletion() {
        events.afterCommit(event);
        TransactionSynchronizationManager.getSynchronizations().forEach(item -> item.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verifyNoInteractions(publisher);
    }
    @Test void notificationFailureCannotUndoCommittedEvidence() {
        doThrow(new IllegalStateException("synthetic-private-detail")).when(publisher).publishEvent(event);
        events.afterCommit(event);
        assertThatCode(() -> TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit)).doesNotThrowAnyException();
    }
}
