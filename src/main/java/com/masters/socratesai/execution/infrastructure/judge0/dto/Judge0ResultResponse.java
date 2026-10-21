package com.masters.socratesai.execution.infrastructure.judge0.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.ToString;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/** Provider-only wire contract; never serialize to a student endpoint. */
@Value
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class Judge0ResultResponse {
    @ToString.Exclude
    String token;

    Judge0StatusResponse status;

    @ToString.Exclude
    String stdout;

    @ToString.Exclude
    String stderr;

    @ToString.Exclude
    @JsonProperty("compile_output")
    String compileOutput;

    String time;

    Long memory;

    @JsonProperty("exit_code")
    Integer exitCode;
}
