package com.masters.socratesai.task.verification;

import com.masters.socratesai.execution.domain.port.ReferenceExecutionPort;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.event.TransactionalEventListener;
import com.masters.socratesai.execution.domain.*;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@RequiredArgsConstructor
public class ReferenceVerificationDispatcher {
    private final ReferenceExecutionPort execution;
    private final ReferenceVerificationStore store;
    private final Executor executor;

    @TransactionalEventListener
    public void committed(ReferenceWork work) {
        try { executor.execute(() -> execute(work)); }
        catch (RejectedExecutionException ex) { store.complete(work.id(), null, new SandboxException(SandboxFailureCode.BUSY)); }
    }

    private void execute(ReferenceWork work) {
        try {
            store.started(work.id());
            execution.verify(work.batches(), work.deadline()).whenComplete((result, error) -> store.complete(work.id(), result, error));
        } catch (RuntimeException ex) {
            store.complete(work.id(), null, ex);
        }
    }
}
