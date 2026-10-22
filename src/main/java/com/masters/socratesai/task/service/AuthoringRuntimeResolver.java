package com.masters.socratesai.task.service;

import com.masters.socratesai.common.api.*;
import com.masters.socratesai.execution.domain.ExecutionRuntimeCatalogPort;
import com.masters.socratesai.task.model.ProblemLanguageConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Objects;

@Component @RequiredArgsConstructor
public class AuthoringRuntimeResolver {
    private final ExecutionRuntimeCatalogPort catalog;

    public Optional<ExecutionRuntimeCatalogPort.RuntimeDescriptor> configured(String languageKey) {
        return catalog.configured().stream().filter(runtime -> runtime.languageKey().equals(languageKey)).findFirst();
    }

    public void requireConfigured(List<ProblemLanguageConfig> languages) {
        if (languages.isEmpty() || languages.stream().anyMatch(language -> configured(language.getLanguageKey())
                .filter(runtime -> runtime.runtimeKey().equals(language.getRuntimeKey())
                        && runtime.runtimeVersion().equals(language.getRuntimeVersion())
                        && Objects.equals(runtime.entryPoint(), language.getEntryPoint())).isEmpty())) {
            throw new ApiException(ApiErrorCode.SANDBOX_UNAVAILABLE);
        }
    }
}
