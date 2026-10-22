package com.masters.socratesai.task.controller;

import com.masters.socratesai.execution.domain.ExecutionRuntimeCatalogPort;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequiredArgsConstructor
public class LanguageCatalogController {
    private final ExecutionRuntimeCatalogPort catalog;

    /** Configuration discovery, not a sandbox liveness or language-certification claim. */
    @GetMapping("/api/languages")
    public List<ConfiguredLanguageResponse> languages() {
        return catalog.configured().stream().map(runtime -> new ConfiguredLanguageResponse(runtime.languageKey(), runtime.runtimeVersion())).toList();
    }
    public record ConfiguredLanguageResponse(String languageKey, String runtimeVersion) {}
}
