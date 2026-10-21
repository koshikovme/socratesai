package com.masters.socratesai.execution.infrastructure.judge0;

import lombok.RequiredArgsConstructor;
import java.time.Duration;

@RequiredArgsConstructor
public final class BackoffPolicy {
    private final Duration initial;
    private final Duration maximum;

    public Duration delay(int attempt) {
        long initialNanos = initial.toNanos();
        long cap = maximum.toNanos();
        if (attempt >= 63 || initialNanos > (cap >> Math.max(0, attempt))) return maximum;
        return Duration.ofNanos(Math.min(cap, initialNanos << Math.max(0, attempt)));
    }
}
