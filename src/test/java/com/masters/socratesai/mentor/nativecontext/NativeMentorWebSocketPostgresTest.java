package com.masters.socratesai.mentor.nativecontext;

import com.fasterxml.jackson.databind.*;
import com.masters.socratesai.execution.domain.*;
import com.masters.socratesai.execution.domain.port.CodeExecutionPort;
import com.masters.socratesai.execution.model.*;
import com.masters.socratesai.execution.repo.ExecutionJobRepository;
import com.masters.socratesai.execution.service.*;
import com.masters.socratesai.interaction.repo.InteractionLogRepository;
import com.masters.socratesai.mentor.feedback.gemini.GeminiFeedbackService;
import com.masters.socratesai.mentor.feedback.openai.OpenAiFeedbackService;
import com.masters.socratesai.mentor.policy.*;
import com.masters.socratesai.security.*;
import com.masters.socratesai.session.repo.StudentTaskSessionRepository;
import com.masters.socratesai.submission.repo.StudentProblemProgressRepository;
import com.masters.socratesai.user.model.*;
import com.masters.socratesai.user.repo.UserRepository;
import com.masters.socratesai.websocket.config.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.messaging.converter.ByteArrayMessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.*;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.lang.reflect.Type;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real TCP STOMP/SockJS, real disposable PostgreSQL, synthetic sandbox results and local ML HTTP fixture. */
@Testcontainers @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.import=", "app.mentor.realtime.debounce-ms=100", "app.mentor.realtime.min-interval-ms=100",
        "app.mentor.realtime.timeout-ms=3000", "app.mentor.realtime.max-editors=64"})
@AutoConfigureMockMvc @ActiveProfiles("test")
class NativeMentorWebSocketPostgresTest {
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");
    @DynamicPropertySource static void db(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl); r.add("spring.datasource.username", POSTGRES::getUsername); r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver"); r.add("spring.flyway.enabled", () -> true);
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate"); r.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }
    @LocalServerPort int port;
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired JwtService jwt;
    @Autowired InteractionLogRepository logs;
    @Autowired StudentTaskSessionRepository sessions;
    @Autowired StudentProblemProgressRepository progress;
    @Autowired MentorPolicyProperties policy;
    @Autowired JudgingStore judging;
    @Autowired JudgingCoordinator worker;
    @Autowired ExecutionJobRepository jobs;
    @Autowired MentorSocketSessions socketSessions;
    @Autowired SampleRunCleanup cleanup;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @MockitoBean CodeExecutionPort execution;
    @MockitoBean ExecutionRuntimeCatalogPort catalog;
    @MockitoBean GeminiFeedbackService gemini;
    @MockitoBean OpenAiFeedbackService openai;
    private final ObjectMapper json = new ObjectMapper();
    private final List<Client> clients = new ArrayList<>();
    private User student, other;
    private String token, otherToken, teacherToken, otherTeacherToken, adminToken;
    private long problem, version, teacherId;
    private HttpServer mlServer;
    private final AtomicInteger predictions = new AtomicInteger();
    private final AtomicReference<String> predictionBody = new AtomicReference<>();
    private final AtomicReference<ExecutionVerdict> verdict = new AtomicReference<>(ExecutionVerdict.ACCEPTED);
    private static final String HIDDEN = "hidden-private-input-expected-echo";
    private static final String REFERENCE = "reference-private-source";

    @BeforeEach void fixtures() throws Exception {
        policy.setMode(PolicyMode.RULE); policy.getMl().setEnabled(false); verdict.set(ExecutionVerdict.ACCEPTED);
        student = user(UserRole.STUDENT); other = user(UserRole.STUDENT); token = token(student); otherToken = token(other);
        var teacher = user(UserRole.TEACHER); teacherToken = token(teacher); teacherId = teacher.getId();
        otherTeacherToken = token(user(UserRole.TEACHER)); adminToken = token(user(UserRole.ADMIN));
        when(catalog.configured()).thenReturn(List.of(runtime("python3"), runtime("java"), runtime("cpp17")));
        when(execution.submitBatch(any())).thenAnswer(call -> {
            var batch = (ExecutionBatch) call.getArgument(0);
            return CompletableFuture.completedFuture(SubmittedExecutionBatch.builder().runtimeKey(batch.getRuntimeKey()).runtimeVersion(batch.getRuntimeVersion())
                    .comparison(batch.getComparison()).limits(batch.getLimits()).deadline(batch.getDeadline()).tickets(batch.getTests().stream().map(t -> ExecutionTicket.builder()
                            .testCaseId(t.getTestCaseId()).position(t.getPosition()).hidden(t.isHidden()).expectedOutput(t.getExpectedOutput()).token(UUID.randomUUID().toString()).build()).toList()).build());
        });
        when(execution.pollBatch(any())).thenAnswer(call -> {
            var batch = (SubmittedExecutionBatch) call.getArgument(0);
            return CompletableFuture.completedFuture(new ExecutionResultAggregator().aggregate(batch.getTickets().stream().map(t -> CaseExecutionResult.builder()
                    .testCaseId(t.getTestCaseId()).position(t.getPosition()).hidden(t.isHidden()).status(ExecutionStatus.FINISHED).verdict(verdict.get())
                    .diagnostics(ExecutionDiagnostics.builder().stdout(HIDDEN).stderr(REFERENCE).build()).build()).toList()));
        });
        var body = new HashMap<String,Object>();
        body.put("lockVersion", 0); body.put("title", "Native sum"); body.put("topic", "Arithmetic"); body.put("difficulty", "EASY"); body.put("statement", "Add two numbers.");
        body.put("inputFormat", "Numbers"); body.put("outputFormat", "Sum"); body.put("constraintsText", ""); body.put("comparisonMode", "EXACT"); body.put("visibility", "AUTHENTICATED");
        body.put("languages", List.of("python3", "java", "cpp17").stream().map(k -> Map.of("languageKey", k, "starterCode", "", "timeLimitMs", 2000,
                "wallTimeLimitMs", 5000, "memoryLimitKb", 262144, "outputLimitBytes", 65536)).toList());
        body.put("tests", List.of(Map.of("position", 0, "inputData", "1 2", "expectedOutput", "3", "hidden", false),
                Map.of("position", 1, "inputData", HIDDEN, "expectedOutput", HIDDEN, "hidden", true)));
        var draft = tree(api(post("/api/teacher/problems"), teacherToken, body).andExpect(status().isCreated()).andReturn());
        problem = draft.get("id").asLong(); version = draft.get("version").get("id").asLong();
        var proof = tree(api(post("/api/teacher/problems/{id}/verify", problem), teacherToken, Map.of("lockVersion", 0,
                "solutions", List.of("python3", "java", "cpp17").stream().map(k -> Map.of("languageKey", k, "sourceCode", REFERENCE)).toList()))
                .andExpect(status().isAccepted()).andReturn()).get("id").asText();
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> api(get("/api/teacher/problems/{id}/verifications/{v}", problem, proof), teacherToken, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.verdict").value("ACCEPTED")));
        api(post("/api/teacher/problems/{id}/publish", problem), teacherToken, Map.of("lockVersion", 0, "verificationId", proof)).andExpect(status().isOk());
    }
    @AfterEach void close() {
        clients.forEach(Client::close); clients.clear();
        if (mlServer != null) mlServer.stop(0);
        policy.setMode(PolicyMode.RULE); policy.getMl().setEnabled(false);
    }

    @Test void jwtIdentityOverridesSpoofedPayloadAndUnknownExecutionRemainsUnknown() throws Exception {
        var client = client(token); client.subscribe();
        var body = snapshot(UUID.randomUUID(), "python3", 1, "print(3)"); body.put("studentId", other.getId()); body.put("attemptNo", 999);
        body.put("analyzerResult", Map.of("compileSuccess", true, "testsPassed", 1000));
        client.send(body); var feedback = client.feedback();
        assertThat(feedback.get("executionKnown").asBoolean()).isFalse(); assertThat(feedback.get("compileSuccess").isNull()).isTrue();
        assertThat(feedback.get("testsPassed").isNull()).isTrue(); assertThat(feedback.get("policyMode").asText()).isEqualTo("RULE");
        var log = logs.findById(UUID.fromString(feedback.get("interactionId").asText())).orElseThrow();
        assertThat(log.getStudentId()).isEqualTo(student.getId()); assertThat(log.getAttemptNo()).isZero();
        assertThat(log.getProblemId()).isEqualTo(problem); assertThat(log.getProblemVersionId()).isEqualTo(version); assertThat(log.getCodeRevision()).isEqualTo(1);
        assertThat(log.getTestsPassed()).isNull(); assertThat(log.getCompileSuccess()).isNull(); assertThat(log.getEvidenceOrigin().name()).isEqualTo("STATIC_HEURISTIC");
        assertThat(progress.findByStudentIdOrderByLastActivityAtDesc(student.getId())).isEmpty();
        assertThat(sessions.findById(log.getSessionId()).orElseThrow().getTotalAttempts()).isZero();
    }

    @ParameterizedTest @ValueSource(strings = {"/user/someone-else/queue/feedback", "/queue/feedback-user-victim", "/topic/feedback", "/app/code.update"})
    void foreignAndBrokerSubscriptionsAreDenied(String destination) throws Exception {
        var client = client(token); client.session.subscribe(destination, client.handler());
        assertThat(client.errors.poll(3, TimeUnit.SECONDS)).contains("REQUEST_REJECTED").doesNotContain(token, HIDDEN);
    }
    @Test void anonymousAndExpiredConnectionsCannotAuthenticate() throws Exception {
        var anonymous = new Client(); clients.add(anonymous);
        assertThatThrownBy(() -> anonymous.connect(null)).isInstanceOf(Exception.class);
        long ttl = (long) ReflectionTestUtils.getField(jwt, "jwtExpirationMs");
        String expired;
        try { ReflectionTestUtils.setField(jwt, "jwtExpirationMs", -1000L); expired = token(student); }
        finally { ReflectionTestUtils.setField(jwt, "jwtExpirationMs", ttl); }
        var rejected = new Client(); clients.add(rejected);
        assertThatThrownBy(() -> rejected.connect(expired)).isInstanceOf(Exception.class);
    }
    @Test void responsesAreScopedToTheSendingSessionAndEditorsDoNotInterfere() throws Exception {
        var first = client(token); var second = client(token); var stranger = client(otherToken); first.subscribe(); second.subscribe(); stranger.subscribe();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        first.send(snapshot(a, "python3", 1, "print(3)")); second.send(snapshot(b, "java", 1, "student-java-buffer"));
        assertThat(first.feedback().get("editorSessionId").asText()).isEqualTo(a.toString());
        assertThat(second.feedback().get("editorSessionId").asText()).isEqualTo(b.toString());
        assertThat(first.messages.poll(250, TimeUnit.MILLISECONDS)).isNull(); assertThat(second.messages.poll(250, TimeUnit.MILLISECONDS)).isNull();
        assertThat(stranger.messages.poll(250, TimeUnit.MILLISECONDS)).isNull();
    }
    @Test void rapidSnapshotsAreDebouncedAndOldRevisionsAreIgnored() throws Exception {
        var client = client(token); client.subscribe(); UUID editor = UUID.randomUUID();
        IntStream.rangeClosed(1, 8).forEach(i -> { try { client.send(snapshot(editor, "python3", i, "print("+i+")")); } catch (Exception ex) { throw new AssertionError(ex); } });
        var feedback = client.feedback(); assertThat(feedback.get("codeRevision").asInt()).isEqualTo(8);
        client.send(snapshot(editor, "python3", 7, "older-buffer"));
        assertThat(client.messages.poll(350, TimeUnit.MILLISECONDS)).isNull();
        assertThat(logs.findByStudentIdOrderByCreatedAtDesc(student.getId())).hasSize(1);
    }
    @ParameterizedTest @ValueSource(strings = {"java", "cpp17"})
    void nonPythonCompilationEvidenceUsesRulesAndNeverCallsMlOrLlm(String language) throws Exception {
        localMl(200, "{\"action\":\"NO_FEEDBACK\",\"mentor_state\":\"accepted\",\"confidence\":0.9}");
        verdict.set(ExecutionVerdict.COMPILATION_ERROR); String source = "student-"+language+"-buffer"; sample(language, source);
        var client = client(token); client.subscribe(); client.send(snapshot(UUID.randomUUID(), language, 1, source)); var feedback = client.feedback();
        assertThat(feedback.get("executionKnown").asBoolean()).isTrue(); assertThat(feedback.get("compileSuccess").asBoolean()).isFalse();
        assertThat(feedback.get("verdict").asText()).isEqualTo("COMPILATION_ERROR"); assertThat(feedback.get("policyMode").asText()).isEqualTo("RULE");
        assertThat(feedback.get("fallbackReason").asText()).isEqualTo("UNSUPPORTED_LANGUAGE"); assertThat(predictions.get()).isZero();
        verify(gemini, never()).generateWithGemini(any(), any(), any(), any()); verify(openai, never()).generateWithLlm(any(), any(), any());
    }
    @Test void evidenceFromAnotherHashOrStudentCannotBecomeCurrentCompilerEvidence() throws Exception {
        sample("python3", "print(3)"); var client = client(token); client.subscribe();
        client.send(snapshot(UUID.randomUUID(), "python3", 1, "print(4)")); assertThat(client.feedback().get("executionKnown").asBoolean()).isFalse();
        var stranger = client(otherToken); stranger.subscribe(); stranger.send(snapshot(UUID.randomUUID(), "python3", 1, "print(3)"));
        assertThat(stranger.feedback().get("executionKnown").asBoolean()).isFalse();
    }
    @Test void pythonInferenceReceivesOnlyCurrentPublicEvidenceAndLlmFailureUsesTemplates() throws Exception {
        sample("python3", "print(3)"); localMl(200, "{\"action\":\"CONCEPTUAL_HINT\",\"mentor_state\":\"semantic_debug\",\"confidence\":0.9}");
        when(gemini.isEnabled()).thenReturn(true); when(openai.isEnabled()).thenReturn(true);
        doThrow(new IllegalStateException(HIDDEN+REFERENCE)).when(gemini).generateWithGemini(any(), any(), any(), any());
        doThrow(new IllegalStateException(HIDDEN+REFERENCE)).when(openai).generateWithLlm(any(), any(), any());
        var client = client(token); client.subscribe(); client.send(snapshot(UUID.randomUUID(), "python3", 1, "print(3)")); var feedback = client.feedback();
        assertThat(feedback.get("policyMode").asText()).isEqualTo("ML"); assertThat(feedback.get("feedbackSource").asText()).isEqualTo("template");
        assertThat(predictions.get()).isEqualTo(1); assertThat(predictionBody.get()).contains("\"language_key\":\"python3\"", "\"tests_passed\":1")
                .doesNotContain(HIDDEN, REFERENCE, "stdout", "stderr", "expected_output");
        assertThat(feedback.toString()).doesNotContain(HIDDEN, REFERENCE, "stdout", "stderr");
    }
    @Test void mlOutageAndLowConfidenceFallBackToRuleWithoutSendingUnknownMetricsAsObservedZeros() throws Exception {
        localMl(503, HIDDEN+REFERENCE);
        var client = client(token); client.subscribe(); client.send(snapshot(UUID.randomUUID(), "python3", 1, "print(3)")); var feedback = client.feedback();
        assertThat(feedback.get("policyMode").asText()).isEqualTo("RULE"); assertThat(feedback.get("fallbackReason").asText()).isEqualTo("ML_UNAVAILABLE");
        assertThat(predictionBody.get()).contains("\"compile_success\":null", "\"tests_passed\":null");
        assertThat(feedback.toString()).doesNotContain(HIDDEN, REFERENCE);
    }
    @Test void invalidBuffersAndArbitraryBrokerSendAreRejectedWithoutSourceInErrors() throws Exception {
        var client = client(token); client.subscribe(); client.send(snapshot(UUID.randomUUID(), "python3", 1, "private-source-"+"x".repeat(20001)));
        assertThat(client.feedback().toString()).contains("INVALID_REQUEST").doesNotContain("private-source-");
        client.sendDestination("/queue/feedback", "private-source".getBytes(StandardCharsets.UTF_8));
        assertThat(client.errors.poll(3, TimeUnit.SECONDS)).contains("REQUEST_REJECTED").doesNotContain("private-source");
    }
    @Test void nativeHttpFallbackAndResearchExportsPreserveContractsWithOwnershipChecks() throws Exception {
        var result = api(post("/api/mentor/native-feedback"), token, snapshot(UUID.randomUUID(), "python3", 1, "print(3)"))
                .andExpect(request().asyncStarted()).andReturn();
        var feedback = tree(mvc.perform(asyncDispatch(result)).andExpect(status().isOk()).andReturn()); String id = feedback.get("interactionId").asText();
        api(post("/api/interactions/{id}/result", id), otherToken, Map.of("resolvedAfterFeedback", true)).andExpect(status().isForbidden());
        api(post("/api/interactions/{id}/result", id), token, Map.of("resolvedAfterFeedback", true)).andExpect(status().isOk());
        api(get("/api/interactions/policy-dataset"), token, null).andExpect(status().isForbidden());
        var csv = api(get("/api/interactions/policy-dataset"), adminToken, null).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(csv).startsWith("interaction_id,student_id,task_id,policy_version,created_at,error_type,severity,compile_success,tests_passed,tests_failed,");
        assertThat(csv).contains("evidence_origin", "STATIC_HEURISTIC", "execution_known", "python3");
        api(post("/api/mentor/feedback"), token, Map.of()).andExpect(status().isForbidden());
        api(post("/api/interactions/{id}/expert-labels", id), otherTeacherToken,
                Map.of("reviewerId", other.getId(), "targetFeedbackAction", "CONCEPTUAL_HINT", "confidence", 3)).andExpect(status().isForbidden());
        api(post("/api/interactions/{id}/expert-labels", id), teacherToken,
                Map.of("reviewerId", teacherId, "targetFeedbackAction", "CONCEPTUAL_HINT", "confidence", 3)).andExpect(status().isOk());
        var labels = api(get("/api/interactions/expert-labels/export"), adminToken, null).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(labels).startsWith("label_id,interaction_id,reviewer_id,target_feedback_action,confidence,rationale,created_at,error_type,feedback_action,");
        assertThat(labels).contains("evidence_origin", "STATIC_HEURISTIC");
    }

    @Test void fullBuffersWithinTheContractAreAcceptedAndExpiredSampleCleanupPreservesResearchProvenance() throws Exception {
        String source = "#".repeat(20000); sample("python3", source);
        var client = client(token); client.subscribe(); client.send(snapshot(UUID.randomUUID(), "python3", 1, source)); var feedback = client.feedback();
        assertThat(feedback.get("executionKnown").asBoolean()).isTrue();
        UUID interaction = UUID.fromString(feedback.get("interactionId").asText());
        var log = logs.findById(interaction).orElseThrow(); UUID job = log.getExecutionJobId();
        jdbc.update("UPDATE execution_runs SET created_at=NOW()-INTERVAL '1 hour', expires_at=NOW()-INTERVAL '1 minute' WHERE id=?", jobs.findById(job).orElseThrow().getRunId());
        cleanup.removeExpired();
        var retained = logs.findById(interaction).orElseThrow();
        assertThat(retained.getExecutionJobId()).isNull(); assertThat(retained.getEvidenceOrigin().name()).isEqualTo("SAMPLE_RUN");
        assertThat(retained.getTestsPassed()).isEqualTo(1);
    }

    private void localMl(int status, String body) throws Exception {
        predictions.set(0); mlServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        mlServer.createContext("/predict-mentor-state", exchange -> { predictions.incrementAndGet(); predictionBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8); exchange.sendResponseHeaders(status, bytes.length); exchange.getResponseBody().write(bytes); exchange.close(); });
        mlServer.start(); policy.setMode(PolicyMode.ML); policy.getMl().setEnabled(true); policy.getMl().setBaseUrl("http://127.0.0.1:"+mlServer.getAddress().getPort());
        policy.getMl().setPredictPath("/predict-mentor-state"); policy.getMl().setMinConfidence(.35);
    }
    private void sample(String language, String code) throws Exception {
        var run = UUID.fromString(tree(api(post("/api/problems/{id}/runs", problem), token,
                Map.of("problemVersionId", version, "languageKey", language, "sourceCode", code, "idempotencyKey", UUID.randomUUID())).andExpect(status().isAccepted()).andReturn()).get("id").asText());
        worker.start(judging.claim("mentor-fixture", 1).getFirst());
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(jobs.findByRunId(run).orElseThrow().getStatus()).isEqualTo(ExecutionStatus.FINISHED));
    }
    private Map<String,Object> snapshot(UUID editor, String language, long revision, String code) {
        return new HashMap<>(Map.of("problemId", problem, "problemVersionId", version, "languageKey", language, "editorSessionId", editor, "codeRevision", revision, "code", code));
    }
    private ExecutionRuntimeCatalogPort.RuntimeDescriptor runtime(String language) { return new ExecutionRuntimeCatalogPort.RuntimeDescriptor(language, "fixture-"+language, "fixture-v1", null); }
    private User user(UserRole role) { return users.saveAndFlush(User.builder().email(UUID.randomUUID()+"@example.test").passwordHash("unused-fixture-hash").fullName("Synthetic user")
            .role(role).settings(UserSettings.builder().build()).createdAt(LocalDateTime.now()).build()); }
    private String token(User u) { return jwt.generateToken(new SecurityUserDetails(u)); }
    private ResultActions api(MockHttpServletRequestBuilder r, String jwt, Object body) throws Exception {
        if (jwt != null) r.header("Authorization", "Bearer "+jwt); if (body != null) r.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body)); return mvc.perform(r);
    }
    private JsonNode tree(MvcResult result) throws Exception { return json.readTree(result.getResponse().getContentAsString()); }
    private Client client(String jwt) throws Exception { var c = new Client(); clients.add(c); c.connect(jwt); return c; }
    private class Client extends StompSessionHandlerAdapter {
        final BlockingQueue<String> messages = new LinkedBlockingQueue<>(), errors = new LinkedBlockingQueue<>();
        final WebSocketStompClient transport = new WebSocketStompClient(new SockJsClient(List.of(new WebSocketTransport(new StandardWebSocketClient()))));
        StompSession session;
        Client() { transport.setMessageConverter(new ByteArrayMessageConverter() {
            @Override protected boolean supportsMimeType(org.springframework.messaging.MessageHeaders headers) { return true; }
        }); transport.setDefaultHeartbeat(new long[]{0,0}); }
        void connect(String jwt) throws Exception { var headers = new StompHeaders(); if (jwt != null) headers.set("Authorization", "Bearer "+jwt);
            session = transport.connectAsync("http://localhost:"+port+"/ws", new WebSocketHttpHeaders(), headers, this).get(4, TimeUnit.SECONDS); }
        void subscribe() { session.subscribe("/user/queue/feedback", handler()); }
        StompFrameHandler handler() { return new StompFrameHandler() {
            public Type getPayloadType(StompHeaders h) { return byte[].class; }
            public void handleFrame(StompHeaders h, Object p) { messages.add(new String((byte[])p, StandardCharsets.UTF_8)); }
        }; }
        void send(Map<String,Object> body) throws Exception { sendDestination("/app/code.update", json.writeValueAsBytes(body)); }
        void sendDestination(String destination, byte[] body) { var headers = new StompHeaders(); headers.setDestination(destination); headers.setContentType(MediaType.APPLICATION_JSON); session.send(headers, body); }
        JsonNode feedback() throws Exception { String text = messages.poll(5, TimeUnit.SECONDS); assertThat(text).as("owner feedback frame").isNotNull(); return json.readTree(text); }
        @Override public Type getPayloadType(StompHeaders h) { return byte[].class; }
        @Override public void handleFrame(StompHeaders h, Object p) { errors.add(new String((byte[])p, StandardCharsets.UTF_8)); }
        @Override public void handleTransportError(StompSession s, Throwable error) { errors.offer("TRANSPORT_CLOSED"); }
        void close() { try { if (session != null && session.isConnected()) session.disconnect(); } catch (RuntimeException alreadyClosed) { } finally { transport.stop(); } }
    }
}
