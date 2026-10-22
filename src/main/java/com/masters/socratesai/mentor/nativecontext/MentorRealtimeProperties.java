package com.masters.socratesai.mentor.nativecontext;

import lombok.*;
import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter @Setter @Validated @ConfigurationProperties("app.mentor.realtime")
public class MentorRealtimeProperties {
    @Min(20) @Max(2000) private long debounceMs = 250;
    @Min(100) @Max(10000) private long minIntervalMs = 1000;
    @Min(1000) @Max(30000) private long timeoutMs = 15000;
    @Min(1) @Max(16) private int concurrency = 4;
    @Min(1) @Max(50) private int messagesPerSecond = 20;
    @Min(1) @Max(16) private int sessionsPerUser = 4;
    @Min(1) @Max(16) private int editorsPerUser = 4;
    @Min(1) @Max(4096) private int maxSessions = 1024;
    @Min(1) @Max(4096) private int maxEditors = 1024;
}
