package com.masters.socratesai.execution.infrastructure.judge0;

import com.masters.socratesai.execution.domain.*;
import com.masters.socratesai.task.model.OutputComparison;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

/** Protocol fixtures only: language IDs and versions do not describe an installed compiler. */
final class SandboxFixtures {
    static final String SECRET = "private-source-and-auth-fixture";

    static ExecutionLimits limits() {
        return ExecutionLimits.builder().timeLimitMs(1250).wallTimeLimitMs(3000).memoryLimitKb(65536).outputLimitBytes(4096).build();
    }

    static ExecutionTestCase test(long id, int position, boolean hidden, String expected) {
        return ExecutionTestCase.builder().testCaseId(id).position(position).hidden(hidden).input(hidden ? "hidden-input" : "sample-input")
                .expectedOutput(expected).build();
    }

    static ExecutionBatch batch(Instant deadline, List<ExecutionTestCase> tests) {
        return ExecutionBatch.builder().languageKey("python3").runtimeKey("python-fixture").runtimeVersion("3.fixture")
                .sourceCode(SECRET).limits(limits()).comparison(OutputComparison.EXACT).tests(tests).deadline(deadline).build();
    }

    static ExecutionTicket ticket(String token, long id, boolean hidden) {
        return ExecutionTicket.builder().token(token).testCaseId(id).position((int) id - 1).hidden(hidden).expectedOutput("42\n").build();
    }

    static SubmittedExecutionBatch submitted(Instant deadline, ExecutionTicket... tickets) {
        return SubmittedExecutionBatch.builder().runtimeKey("python-fixture").runtimeVersion("3.fixture").limits(limits())
                .comparison(OutputComparison.EXACT).deadline(deadline).tickets(List.of(tickets)).build();
    }

    static Judge0Properties properties() {
        var properties = new Judge0Properties();
        properties.setEnabled(true);
        properties.setBaseUrl("https://sandbox.invalid/api/");
        properties.setApiKey(SECRET);
        var runtime = new Judge0Properties.RuntimeSpec();
        runtime.setKey("python-fixture");
        runtime.setLanguageKey("python3");
        runtime.setVersion("3.fixture");
        runtime.setProviderName("Python (3.fixture)");
        runtime.setLanguageId(71);
        properties.setRuntimes(List.of(runtime));
        return properties;
    }

    static String encoded(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }
}
