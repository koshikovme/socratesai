package com.masters.socratesai.websocket.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class FeedbackWsMessage {
    private UUID interactionId;
    private UUID sessionId;
    private String errorType;
    private String action;
    private String feedbackText;
    private String suspiciousRegion;
    private boolean compileSuccess;
    private Integer testsPassed;
    private Integer testsFailed;
    private Integer analysisTimeMs;
}
