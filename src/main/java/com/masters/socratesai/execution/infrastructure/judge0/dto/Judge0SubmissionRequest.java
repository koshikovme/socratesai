package com.masters.socratesai.execution.infrastructure.judge0.dto;

import java.math.BigDecimal;
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
public class Judge0SubmissionRequest {
    @ToString.Exclude
    @JsonProperty("source_code")
    String sourceCode;

    @JsonProperty("language_id")
    int languageId;

    @ToString.Exclude
    String stdin;

    @JsonProperty("cpu_time_limit")
    BigDecimal cpuTimeLimit;

    @JsonProperty("cpu_extra_time")
    BigDecimal cpuExtraTime;

    @JsonProperty("wall_time_limit")
    BigDecimal wallTimeLimit;

    @JsonProperty("memory_limit")
    int memoryLimit;

    @JsonProperty("stack_limit")
    int stackLimit;

    @JsonProperty("max_processes_and_or_threads")
    int maxProcessesAndOrThreads;

    @JsonProperty("max_file_size")
    int maxFileSize;

    @JsonProperty("enable_network")
    boolean enableNetwork;

    @JsonProperty("enable_per_process_and_thread_time_limit")
    boolean enablePerProcessAndThreadTimeLimit;

    @JsonProperty("enable_per_process_and_thread_memory_limit")
    boolean enablePerProcessAndThreadMemoryLimit;

    @JsonProperty("redirect_stderr_to_stdout")
    boolean redirectStderrToStdout;

    @JsonProperty("number_of_runs")
    int numberOfRuns;
}
