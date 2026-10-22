package com.masters.socratesai.mentor.nativecontext;

import com.masters.socratesai.common.model.EvidenceOrigin;
import com.masters.socratesai.execution.model.ExecutionVerdict;
import lombok.*;
import java.util.UUID;

@Value @Builder(toBuilder = true) @ToString(onlyExplicitlyIncluded = true)
public class NativeMentorFeedback {
    UUID interactionId;
    UUID sessionId;
    UUID editorSessionId;
    Long problemId;
    Long problemVersionId;
    String languageKey;
    Long codeRevision;
    String codeHash;
    EvidenceOrigin evidenceOrigin;
    boolean executionKnown;
    Boolean compileSuccess;
    Integer testsPassed;
    Integer testsFailed;
    ExecutionVerdict verdict;
    String action;
    String feedbackText;
    String feedbackSource;
    String policyVersion;
    String policyMode;
    String fallbackReason;
    String mentorState;
    Double mentorStateConfidence;
}
