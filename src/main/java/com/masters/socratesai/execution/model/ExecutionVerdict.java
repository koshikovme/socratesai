package com.masters.socratesai.execution.model;

/** A mentor-state prediction is never an ExecutionVerdict. */
public enum ExecutionVerdict {
    ACCEPTED, WRONG_ANSWER, COMPILATION_ERROR, RUNTIME_ERROR,
    TIME_LIMIT_EXCEEDED, MEMORY_LIMIT_EXCEEDED, OUTPUT_LIMIT_EXCEEDED
}
