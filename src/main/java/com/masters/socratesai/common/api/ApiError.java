package com.masters.socratesai.common.api;

public record ApiError(String code, String message, String requestId) {}
