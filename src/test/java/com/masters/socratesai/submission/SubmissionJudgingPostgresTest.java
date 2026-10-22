package com.masters.socratesai.submission;

import com.fasterxml.jackson.databind.*;
import com.masters.socratesai.execution.domain.*;
import com.masters.socratesai.execution.domain.port.CodeExecutionPort;
import com.masters.socratesai.execution.model.*;
import com.masters.socratesai.execution.repo.*;
import com.masters.socratesai.execution.service.*;
import com.masters.socratesai.security.*;
import com.masters.socratesai.submission.repo.*;
import com.masters.socratesai.user.model.*;
import com.masters.socratesai.user.repo.UserRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.*;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real disposable PostgreSQL, synthetic programs, no local compilation/execution. */
@Testcontainers @SpringBootTest(properties = {"spring.config.import=", "app.execution.worker.enabled=false"})
@AutoConfigureMockMvc @ActiveProfiles("test")
class SubmissionJudgingPostgresTest {
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired JwtService jwt;
    @Autowired JdbcTemplate jdbc;
    @Autowired JudgingStore store;
    @Autowired JudgingCoordinator coordinator;
    @Autowired JudgingProperties properties;
    @Autowired ExecutionJobRepository jobs;
    @Autowired SubmissionRepository submissions;
    @MockitoSpyBean StudentProblemProgressRepository progress;
    @Autowired SampleRunCleanup cleanup;
    @Autowired Clock clock;
    @Autowired @org.springframework.beans.factory.annotation.Qualifier("judgingExecutor") ExecutorService judgingExecutor;
    @Autowired ExecutionRunRepository runs;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean CodeExecutionPort execution;
    @MockitoBean ExecutionRuntimeCatalogPort catalog;
    private final ObjectMapper json = new ObjectMapper();
    private final AtomicReference<ExecutionVerdict> verdict = new AtomicReference<>(ExecutionVerdict.ACCEPTED);
    private String studentToken, otherToken, teacherToken, otherTeacherToken, adminToken;
    private User student;
    private long problemId, versionId;
    private static final String SOURCE = "synthetic-student-source-not-a-program";
    private static final String SECRET = "private-hidden-echo-compiler-secret";

    @BeforeEach void fixture() throws Exception {
        // This test class owns its container. Finish prior intentionally interrupted jobs.
        jdbc.update("UPDATE execution_jobs SET status='SYSTEM_ERROR', retry_allowed=false, finished_at=NOW(), lease_owner=NULL, lease_token=NULL, lease_expires_at=NULL WHERE status <> 'FINISHED'");
        properties.setBatchSize(5); properties.setMaxPerUser(5); properties.setMaxQueued(1000); properties.setEnabled(false);
        verdict.set(ExecutionVerdict.ACCEPTED);
        student = user(UserRole.STUDENT); studentToken = token(student); otherToken = token(user(UserRole.STUDENT));
        teacherToken = token(user(UserRole.TEACHER)); otherTeacherToken = token(user(UserRole.TEACHER)); adminToken = token(user(UserRole.ADMIN));
        when(catalog.configured()).thenReturn(List.of(new ExecutionRuntimeCatalogPort.RuntimeDescriptor("python3", "fixture-python", "3.fixture", "main.py")));
        when(execution.submitBatch(any())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return CompletableFuture.completedFuture(tickets(call.getArgument(0)));
        });
        when(execution.pollBatch(any())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return CompletableFuture.completedFuture(result(call.getArgument(0)));
        });
        var draft = Map.of("lockVersion", 0, "title", "Sum", "topic", "Arithmetic", "difficulty", "EASY", "statement", "Add numbers",
                "inputFormat", "Numbers", "outputFormat", "Sum", "constraintsText", "", "comparisonMode", "EXACT", "visibility", "AUTHENTICATED");
        var body = new HashMap<String,Object>(draft);
        body.put("languages", List.of(Map.of("languageKey", "python3", "starterCode", "", "timeLimitMs", 2000, "wallTimeLimitMs", 5000,
                "memoryLimitKb", 262144, "outputLimitBytes", 65536)));
        body.put("tests", List.of(Map.of("position", 0, "inputData", "1 2", "expectedOutput", "3", "hidden", false),
                Map.of("position", 1, "inputData", SECRET, "expectedOutput", "3", "hidden", true)));
        var created = tree(api(post("/api/teacher/problems"), teacherToken, body).andExpect(status().isCreated()).andReturn());
        problemId = created.get("id").asLong(); versionId = created.get("version").get("id").asLong();
        var verification = tree(api(post("/api/teacher/problems/{id}/verify", problemId), teacherToken,
                Map.of("lockVersion", 0, "solutions", List.of(Map.of("languageKey", "python3", "sourceCode", "synthetic-reference"))))
                .andExpect(status().isAccepted()).andReturn()).get("id").asText();
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> api(get("/api/teacher/problems/{id}/verifications/{v}", problemId, verification), teacherToken, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.verdict").value("ACCEPTED")));
        api(post("/api/teacher/problems/{id}/publish", problemId), teacherToken, Map.of("lockVersion", 0, "verificationId", verification)).andExpect(status().isOk());
        clearInvocations(execution);
    }

    @Test void concurrentIdempotencyAndAttemptsAreAtomicAndAdmissionDoesNotConsumeANumber() throws Exception {
        var body = request(UUID.randomUUID());
        try (var pool = Executors.newFixedThreadPool(8)) {
            var repeated = IntStream.range(0, 8).mapToObj(i -> pool.submit(() -> submit(body))).toList();
            var ids = new HashSet<UUID>();
            for (var future : repeated) ids.add(future.get(10, TimeUnit.SECONDS));
            assertThat(ids).hasSize(1);
            var distinct = IntStream.range(0, 4).mapToObj(i -> pool.submit(() -> submit(request(UUID.randomUUID())))).toList();
            for (var future : distinct) future.get(10, TimeUnit.SECONDS);
        }
        assertThat(jdbc.queryForList("SELECT attempt_number FROM submissions WHERE student_id=? ORDER BY attempt_number", Integer.class, student.getId()))
                .containsExactly(1, 2, 3, 4, 5);
        api(post("/api/problems/{id}/submissions", problemId), studentToken, request(UUID.randomUUID())).andExpect(status().isTooManyRequests());
        assertThat(profile().getFormalAttempts()).isEqualTo(5);
        // An identical retry remains allowed even at capacity.
        submit(body);
        var changed = new HashMap<>(body); changed.put("sourceCode", "changed-program");
        api(post("/api/problems/{id}/submissions", problemId), studentToken, changed).andExpect(status().isConflict());
        verifyNoInteractions(execution);
    }

    @Test void authenticationOwnershipValidationAndReviewAreEnforced() throws Exception {
        var body = request(UUID.randomUUID());
        api(post("/api/problems/{id}/submissions", problemId), null, body).andExpect(status().isUnauthorized());
        var invalid = new HashMap<>(body); invalid.put("idempotencyKey", "not-uuid");
        api(post("/api/problems/{id}/submissions", problemId), studentToken, invalid).andExpect(status().isBadRequest());
        invalid.put("idempotencyKey", UUID.randomUUID()); invalid.put("sourceCode", "x".repeat(20001));
        api(post("/api/problems/{id}/submissions", problemId), studentToken, invalid).andExpect(status().isBadRequest());
        invalid.put("sourceCode", SOURCE); invalid.put("languageKey", "unknown");
        api(post("/api/problems/{id}/submissions", problemId), studentToken, invalid).andExpect(status().isBadRequest());
        invalid.put("languageKey", "python3"); invalid.put("collectionId", 9999999);
        api(post("/api/problems/{id}/submissions", problemId), studentToken, invalid).andExpect(status().isNotFound());
        UUID id = submit(body);
        api(get("/api/submissions/{id}", id), null, null).andExpect(status().isUnauthorized());
        api(get("/api/submissions/{id}", id), otherToken, null).andExpect(status().isNotFound());
        api(get("/api/submissions/{id}", id), teacherToken, null).andExpect(status().isNotFound());
        api(get("/api/teacher/submissions/{id}", id), otherTeacherToken, null).andExpect(status().isNotFound());
        api(get("/api/teacher/submissions/{id}", id), studentToken, null).andExpect(status().isForbidden());
        api(get("/api/teacher/submissions/{id}", id), teacherToken, null).andExpect(status().isOk()).andExpect(jsonPath("$.sourceCode").value(SOURCE));
        api(get("/api/teacher/submissions/{id}", id), adminToken, null).andExpect(status().isOk());
        assertThat(profile().getFormalAttempts()).isEqualTo(1);
    }

    @ParameterizedTest @EnumSource(ExecutionVerdict.class)
    void finalVerdictsRequireTheEntireSuiteAndOnlyAcceptedEvidenceSolvesTheProblem(ExecutionVerdict outcome) throws Exception {
        verdict.set(outcome);
        UUID id = submit(request(UUID.randomUUID()));
        coordinator.start(claim());
        awaitStatus(id, ExecutionStatus.FINISHED);
        var submission = submissions.findById(id).orElseThrow();
        assertThat(submission.getVerdict()).isEqualTo(outcome);
        assertThat(submission.getTestsTotal()).isEqualTo(2);
        assertThat(submission.getTestsPassed()).isEqualTo(outcome == ExecutionVerdict.ACCEPTED ? 2 : 1);
        assertThat(profile().getAcceptedSubmissionId()).isEqualTo(outcome == ExecutionVerdict.ACCEPTED ? id : null);
        var payload = api(get("/api/submissions/{id}", id), studentToken, null).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(payload).doesNotContain(SOURCE, SECRET, "stdout", "stderr", "compileOutput", "providerToken", "testCaseId", "sourceCode", "expectedOutput");
        var history = api(get("/api/submissions"), studentToken, null).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(history).doesNotContain(SOURCE, SECRET, "stdout", "stderr", "providerToken");
        api(get("/api/users/me/progress"), studentToken, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.solvedTasks").value(outcome == ExecutionVerdict.ACCEPTED ? 1 : 0));
        verify(execution, times(1)).submitBatch(any());
    }

    @Test void skipLockedClaimsDoNotWaitOnAnotherWorkersTransaction() throws Exception {
        UUID first = submit(request(UUID.randomUUID())); UUID second = submit(request(UUID.randomUUID()));
        var locked = new CountDownLatch(1); var release = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> new TransactionTemplate(transactions).execute(tx -> {
                var lease = store.claim("worker-a", 1).getFirst(); locked.countDown();
                try { assertThat(release.await(5, TimeUnit.SECONDS)).isTrue(); } catch (InterruptedException ex) { throw new AssertionError(ex); }
                return lease;
            }));
            assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
            var b = pool.submit(() -> store.claim("worker-b", 1).getFirst());
            try {
                var leaseB = b.get(3, TimeUnit.SECONDS); release.countDown();
                var leaseA = a.get(3, TimeUnit.SECONDS);
                assertThat(leaseA.getJobId()).isNotEqualTo(leaseB.getJobId());
                assertThat(leaseA.getGeneration()).isEqualTo(1);
                assertThat(jobs.findAllById(List.of(leaseA.getJobId(), leaseB.getJobId()))).allMatch(job -> job.getAttemptCount() == 1 && job.getLeaseOwner() != null);
            } finally { release.countDown(); }
        }
        assertThat(submissions.findById(first).orElseThrow().getAttemptNumber()).isEqualTo(1);
        assertThat(submissions.findById(second).orElseThrow().getAttemptNumber()).isEqualTo(2);
    }

    @Test void restartPollsPersistedTicketsAndRejectsStaleOwnerWithoutResubmittingCode() throws Exception {
        UUID id = submit(request(UUID.randomUUID())); var old = claim();
        var plan = store.next(old); var saved = tickets(plan.create()); store.tickets(old, saved);
        expireLease(old); var replacement = claim();
        assertThat(replacement.getGeneration()).isEqualTo(2);
        assertThatThrownBy(() -> store.polled(old, result(saved))).isInstanceOf(StaleJudgingLease.class);
        assertThat(store.heartbeat(old)).isFalse();
        coordinator.start(replacement); awaitStatus(id, ExecutionStatus.FINISHED);
        verify(execution, never()).submitBatch(any()); verify(execution).pollBatch(any());
        assertThat(profile().getFormalAttempts()).isEqualTo(1);
    }

    @Test void crashAfterIntentWithoutTicketsIsAmbiguousAndNeverPostsAgain() throws Exception {
        UUID id = submit(request(UUID.randomUUID())); var old = claim();
        assertThat(store.next(old).action()).isEqualTo(JudgingPlan.Action.CREATE);
        expireLease(old); coordinator.start(claim()); awaitStatus(id, ExecutionStatus.SYSTEM_ERROR);
        assertThat(jobs.findBySubmissionId(id).orElseThrow().getLastErrorCode()).isEqualTo("SUBMISSION_UNKNOWN");
        assertThat(jobs.findBySubmissionId(id).orElseThrow().getRetryAllowed()).isFalse();
        assertThat(profile().getAcceptedSubmissionId()).isNull(); verifyNoInteractions(execution);
    }

    @Test void restartPreservesFinishedBatchesAndDispatchesOnlyRemainingTests() throws Exception {
        properties.setBatchSize(1);
        UUID id = submit(request(UUID.randomUUID())); var old = claim();
        var plan = store.next(old); var saved = tickets(plan.create()); store.tickets(old, saved); store.polled(old, result(saved));
        assertThat(profile().getAcceptedSubmissionId()).isNull();
        expireLease(old); coordinator.start(claim()); awaitStatus(id, ExecutionStatus.FINISHED);
        verify(execution, times(1)).submitBatch(argThat(batch -> batch.getTests().size() == 1 && batch.getTests().getFirst().isHidden()));
        assertThat(profile().getAcceptedSubmissionId()).isEqualTo(id);
    }

    @Test void pollOutageRetriesOnlyGetAndStopsAtThePersistedBudget() throws Exception {
        doReturn(CompletableFuture.failedFuture(new SandboxException(SandboxFailureCode.TRANSPORT_ERROR))).when(execution).pollBatch(any());
        UUID id = submit(request(UUID.randomUUID()));
        for (int i = 0; i < 3; i++) {
            coordinator.start(claim()); awaitStatus(id, ExecutionStatus.SYSTEM_ERROR);
            jdbc.update("UPDATE execution_jobs SET next_attempt_at=NOW() WHERE submission_id=?", id);
            await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> assertThat(jobs.findBySubmissionId(id).orElseThrow().getStatus()).isEqualTo(ExecutionStatus.SYSTEM_ERROR));
        }
        var job = jobs.findBySubmissionId(id).orElseThrow();
        assertThat(job.getAttemptCount()).isEqualTo(3); assertThat(job.getRetryAllowed()).isFalse();
        assertThat(store.claim("extra-worker", 1)).isEmpty();
        assertThat(profile().getFormalAttempts()).isEqualTo(1); assertThat(profile().getAcceptedSubmissionId()).isNull();
        verify(execution, times(1)).submitBatch(any()); verify(execution, times(3)).pollBatch(any());
    }

    @Test void definitelyRejectedCreateCanRetryButUnknownCreateCannot() throws Exception {
        doReturn(CompletableFuture.failedFuture(new SandboxException(SandboxFailureCode.BUSY))).when(execution).submitBatch(any());
        UUID id = submit(request(UUID.randomUUID())); coordinator.start(claim()); awaitStatus(id, ExecutionStatus.SYSTEM_ERROR);
        assertThat(jobs.findBySubmissionId(id).orElseThrow().getRetryAllowed()).isTrue();
        jdbc.update("UPDATE execution_jobs SET next_attempt_at=NOW() WHERE submission_id=?", id);
        when(execution.submitBatch(any())).thenAnswer(call -> CompletableFuture.completedFuture(tickets(call.getArgument(0))));
        coordinator.start(claim()); awaitStatus(id, ExecutionStatus.FINISHED);
        assertThat(profile().getFormalAttempts()).isEqualTo(1);
        doReturn(CompletableFuture.failedFuture(new SandboxException(SandboxFailureCode.SUBMISSION_UNKNOWN, true))).when(execution).submitBatch(any());
        UUID unknown = submit(request(UUID.randomUUID())); coordinator.start(claim()); awaitStatus(unknown, ExecutionStatus.SYSTEM_ERROR);
        assertThat(jobs.findBySubmissionId(unknown).orElseThrow().getRetryAllowed()).isFalse();
    }

    @Test void missingCasesCannotBeAcceptedEvenIfProviderClaimsAccepted() throws Exception {
        doReturn(CompletableFuture.completedFuture(ExecutionBatchResult.builder()
                .status(ExecutionStatus.FINISHED).verdict(ExecutionVerdict.ACCEPTED).cases(List.of()).build())).when(execution).pollBatch(any());
        UUID id = submit(request(UUID.randomUUID())); coordinator.start(claim()); awaitStatus(id, ExecutionStatus.SYSTEM_ERROR);
        assertThat(submissions.findById(id).orElseThrow().getVerdict()).isNull(); assertThat(profile().getAcceptedSubmissionId()).isNull();
        assertThat(jobs.findBySubmissionId(id).orElseThrow().getLastErrorCode()).isEqualTo("INVALID_RESPONSE");
    }

    @Test void missingSuiteAndExpiredJobsFailClosedWithoutProviderIo() throws Exception {
        UUID id = submit(request(UUID.randomUUID())); var lease = claim();
        jdbc.update("DELETE FROM execution_case_results WHERE job_id=?", lease.getJobId());
        assertThat(store.next(lease).action()).isEqualTo(JudgingPlan.Action.DONE); awaitStatus(id, ExecutionStatus.SYSTEM_ERROR);
        UUID timedOut = submit(request(UUID.randomUUID()));
        jdbc.update("UPDATE execution_jobs SET created_at=NOW()-INTERVAL '2 minutes', deadline_at=NOW()-INTERVAL '1 minute' WHERE submission_id=?", timedOut);
        store.expire(); awaitStatus(timedOut, ExecutionStatus.SYSTEM_ERROR);
        assertThat(jobs.findBySubmissionId(timedOut).orElseThrow().getLastErrorCode()).isEqualTo("DEADLINE_EXCEEDED");
        verifyNoInteractions(execution);
    }

    @Test void acceptedProgressCannotBeForgedAndRepeatedAcceptanceDoesNotDoubleCount() throws Exception {
        UUID id = submit(request(UUID.randomUUID()));
        assertThatThrownBy(() -> jdbc.update("UPDATE student_problem_progress SET accepted_submission_id=?, accepted_at=NOW() WHERE student_id=?", id, student.getId()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        coordinator.start(claim()); awaitStatus(id, ExecutionStatus.FINISHED);
        UUID next = submit(request(UUID.randomUUID())); coordinator.start(claim()); awaitStatus(next, ExecutionStatus.FINISHED);
        assertThat(profile().getAcceptedSubmissionId()).isEqualTo(id); assertThat(profile().getFormalAttempts()).isEqualTo(2);
        api(get("/api/users/me/progress"), studentToken, null).andExpect(status().isOk()).andExpect(jsonPath("$.summary.solvedTasks").value(1));
    }

    @Test void sampleRunsAreIdempotentOwnedSampleOnlyAndNeverIncreaseFormalAttempts() throws Exception {
        var body = request(UUID.randomUUID());
        var result = api(post("/api/problems/{id}/runs", problemId), studentToken, body).andExpect(status().isAccepted()).andReturn();
        UUID run = UUID.fromString(tree(result).get("id").asText());
        api(post("/api/problems/{id}/runs", problemId), studentToken, body).andExpect(status().isAccepted()).andExpect(jsonPath("$.id").value(run.toString()));
        api(get("/api/runs/{id}", run), null, null).andExpect(status().isUnauthorized());
        api(get("/api/runs/{id}", run), otherToken, null).andExpect(status().isNotFound());
        assertThat(progress.findByStudentIdOrderByLastActivityAtDesc(student.getId())).isEmpty();
        coordinator.start(claim());
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> api(get("/api/runs/{id}", run), studentToken, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINISHED")).andExpect(jsonPath("$.verdict").value("ACCEPTED")));
        verify(execution).submitBatch(argThat(batch -> batch.getTests().size() == 1 && !batch.getTests().getFirst().isHidden()));
        assertThat(progress.findByStudentIdOrderByLastActivityAtDesc(student.getId())).isEmpty();
        assertThat(submissions.countByStudentIdAndStatusIn(student.getId(), List.of(ExecutionStatus.FINISHED))).isZero();
        UUID formal = submit(request(UUID.randomUUID()));
        assertThat(submissions.findById(formal).orElseThrow().getAttemptNumber()).isEqualTo(1);
        jdbc.update("UPDATE execution_runs SET created_at=NOW()-INTERVAL '1 hour', expires_at=NOW()-INTERVAL '1 minute' WHERE id=?", run);
        api(get("/api/runs/{id}", run), studentToken, null).andExpect(status().isNotFound());
        cleanup.removeExpired();
        assertThat(runs.findById(run)).isEmpty(); assertThat(submissions.findById(formal)).isPresent();
    }

    @Test void coordinatorTickClaimsExecutesAndReapsAnUnresponsiveJobAtItsDeadline() throws Exception {
        UUID id = submit(request(UUID.randomUUID()));
        try {
            properties.setEnabled(true); coordinator.tick(); awaitStatus(id, ExecutionStatus.FINISHED);
            var pending = new CompletableFuture<ExecutionBatchResult>();
            doReturn(pending).when(execution).pollBatch(any()); clearInvocations(execution);
            UUID stalled = submit(request(UUID.randomUUID())); coordinator.tick();
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> verify(execution).pollBatch(any()));
            jdbc.update("UPDATE execution_jobs SET created_at=NOW()-INTERVAL '2 minutes', deadline_at=NOW()-INTERVAL '1 minute' WHERE submission_id=?", stalled);
            coordinator.tick(); awaitStatus(stalled, ExecutionStatus.SYSTEM_ERROR);
            pending.complete(ExecutionBatchResult.builder().status(ExecutionStatus.FINISHED).verdict(ExecutionVerdict.ACCEPTED).cases(List.of()).build());
            assertThat(profile().getFormalAttempts()).isEqualTo(2);
            assertThat(profile().getAcceptedSubmissionId()).isEqualTo(id);
            assertThat(jobs.findBySubmissionId(stalled).orElseThrow().getRetryAllowed()).isFalse();
        } finally { properties.setEnabled(false); }
    }

    @Test void sampleAndSubmissionShareAdmissionLimitsAndGlobalRejectionDoesNotConsumeAnAttempt() throws Exception {
        properties.setMaxPerUser(1);
        api(post("/api/problems/{id}/runs", problemId), studentToken, request(UUID.randomUUID())).andExpect(status().isAccepted());
        api(post("/api/problems/{id}/submissions", problemId), studentToken, request(UUID.randomUUID())).andExpect(status().isTooManyRequests());
        assertThat(progress.findByStudentIdOrderByLastActivityAtDesc(student.getId())).isEmpty();
        properties.setMaxQueued(1);
        api(post("/api/problems/{id}/submissions", problemId), otherToken, request(UUID.randomUUID())).andExpect(status().isTooManyRequests());
    }

    @Test void collectionProgressRequiresActualAcceptanceByTheRequestingStudent() throws Exception {
        var body = Map.of("lockVersion", 0, "title", "Practice", "description", "", "visibility", "AUTHENTICATED",
                "problems", List.of(Map.of("problemId", problemId, "problemVersionId", versionId)));
        long collectionId = tree(api(post("/api/teacher/collections"), teacherToken, body).andExpect(status().isCreated()).andReturn()).get("id").asLong();
        api(post("/api/teacher/collections/{id}/publish", collectionId), teacherToken, Map.of("lockVersion", 0)).andExpect(status().isOk());
        api(get("/api/collections/{id}/progress", collectionId), null, null).andExpect(status().isUnauthorized());
        api(get("/api/collections/{id}/progress", collectionId), studentToken, null).andExpect(status().isOk()).andExpect(jsonPath("$.solved").value(0));
        var submission = new HashMap<>(request(UUID.randomUUID())); submission.put("collectionId", collectionId);
        UUID id = submit(submission); coordinator.start(claim()); awaitStatus(id, ExecutionStatus.FINISHED);
        api(get("/api/collections/{id}/progress", collectionId), studentToken, null).andExpect(status().isOk()).andExpect(jsonPath("$.solved").value(1));
        api(get("/api/collections/{id}/progress", collectionId), otherToken, null).andExpect(status().isOk()).andExpect(jsonPath("$.solved").value(0));
    }

    @Test void partialInfrastructureFailurePreservesFinishedCasesAndRepollsOnlyUnfinishedTickets() throws Exception {
        UUID id = submit(request(UUID.randomUUID())); var lease = claim(); var created = tickets(store.next(lease).create()); store.tickets(lease, created);
        var sample = result(created).getCases().getFirst();
        var hidden = created.getTickets().getLast();
        store.polled(lease, ExecutionBatchResult.builder().status(ExecutionStatus.SYSTEM_ERROR).failureCode(SandboxFailureCode.PROVIDER_INTERNAL_ERROR)
                .cases(List.of(sample, CaseExecutionResult.builder().testCaseId(hidden.getTestCaseId()).status(ExecutionStatus.SYSTEM_ERROR)
                        .failureCode(SandboxFailureCode.PROVIDER_INTERNAL_ERROR).build())).build());
        assertThat(profile().getAcceptedSubmissionId()).isNull();
        jdbc.update("UPDATE execution_jobs SET next_attempt_at=NOW() WHERE submission_id=?", id);
        coordinator.start(claim()); awaitStatus(id, ExecutionStatus.FINISHED);
        verify(execution, never()).submitBatch(any());
        verify(execution).pollBatch(argThat(batch -> batch.getTickets().size() == 1 && batch.getTickets().getFirst().isHidden()));
        assertThat(profile().getAcceptedSubmissionId()).isEqualTo(id);
    }

    @Test void savedProviderDeadlineIsNotExtendedByLeaseRecovery() throws Exception {
        UUID id = submit(request(UUID.randomUUID())); var old = claim(); var saved = tickets(store.next(old).create()); store.tickets(old, saved);
        jdbc.update("UPDATE execution_case_results SET provider_deadline_at=NOW()-INTERVAL '1 second' WHERE job_id=?", old.getJobId());
        expireLease(old); coordinator.start(claim()); awaitStatus(id, ExecutionStatus.SYSTEM_ERROR);
        assertThat(jobs.findBySubmissionId(id).orElseThrow().getLastErrorCode()).isEqualTo("DEADLINE_EXCEEDED");
        verifyNoInteractions(execution);
    }

    @Test void failedProgressWriteRollsBackFinalVerdictAndCanRecoverWithoutNewExecution() throws Exception {
        UUID id = submit(request(UUID.randomUUID())); var old = claim();
        clearInvocations(progress);
        doThrow(new IllegalStateException("synthetic-private-database-error")).when(progress).lockProgress(student.getId(), problemId);
        coordinator.start(old);
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> verify(progress).lockProgress(student.getId(), problemId));
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(jobs.findBySubmissionId(id).orElseThrow().getStatus()).isEqualTo(ExecutionStatus.RUNNING));
        assertThat(submissions.findById(id).orElseThrow().getVerdict()).isNull();
        reset(progress); clearInvocations(execution); expireLease(old);
        var replacement = claim();
        var restarted = new JudgingCoordinator(store, execution, properties, cleanup, clock, judgingExecutor);
        restarted.start(replacement); awaitStatus(id, ExecutionStatus.FINISHED); restarted.stop();
        assertThat(profile().getAcceptedSubmissionId()).isEqualTo(id); verifyNoInteractions(execution);
    }

    @Test void malformedTokensAndExhaustedOrphanedLeasesFailClosed() throws Exception {
        UUID id = submit(request(UUID.randomUUID())); var lease = claim(); var plan = store.next(lease);
        store.tickets(lease, SubmittedExecutionBatch.builder().deadline(plan.create().getDeadline()).tickets(List.of()).build());
        awaitStatus(id, ExecutionStatus.SYSTEM_ERROR);
        assertThat(jobs.findBySubmissionId(id).orElseThrow().getLastErrorCode()).isEqualTo("SUBMISSION_UNKNOWN");
        UUID orphan = submit(request(UUID.randomUUID()));
        var first = claim(); assertThat(store.heartbeat(first)).isTrue(); expireLease(first);
        var second = claim(); expireLease(second); var third = claim(); expireLease(third);
        store.expire(); awaitStatus(orphan, ExecutionStatus.SYSTEM_ERROR);
        assertThat(jobs.findBySubmissionId(orphan).orElseThrow().getLastErrorCode()).isEqualTo("RETRY_EXHAUSTED");
        verifyNoInteractions(execution);
    }

    private SubmittedExecutionBatch tickets(ExecutionBatch batch) {
        return SubmittedExecutionBatch.builder().runtimeKey(batch.getRuntimeKey()).runtimeVersion(batch.getRuntimeVersion()).limits(batch.getLimits())
                .comparison(batch.getComparison()).deadline(batch.getDeadline()).tickets(batch.getTests().stream().map(test -> ExecutionTicket.builder()
                        .testCaseId(test.getTestCaseId()).position(test.getPosition()).hidden(test.isHidden()).expectedOutput(test.getExpectedOutput())
                        .token(UUID.randomUUID().toString()).build()).toList()).build();
    }
    private ExecutionBatchResult result(SubmittedExecutionBatch batch) {
        return new ExecutionResultAggregator().aggregate(batch.getTickets().stream().map(ticket -> CaseExecutionResult.builder()
                .testCaseId(ticket.getTestCaseId()).position(ticket.getPosition()).hidden(ticket.isHidden()).status(ExecutionStatus.FINISHED)
                .verdict(ticket.isHidden() ? verdict.get() : ExecutionVerdict.ACCEPTED).timeConsumedMs(17).memoryConsumedBytes(1024L)
                .diagnostics(ExecutionDiagnostics.builder().stdout(SECRET).stderr(SECRET).compileOutput(SECRET).build()).build()).toList());
    }
    private com.masters.socratesai.submission.model.StudentProblemProgress profile() { return progress.findByStudentIdOrderByLastActivityAtDesc(student.getId()).getFirst(); }
    private JudgingLease claim() { return store.claim("fixture-worker", 1).getFirst(); }
    private void expireLease(JudgingLease lease) { jdbc.update("UPDATE execution_jobs SET lease_expires_at=NOW()-INTERVAL '1 second' WHERE id=?", lease.getJobId()); }
    private void awaitStatus(UUID id, ExecutionStatus status) { await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(submissions.findById(id).orElseThrow().getStatus()).isEqualTo(status)); }
    private Map<String,Object> request(UUID key) { return Map.of("problemVersionId", versionId, "languageKey", "python3", "sourceCode", SOURCE, "idempotencyKey", key); }
    private UUID submit(Map<String,Object> body) throws Exception {
        var response = api(post("/api/problems/{id}/submissions", problemId), studentToken, body).andExpect(status().isAccepted()).andReturn();
        var id = UUID.fromString(tree(response).get("id").asText());
        assertThat(response.getResponse().getHeader("Location")).isEqualTo("/api/submissions/" + id);
        return id;
    }
    private User user(UserRole role) { return users.saveAndFlush(User.builder().email(UUID.randomUUID()+"@example.test").passwordHash("unused-fixture-hash")
            .fullName("Synthetic user").role(role).settings(UserSettings.builder().build()).createdAt(LocalDateTime.now()).build()); }
    private String token(User user) { return jwt.generateToken(new SecurityUserDetails(user)); }
    private ResultActions api(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body));
        return mvc.perform(request);
    }
    private JsonNode tree(MvcResult result) throws Exception { return json.readTree(result.getResponse().getContentAsString()); }
}
