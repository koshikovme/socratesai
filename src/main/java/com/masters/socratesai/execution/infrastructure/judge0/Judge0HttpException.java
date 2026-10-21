package com.masters.socratesai.execution.infrastructure.judge0;

import lombok.Getter;

@Getter
final class Judge0HttpException extends RuntimeException {
    private final int status;

    Judge0HttpException(int status) {
        super("Sandbox HTTP failure: " + status, null, false, false);
        this.status = status;
    }
}
