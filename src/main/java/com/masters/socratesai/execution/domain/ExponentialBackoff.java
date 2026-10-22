package com.masters.socratesai.execution.domain;

import lombok.RequiredArgsConstructor;
import java.time.Duration;

@RequiredArgsConstructor
public final class ExponentialBackoff {
    private final Duration initial;
    private final Duration maximum;
    public Duration delay(int attempt) {
        long nanos = initial.toNanos(); long cap = maximum.toNanos();
        if (attempt >= 63 || nanos > (cap >> Math.max(0, attempt))) return maximum;
        return Duration.ofNanos(Math.min(cap, nanos << Math.max(0, attempt)));
    }
}
