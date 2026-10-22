package com.masters.socratesai.task.service;

import com.masters.socratesai.common.api.*;
import com.masters.socratesai.task.dto.ProblemDraftRequest;
import lombok.experimental.UtilityClass;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

@UtilityClass
public class AuthoringValidation {
    public void revision(Long actual, Long expected) {
        if (!Objects.equals(actual, expected)) throw new ApiException(ApiErrorCode.CONFLICT);
    }

    public void text(String value, int maxBytes) {
        if (value == null || value.length() > maxBytes || !StandardCharsets.UTF_8.newEncoder().canEncode(value)
                || value.getBytes(StandardCharsets.UTF_8).length > maxBytes) throw new ApiException(ApiErrorCode.INVALID_REQUEST);
    }

    public void draft(ProblemDraftRequest request) {
        text(request.statement(), 200000);
        text(request.inputFormat(), 200000);
        text(request.outputFormat(), 200000);
        text(request.constraintsText(), 200000);
        request.languages().forEach(language -> { if (language.starterCode() != null) text(language.starterCode(), 80000); });
        request.tests().forEach(test -> { text(test.inputData(), 65536); text(test.expectedOutput(), 65536); });
    }
}
