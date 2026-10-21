package com.masters.socratesai.execution.domain;

import com.masters.socratesai.task.model.OutputComparison;
import java.util.Objects;

/** Deterministic text comparison: no locale, trimming, Unicode or whitespace folding. */
public final class OutputComparator {

    public boolean matches(String expected, String actual, OutputComparison policy) {
        Objects.requireNonNull(expected, "Expected output is required");
        Objects.requireNonNull(actual, "Actual output is required");
        Objects.requireNonNull(policy, "Comparison policy is required");
        return normalize(expected, policy).equals(normalize(actual, policy));
    }

    private String normalize(String text, OutputComparison policy) {
        String normalized = text.replace("\r\n", "\n");
        if (policy == OutputComparison.EXACT) return normalized;
        int end = normalized.length();
        while (end > 0 && normalized.charAt(end - 1) == '\n') end--;
        return normalized.substring(0, end);
    }
}
