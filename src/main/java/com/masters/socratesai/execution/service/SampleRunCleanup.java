package com.masters.socratesai.execution.service;

import com.masters.socratesai.execution.model.*;
import com.masters.socratesai.execution.repo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;

/** Never deletes graded submissions or their progress evidence. */
@Service @RequiredArgsConstructor
public class SampleRunCleanup {
    private final ExecutionRunRepository runs;
    private final ExecutionJobRepository jobs;
    private final ExecutionCaseResultRepository cases;
    private final Clock clock;
    @Transactional
    public void removeExpired() {
        runs.findAllByExpiresAtBeforeOrderByExpiresAt(OffsetDateTime.now(clock), PageRequest.of(0, 10)).forEach(run -> {
            if (run.getMode() != ExecutionMode.SAMPLE_RUN) return;
            jobs.findByRunId(run.getId()).flatMap(job -> jobs.lockById(job.getId())).ifPresent(job -> {
                if (job.getStatus() == ExecutionStatus.FINISHED || (job.getStatus() == ExecutionStatus.SYSTEM_ERROR && !job.getRetryAllowed())) {
                    cases.deleteCheckpoints(job.getId()); jobs.delete(job); jobs.flush(); runs.delete(run);
                }
            });
        });
    }
}
