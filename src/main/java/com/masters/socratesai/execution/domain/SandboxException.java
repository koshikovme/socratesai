package com.masters.socratesai.execution.domain;

import lombok.Getter;

@Getter
public final class SandboxException extends RuntimeException {
    private final SandboxFailureCode code;
    private final boolean mayHaveBeenSubmitted;

    public SandboxException(SandboxFailureCode code) {
        this(code, false);
    }

    public SandboxException(SandboxFailureCode code, boolean mayHaveBeenSubmitted) {
        super("Sandbox operation failed: " + code.name(), null, false, false);
        this.code = code;
        this.mayHaveBeenSubmitted = mayHaveBeenSubmitted;
    }
}
