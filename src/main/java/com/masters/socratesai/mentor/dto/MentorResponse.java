package com.masters.socratesai.mentor.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class MentorResponse {
    private UUID interactionId;
    private UUID sessionId;
    private String action;
    private String feedbackText;
    private String feedbackSource;
    private String errorType;
    private String suspiciousRegion;
    private boolean compileSuccess;
    private int testsPassed;
    private int testsFailed;
    private int analysisTimeMs;
    private String mentorState;
    private Double mentorStateConfidence;
}
