package com.masters.socratesai.execution.service;

import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Value @Builder @ToString(onlyExplicitlyIncluded = true)
public class JudgingLease {
    @ToString.Include UUID jobId;
    int generation;
    UUID token;
    Instant deadline;
}
