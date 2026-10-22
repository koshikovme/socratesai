package com.masters.socratesai.execution.domain;

import java.util.List;

/** Operator-approved runtime configuration. Actual availability is checked again during execution. */
public interface ExecutionRuntimeCatalogPort {
    List<RuntimeDescriptor> configured();

    record RuntimeDescriptor(String languageKey, String runtimeKey, String runtimeVersion, String entryPoint) {}
}
