package com.masters.socratesai.execution.model;

/** FINISHED has a judge verdict; SYSTEM_ERROR has no student verdict. */
public enum ExecutionStatus {
    QUEUED, DISPATCHING, RUNNING, FINISHED, SYSTEM_ERROR
}
