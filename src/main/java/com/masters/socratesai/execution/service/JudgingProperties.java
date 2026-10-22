package com.masters.socratesai.execution.service;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import java.time.Duration;

@Getter @Setter @Validated @ConfigurationProperties("app.execution.worker")
public class JudgingProperties {
    private boolean enabled = true;
    @Min(1) @Max(32) private int concurrency = 4;
    @Min(1) @Max(20) private int batchSize = 5;
    @Min(1) @Max(10) private int maxAttempts = 3;
    @Min(1) @Max(100) private int maxPerUser = 5;
    @Min(1) @Max(10000) private int maxQueued = 1000;
    private Duration lease = Duration.ofSeconds(15);
    private Duration deadline = Duration.ofMinutes(5);
    private Duration sampleTtl = Duration.ofMinutes(15);
    private Duration initialBackoff = Duration.ofSeconds(1);
    private Duration maxBackoff = Duration.ofSeconds(10);

    @AssertTrue(message = "Invalid judging durations")
    public boolean isDurationsValid() {
        return lease != null && lease.compareTo(Duration.ofSeconds(3)) >= 0 && lease.compareTo(Duration.ofMinutes(1)) <= 0
                && deadline != null && deadline.compareTo(lease) >= 0 && deadline.compareTo(Duration.ofMinutes(30)) <= 0
                && sampleTtl != null && sampleTtl.compareTo(deadline) >= 0 && sampleTtl.compareTo(Duration.ofDays(1)) <= 0
                && initialBackoff != null && initialBackoff.compareTo(Duration.ofMillis(10)) >= 0
                && maxBackoff != null && maxBackoff.compareTo(initialBackoff) >= 0 && maxBackoff.compareTo(Duration.ofMinutes(1)) <= 0;
    }
}
