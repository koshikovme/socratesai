package com.masters.socratesai.execution.infrastructure.judge0.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/** Provider-only wire contract; never serialize to a student endpoint. */
@Value
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class Judge0LanguageResponse {
    Integer id;

    String name;
}
