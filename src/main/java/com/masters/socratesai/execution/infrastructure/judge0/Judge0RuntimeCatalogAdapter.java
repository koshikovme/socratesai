package com.masters.socratesai.execution.infrastructure.judge0;

import com.masters.socratesai.execution.domain.ExecutionRuntimeCatalogPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component @RequiredArgsConstructor
public class Judge0RuntimeCatalogAdapter implements ExecutionRuntimeCatalogPort {
    private final Judge0Properties properties;

    @Override
    public List<RuntimeDescriptor> configured() {
        if (!properties.isEnabled()) return List.of();
        return properties.getRuntimes().stream().map(spec -> new RuntimeDescriptor(spec.getLanguageKey(), spec.getKey(),
                spec.getVersion(), spec.getEntryPoint())).toList();
    }
}
