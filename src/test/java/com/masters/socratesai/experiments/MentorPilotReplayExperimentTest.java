package com.masters.socratesai.experiments;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.masters.socratesai.interaction.model.InteractionLog;
import com.masters.socratesai.interaction.repo.InteractionLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EnabledIfSystemProperty(named = "socratesai.experiments", matches = "true")
class MentorPilotReplayExperimentTest {

    private static final int STUDENT_COUNT = 30;
    private static final int STRESS_REQUESTS = 400;
    private static final int STRESS_CONCURRENCY = 8;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InteractionLogRepository interactionLogRepository;

    @Test
    void shouldReplayPilotTraceAndWriteResults() throws Exception {
        String token = registerStudent();
        List<Scenario> scenarios = scenarios();
        List<EventRow> rows = new ArrayList<>();

        long replayStarted = System.nanoTime();
        int eventIndex = 1;
        for (long studentId = 1; studentId <= STUDENT_COUNT; studentId++) {
            int attemptNo = 1;
            for (Scenario scenario : scenarios) {
                long wallStart = System.nanoTime();
                MvcResult result = mockMvc.perform(post("/api/mentor/analyze-feedback")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(mentorRequest(studentId, scenario.taskId(), attemptNo, scenario.code())))
                        .andExpect(status().isOk())
                        .andReturn();
                long wallLatencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - wallStart);

                JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
                rows.add(new EventRow(
                        eventIndex++,
                        studentId,
                        scenario.taskId(),
                        scenario.name(),
                        body.path("action").asText(),
                        body.path("errorType").asText(),
                        body.path("compileSuccess").asBoolean(),
                        body.path("testsPassed").asInt(),
                        body.path("testsFailed").asInt(),
                        body.path("analysisTimeMs").asInt(),
                        wallLatencyMs,
                        body.path("suspiciousRegion").asText("")
                ));
                attemptNo++;
            }
        }
        long replayWallMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - replayStarted);

        StressStats stressStats = runConcurrentStress(token);
        List<InteractionLog> replayLogs = interactionLogRepository.findAllByOrderByCreatedAtAsc().stream()
                .limit(rows.size())
                .toList();

        Path resultDir = Path.of("experiments", "results", "mentor-pilot-replay");
        Files.createDirectories(resultDir);
        Files.writeString(resultDir.resolve("events.csv"), toCsv(rows), StandardCharsets.UTF_8);
        Files.writeString(
                resultDir.resolve("summary.md"),
                toMarkdown(rows, replayLogs, replayWallMs, stressStats),
                StandardCharsets.UTF_8
        );

        assertThat(rows).hasSize(STUDENT_COUNT * scenarios.size());
        assertThat(stressStats.errors()).isZero();
    }

    private String registerStudent() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "experiment-student@socratesai.test",
                                  "password": "password123",
                                  "fullName": "Experiment Student",
                                  "role": "STUDENT"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).path("token").asText();
    }

    private String mentorRequest(long studentId, long taskId, int attemptNo, String code) throws Exception {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("studentId", studentId);
        request.put("taskId", taskId);
        request.put("language", "java");
        request.put("code", code);
        request.put("attemptNo", attemptNo);
        return objectMapper.writeValueAsString(request);
    }

    private StressStats runConcurrentStress(String token) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(STRESS_CONCURRENCY);
        ConcurrentLinkedQueue<Long> latencies = new ConcurrentLinkedQueue<>();
        AtomicInteger errors = new AtomicInteger();
        List<Callable<Void>> tasks = new ArrayList<>();

        for (int i = 0; i < STRESS_REQUESTS; i++) {
            int requestNo = i;
            tasks.add(() -> {
                try {
                    Scenario scenario = scenarios().get(requestNo % scenarios().size());
                    long start = System.nanoTime();
                    mockMvc.perform(post("/api/mentor/analyze-feedback")
                                    .header("Authorization", "Bearer " + token)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(mentorRequest(1000L + requestNo, 2000L + (requestNo % 20), 1, scenario.code())))
                            .andExpect(status().isOk());
                    latencies.add(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
                } catch (Exception ex) {
                    errors.incrementAndGet();
                }
                return null;
            });
        }

        long started = System.nanoTime();
        executor.invokeAll(tasks);
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.MINUTES);
        long wallMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

        List<Long> sorted = latencies.stream().sorted().toList();
        return new StressStats(
                STRESS_REQUESTS,
                STRESS_CONCURRENCY,
                errors.get(),
                wallMs,
                mean(sorted),
                percentile(sorted, 95),
                percentile(sorted, 99)
        );
    }

    private List<Scenario> scenarios() {
        return List.of(
                new Scenario("syntax_missing_semicolon_a", 101, "int x = 1"),
                new Scenario("syntax_missing_semicolon_b", 101, "System.out.println(x)"),
                new Scenario("syntax_missing_semicolon_c", 101, "int total = 0"),
                new Scenario("off_by_one_first", 102, "for (int i = 0; i <= n; i++) { sum += i; }"),
                new Scenario("off_by_one_repeat_a", 102, "for (int i = 0; i <= values.length; i++) { sum += values[i]; }"),
                new Scenario("off_by_one_repeat_b", 102, "for (int i = 1; i <= n; i++) { result += i; }"),
                new Scenario("wrong_condition_first", 103, "while (true) { work(); }"),
                new Scenario("wrong_condition_repeat", 103, "while (true) { if (done) break; }"),
                new Scenario("unfinished_todo_first", 104, "// TODO\nint value = 0;"),
                new Scenario("unfinished_todo_repeat_a", 104, "int value = 0; // TODO implement loop"),
                new Scenario("unfinished_todo_repeat_b", 104, "throw new UnsupportedOperationException();"),
                new Scenario("complete_return_first", 105, "int value = 1; return value;"),
                new Scenario("unknown_partial_logic", 106, "int value = 1; value++;"),
                new Scenario("complete_return_second", 105, "int total = 0; return total;"),
                new Scenario("syntax_after_progress", 101, "int z = 3"),
                new Scenario("off_by_one_after_progress", 102, "for (int i = 0; i <= items.length; i++) { total += i; }"),
                new Scenario("unfinished_todo_after_progress", 104, "// TODO retry\nint count = 0;"),
                new Scenario("wrong_condition_after_progress", 103, "while (true) { count++; }"),
                new Scenario("complete_return_final", 105, "int answer = 42; return answer;")
        );
    }

    private String toCsv(List<EventRow> rows) {
        StringBuilder csv = new StringBuilder();
        csv.append("event_index,student_id,task_id,scenario,action,error_type,compile_success,tests_passed,tests_failed,analysis_time_ms,wall_latency_ms,suspicious_region\n");
        for (EventRow row : rows) {
            csv.append(row.eventIndex()).append(',')
                    .append(row.studentId()).append(',')
                    .append(row.taskId()).append(',')
                    .append(escape(row.scenario())).append(',')
                    .append(row.action()).append(',')
                    .append(row.errorType()).append(',')
                    .append(row.compileSuccess()).append(',')
                    .append(row.testsPassed()).append(',')
                    .append(row.testsFailed()).append(',')
                    .append(row.analysisTimeMs()).append(',')
                    .append(row.wallLatencyMs()).append(',')
                    .append(escape(row.suspiciousRegion()))
                    .append('\n');
        }
        return csv.toString();
    }

    private String toMarkdown(List<EventRow> rows, List<InteractionLog> logs, long replayWallMs, StressStats stressStats) {
        List<Long> wallLatencies = rows.stream().map(EventRow::wallLatencyMs).sorted().toList();
        List<Integer> analysisLatencies = logs.stream().map(InteractionLog::getAnalysisTimeMs).sorted().toList();
        List<Integer> policyLatencies = logs.stream().map(InteractionLog::getPolicyTimeMs).sorted().toList();
        List<Integer> feedbackLatencies = logs.stream().map(InteractionLog::getFeedbackTimeMs).sorted().toList();
        List<Integer> totalLatencies = logs.stream().map(InteractionLog::getTotalLatencyMs).sorted().toList();
        Map<String, Long> actionCounts = rows.stream()
                .collect(Collectors.groupingBy(EventRow::action, Collectors.counting()));
        Map<String, Long> errorCounts = rows.stream()
                .collect(Collectors.groupingBy(EventRow::errorType, Collectors.counting()));

        StringBuilder md = new StringBuilder();
        md.append("# Mentor Pilot Replay Results\n\n");
        md.append("- Generated at: ").append(OffsetDateTime.now()).append('\n');
        md.append("- Environment: Spring Boot test profile, H2 in-memory database, template feedback, rule policy, MockMvc request pipeline.\n");
        md.append("- Replay events: ").append(rows.size()).append('\n');
        md.append("- Replay wall time: ").append(replayWallMs).append(" ms\n");
        md.append("- Replay throughput: ").append(format(rows.size() / (replayWallMs / 1000.0))).append(" events/s\n\n");

        md.append("## Latency\n\n");
        md.append("| Metric | Mean | P95 | P99 |\n");
        md.append("|---|---:|---:|---:|\n");
        md.append(metricRow("HTTP-style wall latency, ms", wallLatencies));
        md.append(metricRow("Logged total service latency, ms", totalLatencies));
        md.append(metricRow("Analyzer latency, ms", analysisLatencies));
        md.append(metricRow("Policy latency, ms", policyLatencies));
        md.append(metricRow("Feedback latency, ms", feedbackLatencies));

        md.append("\n## Action Distribution\n\n");
        md.append("| Action | Events | Share |\n");
        md.append("|---|---:|---:|\n");
        appendDistribution(md, actionCounts, rows.size());

        md.append("\n## Error-Type Distribution\n\n");
        md.append("| Error type | Events | Share |\n");
        md.append("|---|---:|---:|\n");
        appendDistribution(md, errorCounts, rows.size());

        md.append("\n## Concurrent Stress Smoke Test\n\n");
        md.append("| Requests | Concurrency | Errors | Wall time | Mean latency | P95 latency | P99 latency |\n");
        md.append("|---:|---:|---:|---:|---:|---:|---:|\n");
        md.append("| ")
                .append(stressStats.requests()).append(" | ")
                .append(stressStats.concurrency()).append(" | ")
                .append(stressStats.errors()).append(" | ")
                .append(stressStats.wallMs()).append(" ms | ")
                .append(format(stressStats.meanMs())).append(" ms | ")
                .append(stressStats.p95Ms()).append(" ms | ")
                .append(stressStats.p99Ms()).append(" ms |\n");

        md.append("\n## Interpretation Boundary\n\n");
        md.append("These results measure reproducible operational behavior of the prototype. They do not measure learning gain, retention, or authentic student outcome improvement.\n");
        return md.toString();
    }

    private String metricRow(String label, List<? extends Number> values) {
        return "| " + label + " | " + format(mean(values)) + " | "
                + percentile(values, 95) + " | "
                + percentile(values, 99) + " |\n";
    }

    private void appendDistribution(StringBuilder md, Map<String, Long> counts, int total) {
        counts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> md.append("| ")
                        .append(entry.getKey()).append(" | ")
                        .append(entry.getValue()).append(" | ")
                        .append(format(100.0 * entry.getValue() / total)).append("% |\n"));
    }

    private static double mean(List<? extends Number> values) {
        DoubleSummaryStatistics stats = values.stream()
                .mapToDouble(Number::doubleValue)
                .summaryStatistics();
        return stats.getCount() == 0 ? 0 : stats.getAverage();
    }

    private static long percentile(List<? extends Number> sortedValues, int percentile) {
        if (sortedValues.isEmpty()) {
            return 0;
        }
        int index = (int) Math.ceil(percentile / 100.0 * sortedValues.size()) - 1;
        return sortedValues.get(Math.max(0, Math.min(index, sortedValues.size() - 1))).longValue();
    }

    private String escape(String value) {
        String escaped = value == null ? "" : value.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n") || escaped.contains("\r")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }

    private String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private record Scenario(String name, long taskId, String code) {
    }

    private record EventRow(
            int eventIndex,
            long studentId,
            long taskId,
            String scenario,
            String action,
            String errorType,
            boolean compileSuccess,
            int testsPassed,
            int testsFailed,
            int analysisTimeMs,
            long wallLatencyMs,
            String suspiciousRegion
    ) {
    }

    private record StressStats(
            int requests,
            int concurrency,
            int errors,
            long wallMs,
            double meanMs,
            long p95Ms,
            long p99Ms
    ) {
    }
}
