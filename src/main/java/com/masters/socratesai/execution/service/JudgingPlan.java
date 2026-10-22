package com.masters.socratesai.execution.service;

import com.masters.socratesai.execution.domain.*;

/** Internal, redacted contracts; no managed JPA objects cross a transaction. */
public record JudgingPlan(Action action, ExecutionBatch create, SubmittedExecutionBatch poll) {
    public enum Action { CREATE, POLL, DONE }
    public static JudgingPlan done() { return new JudgingPlan(Action.DONE, null, null); }
    @Override public String toString() { return "JudgingPlan[" + action + "]"; }
}
