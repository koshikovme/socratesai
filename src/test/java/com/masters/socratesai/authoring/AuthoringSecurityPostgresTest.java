package com.masters.socratesai.authoring;

import com.fasterxml.jackson.databind.*;
import com.masters.socratesai.execution.domain.*;
import com.masters.socratesai.execution.domain.port.CodeExecutionPort;
import com.masters.socratesai.execution.model.*;
import com.masters.socratesai.security.*;
import com.masters.socratesai.task.dto.*;
import com.masters.socratesai.task.model.*;
import com.masters.socratesai.user.model.*;
import com.masters.socratesai.user.repo.UserRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Every DB connection comes from this disposable container. Execution is a port fixture, never a compiler. */
@Testcontainers @SpringBootTest(properties = {"spring.config.import=", "app.admin.bootstrap.enabled=false",
        "app.gemini.enabled=false", "app.openai.enabled=false", "app.policy.ml.enabled=false"})
@AutoConfigureMockMvc @ActiveProfiles("test")
class AuthoringSecurityPostgresTest {
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
    @Autowired PasswordEncoder passwords;
    @Autowired JwtService jwt;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean CodeExecutionPort execution;
    @MockitoBean ExecutionRuntimeCatalogPort catalog;
    @MockitoSpyBean com.masters.socratesai.admin.repo.RoleChangeAuditRepository audits;
    private final ObjectMapper json = new ObjectMapper();
    private final AtomicReference<ExecutionVerdict> outcome = new AtomicReference<>(ExecutionVerdict.ACCEPTED);
    private User teacherA, teacherB, student, admin;
    private String tokenA, tokenB, tokenStudent, tokenAdmin;
    private static String passwordHash;
    private static final String HIDDEN = "hidden-case-private-value";
    private static final String REFERENCE = "staff-reference-private-source";

    @BeforeEach void fixtures() {
        if (passwordHash == null) passwordHash = passwords.encode("Local-fixture-password-123");
        teacherA = user(UserRole.TEACHER); teacherB = user(UserRole.TEACHER); student = user(UserRole.STUDENT); admin = user(UserRole.ADMIN);
        tokenA = token(teacherA); tokenB = token(teacherB); tokenStudent = token(student); tokenAdmin = token(admin);
        when(catalog.configured()).thenReturn(List.of(
                new ExecutionRuntimeCatalogPort.RuntimeDescriptor("python3", "fixture-python", "3.fixture", "main.py"),
                new ExecutionRuntimeCatalogPort.RuntimeDescriptor("java", "fixture-java", "21.fixture", "Main")));
        when(execution.submitBatch(any())).thenAnswer(call -> {
            ExecutionBatch batch = call.getArgument(0);
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return CompletableFuture.completedFuture(SubmittedExecutionBatch.builder().limits(batch.getLimits()).comparison(batch.getComparison())
                    .deadline(batch.getDeadline()).runtimeKey(batch.getRuntimeKey()).runtimeVersion(batch.getRuntimeVersion())
                    .tickets(batch.getTests().stream().map(test -> ExecutionTicket.builder().testCaseId(test.getTestCaseId()).position(test.getPosition())
                            .hidden(test.isHidden()).expectedOutput(test.getExpectedOutput()).token(UUID.randomUUID().toString()).build()).toList()).build());
        });
        when(execution.pollBatch(any())).thenAnswer(call -> {
            SubmittedExecutionBatch batch = call.getArgument(0);
            return CompletableFuture.completedFuture(new ExecutionResultAggregator().aggregate(batch.getTickets().stream().map(ticket ->
                    CaseExecutionResult.builder().testCaseId(ticket.getTestCaseId()).position(ticket.getPosition()).hidden(ticket.isHidden())
                            .status(ExecutionStatus.FINISHED).verdict(outcome.get())
                            .diagnostics(ExecutionDiagnostics.builder().stdout(HIDDEN).stderr(REFERENCE).compileOutput(REFERENCE).build()).build()).toList()));
        });
    }

    @ParameterizedTest @ValueSource(strings = {"TEACHER", "ADMIN", "MENTOR", "teacher"})
    void publicRegistrationRejectsPrivilegeRequests(String role) throws Exception {
        String email = UUID.randomUUID() + "@example.test";
        api(post("/api/auth/register"), null, Map.of("email", email, "password", "Local-fixture-password-123", "fullName", "Student", "role", role))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
        assertThat(users.findByEmail(email)).isEmpty();
    }

    @Test void omittedOrStudentRegistrationRoleAlwaysCreatesStudent() throws Exception {
        String email = UUID.randomUUID() + "@example.test";
        var result = api(post("/api/auth/register"), null, Map.of("email", email, "password", "Local-fixture-password-123", "fullName", "Student"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("STUDENT")).andReturn();
        String token = tree(result).get("token").asText();
        api(get("/api/teacher/problems"), token, null).andExpect(status().isForbidden());
        assertThat(users.findByEmail(email).orElseThrow().getRole()).isEqualTo(UserRole.STUDENT);
    }

    @Test void onlyAdminsCanAssignRolesAndExistingJwtCannotRetainOldPrivilege() throws Exception {
        var change = Map.of("expectedRole", "TEACHER", "role", "STUDENT", "reason", "Synthetic security regression");
        api(patch("/api/admin/users/{id}/role", teacherA.getId()), null, change).andExpect(status().isUnauthorized());
        api(patch("/api/admin/users/{id}/role", teacherA.getId()), tokenStudent, change).andExpect(status().isForbidden());
        api(patch("/api/admin/users/{id}/role", teacherA.getId()), tokenB, change).andExpect(status().isForbidden());
        api(patch("/api/admin/users/{id}/role", teacherA.getId()), tokenAdmin, change).andExpect(status().isOk()).andExpect(jsonPath("$.role").value("STUDENT"));
        api(get("/api/teacher/problems"), tokenA, null).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM role_change_audit WHERE target_id=? AND actor_id=? AND previous_role='TEACHER' AND new_role='STUDENT'",
                Integer.class, teacherA.getId(), admin.getId())).isEqualTo(1);
        api(patch("/api/admin/users/{id}/role", teacherA.getId()), tokenAdmin, change).andExpect(status().isConflict());
    }

    @Test void auditFailureRollsBackRoleChangeAndDoesNotExposeDatabaseDetails() throws Exception {
        doThrow(new org.springframework.dao.DataIntegrityViolationException("private-sql-structure-secret"))
                .when(audits).save(any());
        var response = api(patch("/api/admin/users/{id}/role", teacherA.getId()), tokenAdmin,
                Map.of("expectedRole", "TEACHER", "role", "STUDENT", "reason", "Transactional audit fixture"))
                .andExpect(status().isConflict()).andReturn().getResponse().getContentAsString();
        assertThat(users.findById(teacherA.getId()).orElseThrow().getRole()).isEqualTo(UserRole.TEACHER);
        assertThat(response).doesNotContain("private-sql-structure-secret", "DataIntegrityViolationException", "role_change_audit");
    }

    @Test void ownershipIsEnforcedForReadsWritesVerificationAndLegacyFacade() throws Exception {
        var problem = createProblem("AUTHENTICATED");
        long id = problem.get("id").asLong();
        api(get("/api/teacher/problems/{id}", id), tokenA, null).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString(HIDDEN)));
        api(get("/api/teacher/problems/{id}", id), tokenB, null).andExpect(status().isNotFound());
        api(get("/api/teacher/problems/{id}", id), tokenStudent, null).andExpect(status().isForbidden());
        api(get("/api/teacher/problems/{id}", id), null, null).andExpect(status().isUnauthorized());
        api(get("/api/teacher/problems/{id}", id), tokenAdmin, null).andExpect(status().isOk());
        api(put("/api/teacher/problems/{id}/draft", id), tokenB, draft(0, "AUTHENTICATED", "Foreign edit")).andExpect(status().isNotFound());
        api(post("/api/teacher/problems/{id}/verify", id), tokenB, verification(0)).andExpect(status().isNotFound());
        api(delete("/api/teacher/problems/{id}", id).param("lockVersion", "0"), tokenB, null).andExpect(status().isNotFound());
        api(get("/api/tasks/{id}", id), tokenB, null).andExpect(status().isNotFound());
        api(get("/api/tasks/public/{id}", id), tokenStudent, null).andExpect(status().isNotFound());
        api(get("/api/problems/{id}", id), tokenStudent, null).andExpect(status().isNotFound());
        assertThat(tree(api(get("/api/teacher/problems"), tokenB, null).andExpect(status().isOk()).andReturn()).get("totalElements").asInt()).isZero();
    }

    @Test void publicationNeedsCompleteTestsAndSuccessfulExactDraftVerification() throws Exception {
        var problem = createProblem("AUTHENTICATED"); long id = problem.get("id").asLong();
        publish(id, 0, UUID.randomUUID(), tokenA).andExpect(status().isConflict());
        String verification = verifyProblem(id, 0);
        var published = tree(publish(id, 0, UUID.fromString(verification), tokenA).andExpect(status().isOk()).andReturn());
        assertThat(published.get("status").asText()).isEqualTo("PUBLISHED");
        assertThat(published.get("version").get("status").asText()).isEqualTo("PUBLISHED");
        String studentJson = api(get("/api/problems/{id}", id), tokenStudent, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.samples.length()").value(1)).andReturn().getResponse().getContentAsString();
        assertThat(studentJson).doesNotContain(HIDDEN, REFERENCE, "hidden", "testCaseId", "providerToken", "runtimeKey", "compileOutput", "ownerId");
        api(get("/api/problems/{id}", id), null, null).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT reference_verification_id IS NOT NULL FROM problem_versions WHERE id=?", Boolean.class,
                published.get("version").get("id").asLong())).isTrue();
    }

    @Test void editAfterVerificationInvalidatesPublicationAndStaleEditsConflict() throws Exception {
        var problem = createProblem("PUBLIC"); long id = problem.get("id").asLong();
        String verification = verifyProblem(id, 0);
        var updated = tree(api(put("/api/teacher/problems/{id}/draft", id), tokenA, draft(0, "PUBLIC", "Updated statement"))
                .andExpect(status().isOk()).andReturn());
        long revision = updated.get("lockVersion").asLong();
        assertThat(revision).isGreaterThan(0);
        api(put("/api/teacher/problems/{id}/draft", id), tokenA, draft(0, "PUBLIC", "Lost update")).andExpect(status().isConflict());
        publish(id, revision, UUID.fromString(verification), tokenA).andExpect(status().isConflict());
        api(get("/api/problems/{id}", id), tokenStudent, null).andExpect(status().isNotFound());
    }

    @ParameterizedTest @ValueSource(strings = {"SAMPLE_ONLY", "HIDDEN_ONLY", "NO_LANGUAGES"})
    void incompleteDraftCannotBeVerifiedOrPublished(String kind) throws Exception {
        var body = draft(0, "PUBLIC", "Incomplete draft");
        if (kind.equals("NO_LANGUAGES")) body.put("languages", List.of());
        else body.put("tests", List.of(test(0, kind.equals("HIDDEN_ONLY"))));
        long id = tree(api(post("/api/teacher/problems"), tokenA, body).andExpect(status().isCreated()).andReturn()).get("id").asLong();
        api(post("/api/teacher/problems/{id}/verify", id), tokenA, verification(0)).andExpect(status().isBadRequest());
        publish(id, 0, UUID.randomUUID(), tokenA).andExpect(status().isBadRequest());
        verifyNoInteractions(execution);
    }

    @Test void wrongReferenceAndUnavailableSandboxFailClosed() throws Exception {
        var problem = createProblem("PUBLIC"); long id = problem.get("id").asLong();
        outcome.set(ExecutionVerdict.WRONG_ANSWER);
        String verification = verifyProblem(id, 0, "WRONG_ANSWER");
        publish(id, 0, UUID.fromString(verification), tokenA).andExpect(status().isConflict());
        when(catalog.configured()).thenReturn(List.of());
        api(post("/api/teacher/problems/{id}/verify", id), tokenA, verification(0)).andExpect(status().isServiceUnavailable());
    }

    @Test void verificationChecksEveryConfiguredLanguageAndEveryBatch() throws Exception {
        var body = draft(0, "PUBLIC", "Multiple runtimes");
        body.put("languages", List.of(language("python3"), language("java")));
        body.put("tests", java.util.stream.IntStream.range(0, 7).mapToObj(i -> test(i, i > 0)).toList());
        long id = tree(api(post("/api/teacher/problems"), tokenA, body).andExpect(status().isCreated()).andReturn()).get("id").asLong();
        var verify = Map.of("lockVersion", 0, "solutions", List.of(Map.of("languageKey", "python3", "sourceCode", REFERENCE), Map.of("languageKey", "java", "sourceCode", REFERENCE)));
        var response = tree(api(post("/api/teacher/problems/{id}/verify", id), tokenA, verify).andExpect(status().isAccepted()).andReturn());
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                api(get("/api/teacher/problems/{id}/verifications/{verification}", id, response.get("id").asText()), tokenA, null)
                    .andExpect(status().isOk()).andExpect(jsonPath("$.testsTotal").value(14)).andExpect(jsonPath("$.testsPassed").value(14)));
        verify(execution, times(4)).submitBatch(any());
        publish(id, 0, UUID.fromString(response.get("id").asText()), tokenA).andExpect(status().isOk());
    }

    @Test void clonePreservesPublishedSnapshotAndArchivedProblemDisappearsFromStudentApi() throws Exception {
        var published = publishedProblem("PUBLIC"); long id = published.get("id").asLong();
        long oldVersion = published.get("version").get("id").asLong();
        var cloned = tree(api(post("/api/teacher/problems/{id}/draft", id), tokenA, Map.of("lockVersion", published.get("lockVersion").asLong()))
                .andExpect(status().isOk()).andReturn());
        assertThat(cloned.get("version").get("versionNumber").asInt()).isEqualTo(2);
        api(put("/api/teacher/problems/{id}/draft", id), tokenA, draft(cloned.get("lockVersion").asLong(), "AUTHENTICATED", "Private draft text"))
                .andExpect(status().isOk());
        api(get("/api/problems/{id}", id), null, null).andExpect(status().isOk()).andExpect(jsonPath("$.problemVersionId").value(oldVersion))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Private draft text"))));
        long revision = tree(api(get("/api/teacher/problems/{id}", id), tokenA, null).andReturn()).get("lockVersion").asLong();
        api(post("/api/teacher/problems/{id}/archive", id), tokenA, Map.of("lockVersion", revision)).andExpect(status().isOk());
        api(get("/api/problems/{id}", id), tokenStudent, null).andExpect(status().isNotFound());
        api(delete("/api/teacher/problems/{id}", id).param("lockVersion", Long.toString(revision + 1)), tokenA, null).andExpect(status().isConflict());
    }

    @Test void collectionOwnershipReorderAndPinnedVersionValidationAreAtomic() throws Exception {
        var first = publishedProblem("PUBLIC"); var second = publishedProblem("PUBLIC");
        var selection = List.of(member(first), member(second));
        var created = tree(api(post("/api/teacher/collections"), tokenA, collection(0, "PUBLIC", selection)).andExpect(status().isCreated()).andReturn());
        long id = created.get("id").asLong();
        api(get("/api/teacher/collections/{id}", id), tokenB, null).andExpect(status().isNotFound());
        api(get("/api/teacher/collections/{id}", id), tokenStudent, null).andExpect(status().isForbidden());
        api(get("/api/teacher/collections/{id}", id), null, null).andExpect(status().isUnauthorized());
        api(get("/api/teacher/collections/{id}", id), tokenAdmin, null).andExpect(status().isOk());
        api(put("/api/teacher/collections/{id}/problems", id), tokenB, Map.of("lockVersion", 0, "problems", selection)).andExpect(status().isNotFound());
        var reordered = tree(api(put("/api/teacher/collections/{id}/problems", id), tokenA,
                Map.of("lockVersion", 0, "problems", List.of(member(second), member(first)))).andExpect(status().isOk()).andReturn());
        assertThat(reordered.get("problems").get(0).get("problemId").asLong()).isEqualTo(second.get("id").asLong());
        long revision = reordered.get("lockVersion").asLong();
        api(put("/api/teacher/collections/{id}/problems", id), tokenA, Map.of("lockVersion", 0, "problems", selection)).andExpect(status().isConflict());
        api(put("/api/teacher/collections/{id}/problems", id), tokenA, Map.of("lockVersion", revision, "problems", List.of(
                Map.of("problemId", first.get("id").asLong(), "problemVersionId", second.get("version").get("id").asLong())))).andExpect(status().isBadRequest());
        var unchanged = tree(api(get("/api/teacher/collections/{id}", id), tokenA, null).andReturn());
        assertThat(unchanged.get("lockVersion").asLong()).isEqualTo(revision);
        assertThat(unchanged.get("problems")).isEqualTo(reordered.get("problems"));
        api(post("/api/teacher/collections/{id}/publish", id), tokenA, Map.of("lockVersion", revision)).andExpect(status().isOk());
        String publicJson = api(get("/api/collections/{id}", id), null, null).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(publicJson).doesNotContain(HIDDEN, REFERENCE, "tests", "hidden", "ownerId", "lockVersion", "runtime");
        api(get("/api/collections"), null, null).andExpect(status().isOk());
    }

    @Test void publicCollectionsCannotDiscloseAuthenticatedOnlyProblemsAndDraftsCannotBeMembers() throws Exception {
        var published = publishedProblem("AUTHENTICATED");
        api(post("/api/teacher/collections"), tokenA, collection(0, "PUBLIC", List.of(member(published)))).andExpect(status().isConflict());
        var draft = createProblem("PUBLIC");
        api(post("/api/teacher/collections"), tokenA, collection(0, "AUTHENTICATED", List.of(member(draft)))).andExpect(status().isBadRequest());
        api(post("/api/teacher/collections"), tokenStudent, collection(0, "PUBLIC", List.of())).andExpect(status().isForbidden());
        api(post("/api/teacher/collections"), null, collection(0, "PUBLIC", List.of())).andExpect(status().isUnauthorized());
    }

    @Test void validationAndMalformedJwtUseSafeErrorEnvelope() throws Exception {
        var body = draft(0, "PUBLIC", "sql-private-marker <script>bad</script>");
        body.put("languages", List.of(Map.of("languageKey", "python3", "timeLimitMs", 40000, "wallTimeLimitMs", 1, "memoryLimitKb", -1, "outputLimitBytes", 70000)));
        var response = api(post("/api/teacher/problems"), tokenA, body).andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("sql-private-marker", "40000", "problem_language", "Exception", "stackTrace").contains("requestId");
        api(get("/api/teacher/problems"), "invalid-private-jwt", null).andExpect(status().isUnauthorized())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("invalid-private-jwt"))));
        api(get("/api/teacher/problems").param("size", "100000"), tokenA, null).andExpect(status().isBadRequest());
    }

    @Test void lastAdminCannotBeRemovedEvenByConcurrentRoleChanges() throws Exception {
        jdbc.update("UPDATE users SET role='STUDENT' WHERE role='ADMIN' AND id<>?", admin.getId());
        api(patch("/api/admin/users/{id}/role", admin.getId()), tokenAdmin,
                Map.of("expectedRole", "ADMIN", "role", "STUDENT", "reason", "Synthetic last-admin check")).andExpect(status().isConflict());
        jdbc.update("UPDATE users SET role='ADMIN' WHERE id=?", teacherB.getId());
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> {
                start.await();
                return api(patch("/api/admin/users/{id}/role", admin.getId()), tokenAdmin,
                        Map.of("expectedRole", "ADMIN", "role", "STUDENT", "reason", "Concurrent fixture A")).andReturn().getResponse().getStatus();
            });
            var second = pool.submit(() -> {
                start.await();
                return api(patch("/api/admin/users/{id}/role", teacherB.getId()), tokenB,
                        Map.of("expectedRole", "ADMIN", "role", "STUDENT", "reason", "Concurrent fixture B")).andReturn().getResponse().getStatus();
            });
            start.countDown();
            assertThat(List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS))).containsExactlyInAnyOrder(200, 409);
        }
        assertThat(users.countByRole(UserRole.ADMIN)).isEqualTo(1);
    }

    @Test void twoReordersWithTheSameRevisionHaveExactlyOneWinner() throws Exception {
        var first = publishedProblem("PUBLIC"); var second = publishedProblem("PUBLIC");
        long id = tree(api(post("/api/teacher/collections"), tokenA, collection(0, "PUBLIC", List.of(member(first), member(second))))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var forward = pool.submit(() -> {
                start.await();
                return api(put("/api/teacher/collections/{id}/problems", id), tokenA,
                        Map.of("lockVersion", 0, "problems", List.of(member(first), member(second)))).andReturn().getResponse().getStatus();
            });
            var reverse = pool.submit(() -> {
                start.await();
                return api(put("/api/teacher/collections/{id}/problems", id), tokenA,
                        Map.of("lockVersion", 0, "problems", List.of(member(second), member(first)))).andReturn().getResponse().getStatus();
            });
            start.countDown();
            assertThat(List.of(forward.get(5, TimeUnit.SECONDS), reverse.get(5, TimeUnit.SECONDS))).containsExactlyInAnyOrder(200, 409);
        }
        var positions = jdbc.queryForList("SELECT position FROM collection_problems WHERE collection_id=? ORDER BY position", Integer.class, id);
        assertThat(positions).containsExactly(0, 1);
    }

    @Test void collectionLifecycleAndRemovingAllPublishedMembersAreProtected() throws Exception {
        long emptyId = tree(api(post("/api/teacher/collections"), tokenA, collection(0, "PUBLIC", List.of())).andExpect(status().isCreated()).andReturn()).get("id").asLong();
        api(post("/api/teacher/collections/{id}/publish", emptyId), tokenA, Map.of("lockVersion", 0)).andExpect(status().isConflict());
        api(delete("/api/teacher/collections/{id}", emptyId).param("lockVersion", "0"), tokenA, null).andExpect(status().isNoContent());
        var problem = publishedProblem("PUBLIC");
        long id = tree(api(post("/api/teacher/collections"), tokenA, collection(0, "PUBLIC", List.of(member(problem)))).andExpect(status().isCreated()).andReturn()).get("id").asLong();
        api(put("/api/teacher/collections/{id}", id), tokenA, collection(0, "PUBLIC", List.of(member(problem)))).andExpect(status().isOk());
        api(post("/api/teacher/collections/{id}/publish", id), tokenA, Map.of("lockVersion", 1)).andExpect(status().isOk());
        api(put("/api/teacher/collections/{id}/problems", id), tokenA, Map.of("lockVersion", 2, "problems", List.of())).andExpect(status().isConflict());
        api(delete("/api/teacher/collections/{id}", id).param("lockVersion", "2"), tokenA, null).andExpect(status().isConflict());
        api(post("/api/teacher/collections/{id}/archive", id), tokenA, Map.of("lockVersion", 2)).andExpect(status().isOk());
        api(get("/api/collections/{id}", id), tokenStudent, null).andExpect(status().isNotFound());
        api(put("/api/teacher/collections/{id}", id), tokenA, collection(3, "PUBLIC", List.of(member(problem)))).andExpect(status().isConflict());
    }

    @Test void archivingMemberHidesCollectionAndDoesNotLeakPinnedPrivateTitles() throws Exception {
        var problem = publishedProblem("PUBLIC");
        long id = tree(api(post("/api/teacher/collections"), tokenA, collection(0, "PUBLIC", List.of(member(problem)))).andExpect(status().isCreated()).andReturn()).get("id").asLong();
        api(post("/api/teacher/collections/{id}/publish", id), tokenA, Map.of("lockVersion", 0)).andExpect(status().isOk());
        api(post("/api/teacher/problems/{id}/archive", problem.get("id").asLong()), tokenA,
                Map.of("lockVersion", problem.get("lockVersion").asLong())).andExpect(status().isOk());
        api(get("/api/collections/{id}", id), tokenStudent, null).andExpect(status().isNotFound());
        var summaries = tree(api(get("/api/collections"), tokenStudent, null).andExpect(status().isOk()).andReturn());
        assertThat(summaries.get("items").findValuesAsText("id")).doesNotContain(Long.toString(id));
    }

    @Test void draftDeletionAndAdminEditsPreserveOwnership() throws Exception {
        var problem = createProblem("AUTHENTICATED"); long id = problem.get("id").asLong();
        api(put("/api/teacher/problems/{id}/draft", id), tokenAdmin, draft(0, "AUTHENTICATED", "Admin corrected statement"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ownerId").value(teacherA.getId()));
        api(delete("/api/teacher/problems/{id}", id).param("lockVersion", "1"), tokenA, null).andExpect(status().isNoContent());
        api(get("/api/teacher/problems/{id}", id), tokenA, null).andExpect(status().isNotFound());
        var published = publishedProblem("PUBLIC"); long publishedId = published.get("id").asLong();
        var cloned = tree(api(post("/api/teacher/problems/{id}/draft", publishedId), tokenA,
                Map.of("lockVersion", published.get("lockVersion").asLong())).andExpect(status().isOk()).andReturn());
        api(delete("/api/teacher/problems/{id}/draft", publishedId).param("lockVersion", cloned.get("lockVersion").asText()), tokenA, null).andExpect(status().isNoContent());
        api(get("/api/problems/{id}", publishedId), null, null).andExpect(status().isOk());
    }

    @Test void anotherProblemsVerificationAndExpiredEvidenceCannotPublish() throws Exception {
        long first = createProblem("PUBLIC").get("id").asLong();
        long second = createProblem("PUBLIC").get("id").asLong();
        var verification = UUID.fromString(verifyProblem(first, 0));
        publish(second, 0, verification, tokenA).andExpect(status().isConflict());
        api(get("/api/teacher/problems/{id}/verifications/{verification}", first, verification), tokenB, null).andExpect(status().isNotFound());
        api(get("/api/teacher/problems/{id}/verifications/{verification}", first, verification), tokenStudent, null).andExpect(status().isForbidden());
        jdbc.update("UPDATE problem_reference_verifications SET created_at=NOW()-INTERVAL '2 hours', deadline_at=NOW()-INTERVAL '1 hour', valid_until=NOW()-INTERVAL '1 minute' WHERE id=?", verification);
        publish(first, 0, verification, tokenA).andExpect(status().isConflict());
    }

    @Test void providerFailureIsRecordedWithoutSourceAndCannotPublish() throws Exception {
        long id = createProblem("PUBLIC").get("id").asLong();
        doReturn(CompletableFuture.failedFuture(new IllegalStateException(REFERENCE + HIDDEN))).when(execution).submitBatch(any());
        String verification = tree(api(post("/api/teacher/problems/{id}/verify", id), tokenA, verification(0)).andExpect(status().isAccepted()).andReturn()).get("id").asText();
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            var result = api(get("/api/teacher/problems/{id}/verifications/{verification}", id, verification), tokenA, null)
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SYSTEM_ERROR")).andReturn();
            assertThat(result.getResponse().getContentAsString()).doesNotContain(REFERENCE, HIDDEN, "Exception");
        });
        publish(id, 0, UUID.fromString(verification), tokenA).andExpect(status().isConflict());
    }

    @Test void publicSampleOrdinalsDoNotDiscloseHiddenTestPositions() throws Exception {
        var body = draft(0, "PUBLIC", "Interleaved samples");
        body.put("tests", List.of(test(0, false), test(1, true), test(2, false)));
        long id = tree(api(post("/api/teacher/problems"), tokenA, body).andExpect(status().isCreated()).andReturn()).get("id").asLong();
        var verification = UUID.fromString(verifyProblem(id, 0));
        publish(id, 0, verification, tokenA).andExpect(status().isOk());
        api(get("/api/problems/{id}", id), null, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.samples[0].position").value(0)).andExpect(jsonPath("$.samples[1].position").value(1));
    }

    private User user(UserRole role) {
        return users.saveAndFlush(User.builder().email(UUID.randomUUID() + "@example.test").fullName("Synthetic " + role)
                .passwordHash(passwordHash).role(role).settings(UserSettings.builder().build()).createdAt(LocalDateTime.now()).build());
    }
    private String token(User user) { return jwt.generateToken(new SecurityUserDetails(user)); }
    private ResultActions api(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body));
        return mvc.perform(request);
    }
    private JsonNode tree(MvcResult result) throws Exception { return json.readTree(result.getResponse().getContentAsString()); }
    private Map<String, Object> draft(long revision, String visibility, String statement) {
        var result = new HashMap<String, Object>();
        result.put("lockVersion", revision); result.put("title", "Sum"); result.put("topic", "Arithmetic"); result.put("difficulty", "EASY");
        result.put("statement", statement); result.put("inputFormat", "Two numbers"); result.put("outputFormat", "Sum"); result.put("constraintsText", "");
        result.put("comparisonMode", "EXACT"); result.put("visibility", visibility);
        result.put("languages", List.of(language("python3"))); result.put("tests", List.of(test(0, false), test(1, true)));
        return result;
    }
    private Map<String, Object> language(String key) { return Map.of("languageKey", key, "starterCode", "", "timeLimitMs", 2000, "wallTimeLimitMs", 5000, "memoryLimitKb", 262144, "outputLimitBytes", 65536); }
    private Map<String, Object> test(int position, boolean hidden) { return Map.of("position", position, "inputData", hidden ? HIDDEN : "1 2", "expectedOutput", "3", "hidden", hidden); }
    private JsonNode createProblem(String visibility) throws Exception { return tree(api(post("/api/teacher/problems"), tokenA, draft(0, visibility, "Add two numbers" )).andExpect(status().isCreated()).andReturn()); }
    private Map<String, Object> verification(long revision) { return Map.of("lockVersion", revision, "solutions", List.of(Map.of("languageKey", "python3", "sourceCode", REFERENCE))); }
    private String verifyProblem(long id, long revision) throws Exception { return verifyProblem(id, revision, "ACCEPTED"); }
    private String verifyProblem(long id, long revision, String verdict) throws Exception {
        String verification = tree(api(post("/api/teacher/problems/{id}/verify", id), tokenA, verification(revision)).andExpect(status().isAccepted()).andReturn()).get("id").asText();
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                api(get("/api/teacher/problems/{id}/verifications/{verification}", id, verification), tokenA, null)
                        .andExpect(status().isOk()).andExpect(jsonPath("$.verdict").value(verdict)));
        return verification;
    }
    private ResultActions publish(long id, long revision, UUID verification, String token) throws Exception {
        return api(post("/api/teacher/problems/{id}/publish", id), token, Map.of("lockVersion", revision, "verificationId", verification.toString()));
    }
    private JsonNode publishedProblem(String visibility) throws Exception {
        long id = createProblem(visibility).get("id").asLong(); String verification = verifyProblem(id, 0);
        return tree(publish(id, 0, UUID.fromString(verification), tokenA).andExpect(status().isOk()).andReturn());
    }
    private Map<String, Object> member(JsonNode problem) { return Map.of("problemId", problem.get("id").asLong(), "problemVersionId", problem.get("version").get("id").asLong()); }
    private Map<String, Object> collection(long revision, String visibility, List<?> members) {
        return Map.of("lockVersion", revision, "title", "Week 1", "description", "Practice", "visibility", visibility, "problems", members);
    }
}
