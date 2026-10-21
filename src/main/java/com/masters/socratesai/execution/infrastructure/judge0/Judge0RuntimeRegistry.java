package com.masters.socratesai.execution.infrastructure.judge0;

import com.masters.socratesai.execution.domain.ExecutionBatch;
import com.masters.socratesai.execution.domain.SandboxException;
import com.masters.socratesai.execution.domain.SandboxFailureCode;
import com.masters.socratesai.execution.infrastructure.judge0.dto.Judge0LanguageResponse;
import lombok.RequiredArgsConstructor;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public final class Judge0RuntimeRegistry {
    private final Judge0Properties properties;
    private final Clock clock;
    private volatile Catalog catalog;

    public Judge0Properties.RuntimeSpec configuredRuntime(ExecutionBatch batch) {
        return properties.getRuntimes().stream().filter(runtime -> runtime.getKey().equals(batch.getRuntimeKey())
                && runtime.getLanguageKey().equals(batch.getLanguageKey()) && runtime.getVersion().equals(batch.getRuntimeVersion()))
                .findFirst().orElseThrow(() -> new SandboxException(SandboxFailureCode.RUNTIME_UNAVAILABLE));
    }

    public boolean hasFreshCatalog() {
        return fresh(catalog);
    }

    private boolean fresh(Catalog current) {
        Instant now = clock.instant();
        return current != null && !now.isBefore(current.loadedAt())
                && now.isBefore(current.loadedAt().plus(properties.getCatalogTtl()));
    }

    public void acceptCatalog(Judge0LanguageResponse[] response) {
        if (response == null || response.length == 0 || response.length > 1000
                || Arrays.stream(response).anyMatch(item -> item == null || item.getId() == null
                || item.getId() <= 0 || item.getName() == null || item.getName().length() > 200)) {
            throw new SandboxException(SandboxFailureCode.INVALID_RESPONSE);
        }
        try {
            Map<Integer, String> languages = Arrays.stream(response)
                    .collect(Collectors.toUnmodifiableMap(Judge0LanguageResponse::getId, Judge0LanguageResponse::getName));
            catalog = new Catalog(clock.instant(), languages);
        } catch (IllegalStateException ex) {
            throw new SandboxException(SandboxFailureCode.INVALID_RESPONSE);
        }
    }

    public void verify(Judge0Properties.RuntimeSpec runtime) {
        Catalog current = catalog;
        if (!fresh(current) || !runtime.getProviderName().equals(current.languages().get(runtime.getLanguageId()))) {
            throw new SandboxException(SandboxFailureCode.RUNTIME_UNAVAILABLE);
        }
    }

    private record Catalog(Instant loadedAt, Map<Integer, String> languages) {}
}
