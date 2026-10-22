package com.masters.socratesai.mentor.nativecontext;
import com.masters.socratesai.execution.model.*;

/** Projection cannot load input, expected output, source, tokens or diagnostic bodies. */
public record MentorCaseEvidence(CaseExecutionStatus status, ExecutionVerdict verdict) {}
