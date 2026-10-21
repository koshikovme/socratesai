package com.masters.socratesai.execution.infrastructure.judge0.dto;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/** Provider-only wire contract; never serialize to a student endpoint. */
@Value
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class Judge0BatchRequest {
    List<Judge0SubmissionRequest> submissions;
}
