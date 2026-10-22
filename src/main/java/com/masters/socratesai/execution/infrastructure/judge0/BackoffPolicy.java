package com.masters.socratesai.execution.infrastructure.judge0;

import lombok.RequiredArgsConstructor;
import java.time.Duration;

@RequiredArgsConstructor
public final class BackoffPolicy {
    private final Duration initial;
    private final Duration maximum;

    public Duration delay(int attempt) {
        return new com.masters.socratesai.execution.domain.ExponentialBackoff(initial, maximum).delay(attempt);
    }
}
