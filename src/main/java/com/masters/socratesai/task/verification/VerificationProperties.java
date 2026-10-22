package com.masters.socratesai.task.verification;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Getter @Setter
@ConfigurationProperties("app.authoring.verification")
public class VerificationProperties {
    private Duration timeout = Duration.ofSeconds(30);
    private Duration evidenceTtl = Duration.ofMinutes(30);
    private int maxConcurrent = 4;
    private int batchSize = 5;

    public void validate() {
        if (timeout == null || timeout.compareTo(Duration.ofSeconds(1)) < 0 || timeout.compareTo(Duration.ofMinutes(2)) > 0
                || evidenceTtl == null || evidenceTtl.compareTo(timeout) < 0 || evidenceTtl.compareTo(Duration.ofDays(1)) > 0
                || maxConcurrent < 1 || maxConcurrent > 16 || batchSize < 1 || batchSize > 20) {
            throw new IllegalArgumentException("Invalid reference verification configuration");
        }
    }
}
