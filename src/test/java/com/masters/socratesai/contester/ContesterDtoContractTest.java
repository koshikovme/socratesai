package com.masters.socratesai.contester;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.masters.socratesai.collection.dto.CollectionDraftRequest;
import com.masters.socratesai.collection.dto.CollectionProblemRequest;
import com.masters.socratesai.common.model.ContentVisibility;
import com.masters.socratesai.execution.dto.CreateExecutionJobRequest;
import com.masters.socratesai.execution.dto.ExecutionJobResponse;
import com.masters.socratesai.execution.model.ExecutionMode;
import com.masters.socratesai.submission.dto.CreateSubmissionRequest;
import com.masters.socratesai.submission.dto.SubmissionResponse;
import com.masters.socratesai.task.dto.*;
import com.masters.socratesai.task.model.OutputComparison;
import com.masters.socratesai.task.model.TaskDifficulty;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ContesterDtoContractTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeAll
    static void initializeValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    void acceptsIncompleteDraftsButValidatesNestedDataAndOrdering() {
        assertThat(validator.validate(draft(List.of(), List.of()))).isEmpty();
        assertThat(validator.validate(draft(List.of(language()), List.of(testCase(0))))).isEmpty();

        assertThat(validator.validate(draft(List.of(language(), language()), List.of(testCase(0)))))
                .extracting(v -> v.getPropertyPath().toString()).contains("languageSelectionUnique");
        assertThat(validator.validate(draft(List.of(language()), List.of(testCase(0), testCase(0)))))
                .extracting(v -> v.getPropertyPath().toString()).contains("testOrderUnique");

        var invalidLanguage = new ProblemLanguageRequest("../java", "", 3000, 1000, 0, 65537);
        assertThat(validator.validate(draft(List.of(invalidLanguage), Arrays.asList((ProblemTestCaseRequest) null))))
                .extracting(v -> v.getPropertyPath().toString())
                .anyMatch(path -> path.contains("languageKey"))
                .anyMatch(path -> path.contains("wallTimeValid"))
                .anyMatch(path -> path.contains("memoryLimitKb"))
                .anyMatch(path -> path.contains("outputLimitBytes"))
                .anyMatch(path -> path.contains("tests"));
    }

    @Test
    void rejectsDuplicateProblemMembershipAndMissingConcurrencyToken() {
        var member = new CollectionProblemRequest(1L, 2L);
        var valid = new CollectionDraftRequest(0L, "Loops", "", ContentVisibility.AUTHENTICATED, List.of(member));
        assertThat(validator.validate(valid)).isEmpty();

        var invalid = new CollectionDraftRequest(null, "Loops", "", ContentVisibility.AUTHENTICATED,
                List.of(member, new CollectionProblemRequest(1L, 3L)));
        assertThat(validator.validate(invalid)).extracting(v -> v.getPropertyPath().toString())
                .contains("lockVersion", "problemSelectionUnique");
    }

    @Test
    void validatesSubmissionSourceLimitsLanguageAndIdempotencyWithoutClientIdentity() throws Exception {
        var valid = new CreateSubmissionRequest(1L, null, "python3", "print(3)", UUID.randomUUID());
        assertThat(validator.validate(valid)).isEmpty();
        assertThat(validator.validate(new CreateSubmissionRequest(1L, null, "java", "x".repeat(20001), null)))
                .extracting(v -> v.getPropertyPath().toString()).contains("sourceCode", "idempotencyKey");
        assertThat(validator.validate(new CreateSubmissionRequest(-1L, null, "../shell", " ", UUID.randomUUID())))
                .extracting(v -> v.getPropertyPath().toString()).contains("problemVersionId", "languageKey", "sourceCode");

        assertThat(objectMapper.readValue(objectMapper.writeValueAsString(valid), CreateSubmissionRequest.class))
                .isEqualTo(valid);
        assertThat(componentNames(CreateSubmissionRequest.class))
                .doesNotContain("studentId", "attemptNumber", "compileSuccess", "testsPassed", "runtimeKey");
    }

    @Test
    void requiresExactlyOneExecutionTargetMatchingItsMode() {
        Stream.of(ExecutionMode.values()).forEach(mode -> {
            UUID submission = mode == ExecutionMode.SUBMISSION ? UUID.randomUUID() : null;
            UUID run = mode == ExecutionMode.SUBMISSION ? null : UUID.randomUUID();
            var valid = new CreateExecutionJobRequest(1L, submission, run, mode, OffsetDateTime.now().plusMinutes(5), 3);
            assertThat(validator.validate(valid)).isEmpty();
            var invalid = new CreateExecutionJobRequest(1L, UUID.randomUUID(), UUID.randomUUID(), mode,
                    OffsetDateTime.now().minusMinutes(5), 0);
            assertThat(validator.validate(invalid)).extracting(v -> v.getPropertyPath().toString())
                    .contains("targetValid", "deadlineAt", "maxAttempts");
        });
        assertThat(validator.validate(new CreateExecutionJobRequest(1L, null, null, null,
                OffsetDateTime.now().plusMinutes(5), 3)))
                .extracting(v -> v.getPropertyPath().toString()).contains("mode");
    }

    @Test
    void studentContractsCannotCarryHiddenTestsProviderTokensOrLeases() throws Exception {
        var publicProblem = ProblemResponse.builder().id(1L).title("Sum")
                .samples(List.of(new ProblemSampleResponse(0, "1 2", "3"))).build();
        String json = objectMapper.writeValueAsString(publicProblem);
        assertThat(json).contains("\"samples\"", "\"expectedOutput\":\"3\"")
                .doesNotContain("hidden", "tests", "legacyTestId", "ownerId");
        assertThat(componentNames(ProblemResponse.class)).doesNotContain("tests", "hiddenTests", "referenceSource");
        assertThat(componentNames(ProblemSampleResponse.class)).doesNotContain("hidden", "testCaseId");
        assertThat(componentNames(SubmissionResponse.class)).doesNotContain("sourceCode", "providerToken", "stdout", "stderr");
        assertThat(componentNames(ExecutionJobResponse.class)).doesNotContain("leaseOwner", "leaseToken", "providerToken");
        assertThat(objectMapper.writeValueAsString(draft(List.of(language()), List.of(testCase(0)))))
                .doesNotContain("languageSelectionUnique", "testOrderUnique", "wallTimeValid");
    }

    private static List<String> componentNames(Class<?> type) {
        return Arrays.stream(type.getRecordComponents()).map(component -> component.getName()).toList();
    }

    private static ProblemDraftRequest draft(List<ProblemLanguageRequest> languages, List<ProblemTestCaseRequest> tests) {
        return new ProblemDraftRequest(0L, "Sum", "Arithmetic", TaskDifficulty.EASY, "Read and add two numbers.",
                "Two integers.", "Their sum.", "", OutputComparison.IGNORE_TRAILING_LINE_ENDINGS,
                ContentVisibility.AUTHENTICATED, languages, tests);
    }

    private static ProblemLanguageRequest language() {
        return new ProblemLanguageRequest("python3", "", 2000, 5000, 262144, 65536);
    }

    private static ProblemTestCaseRequest testCase(int position) {
        return new ProblemTestCaseRequest(position, "", "", false);
    }
}
