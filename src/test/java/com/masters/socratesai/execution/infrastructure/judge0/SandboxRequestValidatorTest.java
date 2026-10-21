package com.masters.socratesai.execution.infrastructure.judge0;

import com.masters.socratesai.execution.domain.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.masters.socratesai.execution.infrastructure.judge0.SandboxFixtures.*;
import static org.assertj.core.api.Assertions.*;

class SandboxRequestValidatorTest {
    private final Judge0Properties properties = properties();
    private final SandboxRequestValidator validator = new SandboxRequestValidator(properties);

    @Test
    void snapshotsAndSortsTestsWithoutKeepingMutableCallerList() {
        var tests = new ArrayList<>(List.of(test(2, 1, true, "secret"), test(1, 0, false, "42")));
        var snapshot = validator.snapshot(batch(Instant.now().plusSeconds(10), tests));
        tests.clear();
        assertThat(snapshot.getTests()).extracting(ExecutionTestCase::getTestCaseId).containsExactly(1L, 2L);
        assertThatThrownBy(() -> snapshot.getTests().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(snapshot.toString()).doesNotContain(SECRET, "secret", "sample-input", "hidden-input");
    }

    @Test
    void rejectsMissingDuplicateOrOversizedCasesBeforeSendingSource() {
        var deadline = Instant.now().plusSeconds(10);
        assertInvalid(() -> validator.snapshot((ExecutionBatch) null));
        assertInvalid(() -> validator.snapshot(batch(null, List.of(test(1, 0, false, "")))));
        assertInvalid(() -> validator.snapshot(batch(deadline, List.of())));
        assertInvalid(() -> validator.snapshot(batch(deadline, Arrays.asList((ExecutionTestCase) null))));
        assertInvalid(() -> validator.snapshot(batch(deadline, List.of(test(1, 0, false, ""), test(1, 1, true, "")))));
        assertInvalid(() -> validator.snapshot(batch(deadline, List.of(test(1, 0, false, ""), test(2, 0, true, "")))));
        assertInvalid(() -> validator.snapshot(batch(deadline, List.of(test(0, 0, false, "")))));
        // Keep stdin below the cap so the expected-output byte/encoding checks are actually exercised.
        properties.setMaxTestBytes(16);
        String oversizedUtf8 = "я".repeat(9);
        assertInvalid(() -> validator.snapshot(batch(deadline, List.of(test(1, 0, false, oversizedUtf8)))));
        assertInvalid(() -> validator.snapshot(batch(deadline, List.of(test(1, 0, false, "\uD800")))));
        properties.setMaxSourceBytes(1);
        assertInvalid(() -> validator.snapshot(batch(deadline, List.of(test(1, 0, false, "")))));
    }

    @Test
    void validatesResumedTicketsAndPreventsQueryInjection() {
        var deadline = Instant.now().plusSeconds(10);
        var sorted = validator.snapshot(submitted(deadline, ticket("b", 2, true), ticket("a", 1, false)));
        assertThat(sorted.getTickets()).extracting(ExecutionTicket::getToken).containsExactly("a", "b");
        assertInvalid(() -> validator.snapshot((SubmittedExecutionBatch) null));
        assertInvalid(() -> validator.snapshot(submitted(deadline)));
        assertInvalid(() -> validator.snapshot(submitted(deadline, ticket("same", 1, false), ticket("same", 2, true))));
        assertInvalid(() -> validator.snapshot(submitted(deadline, ticket("a", 1, false), ticket("b", 1, true))));
        assertInvalid(() -> validator.snapshot(submitted(deadline, ticket("token&fields=stdin", 1, false))));
        assertInvalid(() -> validator.snapshot(submitted(deadline, ticket("x".repeat(201), 1, false))));
    }

    private void assertInvalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOf(SandboxException.class).hasNoCause().hasMessageNotContaining(SECRET)
                .extracting(ex -> ((SandboxException) ex).getCode()).isEqualTo(SandboxFailureCode.INVALID_REQUEST);
    }
}
