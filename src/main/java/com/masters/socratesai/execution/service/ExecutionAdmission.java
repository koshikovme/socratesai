package com.masters.socratesai.execution.service;

import com.masters.socratesai.common.api.*;
import com.masters.socratesai.execution.repo.ExecutionJobRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import com.masters.socratesai.execution.model.ExecutionStatus;
import java.util.List;

@Component @RequiredArgsConstructor @Transactional(propagation = Propagation.MANDATORY)
public class ExecutionAdmission {
    private static final List<ExecutionStatus> ACTIVE = List.of(ExecutionStatus.QUEUED, ExecutionStatus.DISPATCHING, ExecutionStatus.RUNNING);
    private final EntityManager entityManager;
    private final ExecutionJobRepository jobs;
    private final JudgingProperties properties;
    /** Short DB-wide admission mutex; shared by all instances and execution modes. */
    public void lock() { entityManager.createNativeQuery("select pg_advisory_xact_lock(7348251901)").getResultList(); }
    public void capacity(Long studentId) {
        if (jobs.countAdmittedByStudent(studentId, ACTIVE) + jobs.countAdmittedRunsByStudent(studentId, ACTIVE) >= properties.getMaxPerUser()
                || jobs.countAdmitted(ACTIVE) >= properties.getMaxQueued()) throw new ApiException(ApiErrorCode.CAPACITY_EXCEEDED);
    }
}
