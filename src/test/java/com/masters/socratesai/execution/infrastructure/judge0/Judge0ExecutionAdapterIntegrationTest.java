package com.masters.socratesai.execution.infrastructure.judge0;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.masters.socratesai.execution.application.StudentExecutionResultMapper;
import com.masters.socratesai.execution.domain.*;
import com.masters.socratesai.execution.domain.port.CodeExecutionPort;
import com.masters.socratesai.execution.model.ExecutionStatus;
import com.masters.socratesai.execution.model.ExecutionVerdict;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static com.masters.socratesai.execution.infrastructure.judge0.SandboxFixtures.*;
import static org.assertj.core.api.Assertions.*;

/** Real HTTP framing/authentication/timeout tests. This server never compiles or executes source. */
class Judge0ExecutionAdapterIntegrationTest {
    private HttpServer server;
    private ExecutorService serverExecutor;
    private ExecutorService clientExecutor;
    private HttpClient transport;
    private SandboxAsyncRuntime runtime;
    private CodeExecutionPort adapter;
    private Judge0Properties properties;
    private final ObjectMapper json = new ObjectMapper();
    private final ConcurrentLinkedQueue<Request> requests = new ConcurrentLinkedQueue<>();
    private final CountDownLatch releaseStream = new CountDownLatch(1);
    private volatile Function<Request, Response> script = request -> new Response(200, "{}");
    private volatile String catalog = "[{\"id\":71,\"name\":\"Python (3.fixture)\"}]";

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serverExecutor = Executors.newCachedThreadPool(Thread.ofPlatform().daemon().factory());
        server.setExecutor(serverExecutor);
        server.createContext("/", this::handle);
        server.start();
        properties = properties();
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/api/");
        properties.setInitialBackoff(Duration.ofMillis(10));
        properties.setMaxBackoff(Duration.ofMillis(40));
        properties.setRequestTimeout(Duration.ofSeconds(1));
        properties.setOperationTimeout(Duration.ofSeconds(5));
        var configuration = new Judge0Configuration();
        clientExecutor = configuration.judge0HttpExecutor();
        transport = configuration.judge0HttpTransport(properties, clientExecutor);
        runtime = configuration.judge0AsyncRuntime(properties, Clock.systemUTC());
        adapter = configuration.codeExecutionPort(properties,
                configuration.judge0HttpClient(properties, transport, Clock.systemUTC()), runtime, Clock.systemUTC());
    }

    @AfterEach
    void tearDown() {
        releaseStream.countDown();
        runtime.close();
        transport.shutdownNow();
        clientExecutor.shutdownNow();
        server.stop(0);
        serverExecutor.shutdownNow();
    }

    @Test
    void submitsSafePayloadAndPollsOnlyUnfinishedTokensInTrustedTestOrder() throws Exception {
        var polls = new AtomicInteger();
        script = request -> {
            if (request.method().equals("POST")) return new Response(201, "[{\"token\":\"token-a\"},{\"token\":\"token-b\"}]");
            if (polls.getAndIncrement() == 0) return batchResponse(result("token-b", 2, "ignored"), result("token-a", 3, "42\n"));
            return batchResponse(result("token-b", 3, "hidden-input"));
        };
        var submitted = adapter.submitBatch(batch(deadline(), List.of(test(2, 1, true, "hidden-input"), test(1, 0, false, "42\n"))))
                .get(3, TimeUnit.SECONDS);
        assertThat(submitted.getDeadline()).isBeforeOrEqualTo(Instant.now().plusSeconds(5));
        assertThat(submitted.getTickets()).extracting(ExecutionTicket::getToken).containsExactly("token-a", "token-b");
        var finished = adapter.pollBatch(submitted).get(3, TimeUnit.SECONDS);
        assertThat(finished.getStatus()).isEqualTo(ExecutionStatus.FINISHED);
        assertThat(finished.getVerdict()).isEqualTo(ExecutionVerdict.ACCEPTED);
        assertThat(finished.getCases()).extracting(CaseExecutionResult::getTestCaseId).containsExactly(1L, 2L);
        assertThat(finished.getCases().get(1).getDiagnostics()).isNull();
        assertThat(json.writeValueAsString(new StudentExecutionResultMapper().toResponse(finished)))
                .doesNotContain("hidden-input", "token-a", "token-b", SECRET);
        assertThat(requests).allMatch(request -> SECRET.equals(request.auth()));
        var creation = requests.stream().filter(request -> request.method().equals("POST")).findFirst().orElseThrow();
        assertThat(creation.uri()).isEqualTo("/api/submissions/batch?base64_encoded=true");
        JsonNode payload = json.readTree(creation.body());
        assertThat(payload.get("submissions")).hasSize(2);
        JsonNode sample = payload.get("submissions").get(0);
        assertThat(sample.get("source_code").asText()).isEqualTo(encoded(SECRET));
        assertThat(sample.get("stdin").asText()).isEqualTo(encoded("sample-input"));
        assertThat(sample.get("language_id").asInt()).isEqualTo(71);
        assertThat(sample.get("cpu_time_limit").decimalValue()).isEqualByComparingTo("1.250");
        assertThat(sample.get("wall_time_limit").decimalValue()).isEqualByComparingTo("3.000");
        assertThat(sample.get("memory_limit").asInt()).isEqualTo(65536);
        assertThat(sample.get("max_file_size").asInt()).isEqualTo(4);
        assertThat(sample.get("max_processes_and_or_threads").asInt()).isEqualTo(64);
        assertThat(sample.get("enable_network").asBoolean()).isFalse();
        assertThat(sample.get("enable_per_process_and_thread_memory_limit").asBoolean()).isFalse();
        assertThat(sample.get("enable_per_process_and_thread_time_limit").asBoolean()).isFalse();
        assertThat(sample.get("redirect_stderr_to_stdout").asBoolean()).isFalse();
        assertThat(creation.body()).doesNotContain("expected_output", "callback_url", "compiler_options", "additional_files", SECRET);
        assertThat(requests.stream().filter(r -> r.method().equals("GET") && r.uri().contains("submissions")).toList())
                .extracting(Request::uri).satisfiesExactly(
                        uri -> assertThat(uri).contains("tokens=token-a,token-b"),
                        uri -> assertThat(uri).contains("tokens=token-b&fields=").doesNotContain("token-a"));
    }

    @ParameterizedTest
    @ValueSource(ints = {429, 503})
    void retriesTransientPollFailuresWithoutResubmittingCode(int status) throws Exception {
        var calls = new AtomicInteger();
        script = request -> calls.incrementAndGet() < 3 ? new Response(status, SECRET) : batchResponse(result("token-a", 3, "42\n"));
        var result = adapter.pollBatch(submitted(deadline(), ticket("token-a", 1, false))).get(3, TimeUnit.SECONDS);
        assertThat(result.getVerdict()).isEqualTo(ExecutionVerdict.ACCEPTED);
        assertThat(calls).hasValue(3);
        assertThat(requests).noneMatch(r -> r.method().equals("POST"));
    }

    @ParameterizedTest
    @CsvSource({"401,false,PROVIDER_REJECTED", "502,true,SUBMISSION_UNKNOWN", "408,true,SUBMISSION_UNKNOWN"})
    void neverRetriesCreationAndMarksAmbiguousOutcomes(int status, boolean unknown, SandboxFailureCode failure) {
        script = request -> new Response(status, SECRET);
        var exception = failure(adapter.submitBatch(batch(deadline(), List.of(test(1, 0, false, "42\n")))));
        assertThat(exception.getCode()).isEqualTo(failure);
        assertThat(exception.isMayHaveBeenSubmitted()).isEqualTo(unknown);
        assertThat(requests.stream().filter(r -> r.method().equals("POST"))).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"[]", "[{\"token\":\"a\"},{\"error\":\"private-source-and-auth-fixture\"}]",
            "[{\"token\":\"a\"},{\"token\":\"a\"}]", "{private-source-and-auth-fixture", "[{\"token\":123}]"})
    void partialOrMalformedCreationDoesNotTriggerDuplicateExecution(String body) {
        script = request -> new Response(201, body);
        var exception = failure(adapter.submitBatch(batch(deadline(), List.of(test(1, 0, false, ""), test(2, 1, true, "")))));
        assertThat(exception.getCode()).isEqualTo(SandboxFailureCode.SUBMISSION_UNKNOWN);
        assertThat(exception.isMayHaveBeenSubmitted()).isTrue();
        assertThat(exception).hasNoCause().hasMessageNotContaining(SECRET);
        assertThat(requests.stream().filter(r -> r.method().equals("POST"))).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{private-source-and-auth-fixture", "{\"submissions\":[]}",
            "{\"submissions\":[{\"token\":\"unknown\",\"status\":{\"id\":3}}]}",
            "{\"submissions\":[{\"token\":\"token-a\",\"status\":{\"id\":\"3\"}}]}"})
    void malformedOrUnmatchedPollResultsCannotBecomeAccepted(String body) throws Exception {
        script = request -> new Response(200, body);
        var result = adapter.pollBatch(submitted(deadline(), ticket("token-a", 1, true))).get(3, TimeUnit.SECONDS);
        assertThat(result.getStatus()).isEqualTo(ExecutionStatus.SYSTEM_ERROR);
        assertThat(result.getFailureCode()).isEqualTo(SandboxFailureCode.INVALID_RESPONSE);
        assertThat(result.getVerdict()).isNull();
        assertThat(result.toString()).doesNotContain(SECRET);
    }

    @Test
    void duplicateResultTokensAreRejected() throws Exception {
        script = request -> batchResponse(result("token-a", 3, "42\n"), result("token-a", 3, "42\n"));
        var result = adapter.pollBatch(submitted(deadline(), ticket("token-a", 1, false), ticket("token-b", 2, true)))
                .get(3, TimeUnit.SECONDS);
        assertThat(result.getFailureCode()).isEqualTo(SandboxFailureCode.INVALID_RESPONSE);
    }

    @Test
    void globalDeadlinePreservesFinishedCasesAndCancelsRemainingPolling() throws Exception {
        var calls = new AtomicInteger();
        script = request -> calls.getAndIncrement() == 0
                ? batchResponse(result("token-a", 3, "42\n"), result("token-b", 2, ""))
                : batchResponse(result("token-b", 2, ""));
        var result = adapter.pollBatch(submitted(Instant.now().plusMillis(400), ticket("token-a", 1, false), ticket("token-b", 2, true)))
                .get(2, TimeUnit.SECONDS);
        assertThat(result.getFailureCode()).isEqualTo(SandboxFailureCode.DEADLINE_EXCEEDED);
        assertThat(result.getVerdict()).isNull();
        assertThat(result.getCases().getFirst().getVerdict()).isEqualTo(ExecutionVerdict.ACCEPTED);
        assertThat(result.getCases().get(1).getStatus()).isEqualTo(ExecutionStatus.SYSTEM_ERROR);
        int requestsAtDeadline = requests.size();
        assertThat(requestsAtDeadline).isLessThan(20);
        // A second operation is accepted after timeout cleanup; there is no occupied polling slot.
        script = request -> batchResponse(result("token-c", 3, "42\n"));
        assertThat(adapter.pollBatch(submitted(deadline(), ticket("token-c", 1, false))).get(2, TimeUnit.SECONDS).getVerdict())
                .isEqualTo(ExecutionVerdict.ACCEPTED);
    }

    @Test
    void independentDeadlineStopsAResponseThatNeverFinishesItsBody() throws Exception {
        script = request -> new Response(200, "STREAM_HANG");
        var result = adapter.pollBatch(submitted(Instant.now().plusMillis(250), ticket("token-a", 1, true))).get(2, TimeUnit.SECONDS);
        assertThat(result.getFailureCode()).isEqualTo(SandboxFailureCode.DEADLINE_EXCEEDED);
    }

    @Test
    void deadlineCannotBecomeAcceptedDuringAggregationOfAlreadyFinishedCases() throws Exception {
        var enteredAggregation = new CountDownLatch(1);
        var releaseAggregation = new CountDownLatch(1);
        var aggregator = org.mockito.Mockito.mock(ExecutionResultAggregator.class);
        org.mockito.Mockito.when(aggregator.aggregate(org.mockito.ArgumentMatchers.anyList())).thenAnswer(invocation -> {
            enteredAggregation.countDown();
            releaseAggregation.await(3, TimeUnit.SECONDS);
            return new ExecutionResultAggregator().aggregate(invocation.getArgument(0));
        });
        var configuration = new Judge0Configuration();
        var raceAdapter = new Judge0ExecutionAdapter(properties,
                configuration.judge0HttpClient(properties, transport, Clock.systemUTC()),
                new Judge0RuntimeRegistry(properties, Clock.systemUTC()), new SandboxRequestValidator(properties),
                new Judge0ResultMapper(new OutputComparator()), aggregator, runtime,
                new BackoffPolicy(properties.getInitialBackoff(), properties.getMaxBackoff()));
        script = request -> batchResponse(result("token-a", 3, "42\n"));
        try {
            var pending = raceAdapter.pollBatch(submitted(Instant.now().plusSeconds(1), ticket("token-a", 1, false)));
            assertThat(enteredAggregation.await(2, TimeUnit.SECONDS)).isTrue();
            var result = pending.get(2, TimeUnit.SECONDS);
            assertThat(result.getFailureCode()).isEqualTo(SandboxFailureCode.DEADLINE_EXCEEDED);
            assertThat(result.getStatus()).isEqualTo(ExecutionStatus.SYSTEM_ERROR);
            assertThat(result.getVerdict()).isNull();
            assertThat(result.getCases().getFirst().getVerdict()).isEqualTo(ExecutionVerdict.ACCEPTED);
        } finally {
            releaseAggregation.countDown();
        }
    }

    @Test
    void creationDeadlineReportsPossibleRemoteWorkWithoutResubmitting() {
        script = request -> new Response(200, "STREAM_HANG");
        var exception = failure(adapter.submitBatch(batch(Instant.now().plusMillis(300), List.of(test(1, 0, false, "")))));
        assertThat(exception.getCode()).isEqualTo(SandboxFailureCode.DEADLINE_EXCEEDED);
        assertThat(exception.isMayHaveBeenSubmitted()).isTrue();
        assertThat(requests.stream().filter(r -> r.method().equals("POST"))).hasSize(1);
    }

    @Test
    void byteLimitRejectsChunkedBodyBeforeJsonParsingAndWithoutRetry() throws Exception {
        properties.setMaxResponseBytes(1024);
        script = request -> new Response(200, "CHUNKED_OVERSIZE");
        var result = adapter.pollBatch(submitted(deadline(), ticket("token-a", 1, false))).get(2, TimeUnit.SECONDS);
        assertThat(result.getFailureCode()).isEqualTo(SandboxFailureCode.RESPONSE_TOO_LARGE);
        assertThat(requests).hasSize(1);
    }

    @Test
    void cancellationPropagatesAndPollBudgetIsBounded() throws Exception {
        properties.setMaxPollAttempts(2);
        script = request -> batchResponse(result("token-a", 1, ""));
        var result = adapter.pollBatch(submitted(deadline(), ticket("token-a", 1, false))).get(2, TimeUnit.SECONDS);
        assertThat(result.getFailureCode()).isEqualTo(SandboxFailureCode.POLL_LIMIT_EXCEEDED);
        assertThat(requests).hasSize(2);
        script = request -> new Response(200, "STREAM_HANG");
        var cancelled = adapter.pollBatch(submitted(deadline(), ticket("token-b", 2, true)));
        assertThat(cancelled.cancel(true)).isTrue();
        assertThat(cancelled.isCancelled()).isTrue();
    }

    @Test
    void runtimeCatalogMismatchFailsBeforeAnySourceIsSent() {
        catalog = "[{\"id\":71,\"name\":\"Python (wrong version)\"}]";
        var exception = failure(adapter.submitBatch(batch(deadline(), List.of(test(1, 0, false, "")))));
        assertThat(exception.getCode()).isEqualTo(SandboxFailureCode.RUNTIME_UNAVAILABLE);
        assertThat(requests).noneMatch(r -> r.method().equals("POST"));
    }

    @Test
    void freshCatalogIsCachedAndPostDeadlineIsPreservedAcrossCalls() throws Exception {
        script = request -> new Response(201, "[{\"token\":\"token-a\"}]");
        var until = Instant.now().plusSeconds(3);
        var batch = batch(until, List.of(test(1, 0, false, "")));
        assertThat(adapter.submitBatch(batch).get(2, TimeUnit.SECONDS).getDeadline()).isEqualTo(until);
        adapter.submitBatch(batch).get(2, TimeUnit.SECONDS);
        assertThat(requests.stream().filter(r -> r.uri().endsWith("languages"))).hasSize(1);
    }

    @Test
    void redirectsAreNotFollowedWithCredentials() throws Exception {
        script = request -> new Response(302, SECRET);
        var result = adapter.pollBatch(submitted(deadline(), ticket("token-a", 1, false))).get(2, TimeUnit.SECONDS);
        assertThat(result.getFailureCode()).isEqualTo(SandboxFailureCode.PROVIDER_REJECTED);
        assertThat(requests).hasSize(1);
    }

    @Test
    void disabledAndInvalidRequestsMakeNoNetworkCalls() {
        properties.setEnabled(false);
        assertThat(failure(adapter.submitBatch(null)).getCode()).isEqualTo(SandboxFailureCode.DISABLED);
        assertThat(failure(adapter.pollBatch(null)).getCode()).isEqualTo(SandboxFailureCode.DISABLED);
        properties.setEnabled(true);
        assertThat(failure(adapter.submitBatch(null)).getCode()).isEqualTo(SandboxFailureCode.INVALID_REQUEST);
        assertThat(failure(adapter.pollBatch(submitted(deadline(), ticket("a&fields=stdin", 1, false)))).getCode())
                .isEqualTo(SandboxFailureCode.INVALID_REQUEST);
        assertThat(requests).isEmpty();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            var request = new Request(exchange.getRequestMethod(), exchange.getRequestURI().toString(),
                    exchange.getRequestHeaders().getFirst("X-Auth-Token"), new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            requests.add(request);
            Response response = request.uri().endsWith("/languages") ? new Response(200, catalog) : script.apply(request);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            if (response.status() == 302) exchange.getResponseHeaders().set("Location", "/redirect-target");
            if (response.body().equals("STREAM_HANG")) {
                exchange.sendResponseHeaders(200, 0);
                exchange.getResponseBody().write('{');
                exchange.getResponseBody().flush();
                try { releaseStream.await(3, TimeUnit.SECONDS); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            } else if (response.body().equals("CHUNKED_OVERSIZE")) {
                exchange.sendResponseHeaders(200, 0);
                exchange.getResponseBody().write("x".repeat(5000).getBytes(StandardCharsets.UTF_8));
            } else {
                byte[] body = response.body().getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(response.status(), body.length);
                exchange.getResponseBody().write(body);
            }
        }
    }

    private Response batchResponse(String... results) {
        return new Response(200, "{\"submissions\":[" + String.join(",", results) + "]}");
    }

    private String result(String token, int status, String output) {
        return "{\"token\":\"" + token + "\",\"status\":{\"id\":" + status + "},\"stdout\":\"" + encoded(output) + "\"}";
    }

    private Instant deadline() { return Instant.now().plusSeconds(10); }

    private SandboxException failure(CompletableFuture<?> future) {
        var failure = catchThrowable(() -> future.get(3, TimeUnit.SECONDS));
        assertThat(failure).isInstanceOf(ExecutionException.class).hasCauseInstanceOf(SandboxException.class);
        var cause = (SandboxException) failure.getCause();
        assertThat(cause).hasNoCause().hasMessageNotContaining(SECRET);
        return cause;
    }

    private record Request(String method, String uri, String auth, String body) {}
    private record Response(int status, String body) {}
}
