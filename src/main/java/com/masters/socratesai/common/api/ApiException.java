package com.masters.socratesai.common.api;

import lombok.Getter;

@Getter
public final class ApiException extends RuntimeException {
    private final ApiErrorCode code;

    public ApiException(ApiErrorCode code) {
        super(code.getMessage(), null, false, false);
        this.code = code;
    }
}
