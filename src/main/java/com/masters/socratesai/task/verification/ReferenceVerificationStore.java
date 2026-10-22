package com.masters.socratesai.task.verification;

import com.masters.socratesai.execution.domain.*;
import com.masters.socratesai.execution.model.*;
import com.masters.socratesai.task.repo.ReferenceVerificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class ReferenceVerificationStore {
    private final ReferenceVerificationRepository verifications;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void started(UUID id) {
        verifications.lockById(id).filter(record -> record.getStatus() == ExecutionStatus.QUEUED)
                .ifPresent(record -> record.setStatus(ExecutionStatus.RUNNING));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(UUID id, ExecutionBatchResult result, Throwable error) {
        var record = verifications.lockById(id).orElse(null);
        if (record == null || terminal(record)) return;
        var now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        record.setFinishedAt(now);
        if (!now.isBefore(record.getDeadlineAt())) fail(record, SandboxFailureCode.DEADLINE_EXCEEDED);
        else if (error != null) fail(record, failureCode(error));
        else if (result == null || result.getStatus() != ExecutionStatus.FINISHED || result.getVerdict() == null
                || result.getCases() == null || result.getCases().size() != record.getTestsTotal()) fail(record, SandboxFailureCode.INVALID_RESPONSE);
        else {
            int passed = (int) result.getCases().stream().filter(item -> item.getStatus() == ExecutionStatus.FINISHED
                    && item.getVerdict() == ExecutionVerdict.ACCEPTED).count();
            if (result.getVerdict() == ExecutionVerdict.ACCEPTED && passed != record.getTestsTotal()) fail(record, SandboxFailureCode.INVALID_RESPONSE);
            else { record.setStatus(ExecutionStatus.FINISHED); record.setVerdict(result.getVerdict()); record.setTestsPassed(passed); }
        }
    }

    private boolean terminal(ReferenceVerification record) {
        return record.getStatus() == ExecutionStatus.FINISHED || record.getStatus() == ExecutionStatus.SYSTEM_ERROR;
    }

    private void fail(ReferenceVerification record, SandboxFailureCode code) {
        record.setStatus(ExecutionStatus.SYSTEM_ERROR); record.setVerdict(null); record.setFailureCode(code.name());
    }

    private SandboxFailureCode failureCode(Throwable error) {
        Throwable current = error;
        for (int depth = 0; depth < 8 && current != null; depth++, current = current.getCause()) {
            if (current instanceof SandboxException exception) return exception.getCode();
        }
        return SandboxFailureCode.PROVIDER_INTERNAL_ERROR;
    }
}
