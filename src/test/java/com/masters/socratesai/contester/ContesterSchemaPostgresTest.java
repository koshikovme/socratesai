package com.masters.socratesai.contester;

import com.masters.socratesai.collection.model.CollectionProblem;
import com.masters.socratesai.collection.model.ProblemCollection;
import com.masters.socratesai.collection.repo.CollectionProblemRepository;
import com.masters.socratesai.collection.repo.ProblemCollectionRepository;
import com.masters.socratesai.common.model.ContentStatus;
import com.masters.socratesai.execution.model.*;
import com.masters.socratesai.execution.repo.*;
import com.masters.socratesai.submission.model.Submission;
import com.masters.socratesai.submission.repo.SubmissionRepository;
import com.masters.socratesai.task.model.*;
import com.masters.socratesai.task.repo.*;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** All connections are built from this disposable container, never app/env DB settings. */
@Testcontainers
class ContesterSchemaPostgresTest {

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbc;
    private String schema;

    @Test
    void bootstrapsAdministratorOnceAndPersistsAnImmutableAuditOnPostgres() {
        migrate("10");
        var properties = new com.masters.socratesai.admin.service.AdminBootstrapProperties();
        properties.setEnabled(true);
        properties.setEmail("operator-fixture@example.test"); properties.setFullName("Synthetic operator");
        String fixturePassword = UUID.randomUUID() + "-test-only";
        properties.setPassword(fixturePassword);
        var encoder = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
        try (var context = repositories()) {
            var service = new com.masters.socratesai.admin.service.AdminBootstrapService(properties,
                    context.getBean(com.masters.socratesai.admin.repo.AdministrationStateRepository.class),
                    context.getBean(com.masters.socratesai.user.repo.UserRepository.class),
                    context.getBean(com.masters.socratesai.admin.repo.RoleChangeAuditRepository.class), encoder);
            transactions(context).executeWithoutResult(status -> service.initialize());
            transactions(context).executeWithoutResult(status -> service.initialize());
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE role='ADMIN'", Integer.class)).isEqualTo(1);
        assertThat(encoder.matches(fixturePassword, jdbc.queryForObject("SELECT password_hash FROM users WHERE role='ADMIN'", String.class))).isTrue();
        assertThat(jdbc.queryForObject("SELECT bootstrap_completed FROM administration_state WHERE id=1", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM role_change_audit WHERE action='BOOTSTRAP' AND actor_id IS NULL", Integer.class)).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update("UPDATE role_change_audit SET reason='tampered'"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @BeforeEach
    void createIsolatedSchema() {
        schema = "contester_" + UUID.randomUUID().toString().replace("-", "");
        var root = new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(), POSTGRES.getPassword()));
        root.execute("CREATE SCHEMA " + schema);
        String separator = POSTGRES.getJdbcUrl().contains("?") ? "&" : "?";
        dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl() + separator + "currentSchema=" + schema,
                POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
    }

    @Test
    void migratesEmptyDatabaseAndValidatesAllJpaMappingsAndRepositories() {
        assertThat(flyway("11").migrate().migrationsExecuted).isEqualTo(11);
        assertThat(flyway("11").migrate().migrationsExecuted).isZero();
        assertThat(flyway("11").validateWithResult().validationSuccessful).isTrue();
        try (var context = repositories()) {
            assertThat(context.getBean(ProblemVersionRepository.class)).isNotNull();
            assertThat(context.getBean(SubmissionRepository.class)).isNotNull();
            assertThat(context.getBean(ExecutionJobRepository.class)).isNotNull();
        }
    }

    @Test
    void backfillsMultipleCatalogBatchesWithoutLosingTestsOrLegacyIds() {
        migrate("8");
        jdbc.update("""
                INSERT INTO tasks(title, topic, difficulty, language, description, published, created_at)
                SELECT 'Fixture ' || n, 'Arithmetic', 'EASY', 'java', 'Statement ' || n, n % 2 = 0, NOW()
                FROM generate_series(1, 503) AS n
                """);
        jdbc.update("""
                INSERT INTO task_tests(task_id, input_data, expected_output, hidden)
                SELECT id, 'sample', 'expected', FALSE FROM tasks
                UNION ALL SELECT id, 'hidden', 'expected', TRUE FROM tasks
                """);
        migrate("9");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tasks", Integer.class)).isEqualTo(503);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM problem_versions", Integer.class)).isEqualTo(503);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM problem_test_cases", Integer.class)).isEqualTo(1006);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM problem_test_cases c JOIN task_tests t ON t.id = c.legacy_test_id
                JOIN problem_versions v ON v.id = c.problem_version_id
                WHERE v.problem_id = t.task_id AND c.hidden = t.hidden AND c.input_data = t.input_data
                    AND c.expected_output = t.expected_output
                """, Integer.class)).isEqualTo(1006);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tasks WHERE published_version_id IS NOT NULL", Integer.class))
                .isEqualTo(251);
    }

    @Test
    void upgradesV8WithoutChangingOldRowsOrGuessingLegacyProblemIdentity() {
        migrate("8");
        long task = jdbc.queryForObject("""
                INSERT INTO tasks(title, topic, difficulty, language, description, starter_code, published, created_at)
                VALUES ('Legacy sum', 'Arithmetic', 'EASY', 'Java', 'Original statement', 'class Main {}', TRUE, NOW())
                RETURNING id
                """, Long.class);
        jdbc.update("INSERT INTO task_tests(task_id, input_data, expected_output, hidden) VALUES (?, '1 2', '3', FALSE)", task);
        jdbc.update("INSERT INTO task_tests(task_id, input_data, expected_output, hidden) VALUES (?, '4 5', '9', TRUE)", task);
        long unpublished = jdbc.queryForObject("""
                INSERT INTO tasks(title, topic, difficulty, language, description, published, created_at)
                VALUES ('Unfinished legacy task', 'Loops', 'EASY', 'python3', 'No judge tests', FALSE, NOW()) RETURNING id
                """, Long.class);
        UUID session = UUID.randomUUID();
        UUID externalSession = UUID.randomUUID();
        jdbc.update("INSERT INTO student_task_sessions(session_id, student_id, task_id) VALUES (?, 91234, ?)", session, task);
        jdbc.update("INSERT INTO student_task_sessions(session_id, student_id, task_id) VALUES (?, 91234, 987654321)", externalSession);
        UUID interaction = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO interaction_logs(interaction_id, session_id, student_id, task_id, feedback_action,
                    feedback_text, mentor_state, mentor_state_confidence, compile_success, tests_passed,
                    feedback_helpful, feedback_rating, student_comment)
                VALUES (?, ?, 91234, ?, 'GUIDING_QUESTION', 'Synthetic feedback', 'semantic_debug', .72,
                    TRUE, 2, TRUE, 4, 'Synthetic fixture')
                """, interaction, session, task);
        jdbc.update("""
                INSERT INTO expert_action_labels(label_id, interaction_id, reviewer_id, target_feedback_action)
                VALUES (?, ?, 12345, 'GUIDING_QUESTION')
                """, UUID.randomUUID(), interaction);
        Map<String, Object> oldTask = jdbc.queryForMap("SELECT * FROM tasks WHERE id = ?", task);
        Map<String, Object> oldSession = jdbc.queryForMap("SELECT * FROM student_task_sessions WHERE session_id = ?", session);
        Map<String, Object> oldLog = jdbc.queryForMap("SELECT * FROM interaction_logs WHERE interaction_id = ?", interaction);
        Map<String, Object> oldLabel = jdbc.queryForMap("SELECT * FROM expert_action_labels WHERE interaction_id = ?", interaction);

        migrate("9");

        assertThat(jdbc.queryForMap("SELECT * FROM tasks WHERE id = ?", task)).containsAllEntriesOf(oldTask);
        assertThat(jdbc.queryForMap("SELECT * FROM student_task_sessions WHERE session_id = ?", session)).containsAllEntriesOf(oldSession);
        assertThat(jdbc.queryForMap("SELECT * FROM interaction_logs WHERE interaction_id = ?", interaction)).containsAllEntriesOf(oldLog);
        assertThat(jdbc.queryForMap("SELECT * FROM expert_action_labels WHERE interaction_id = ?", interaction)).isEqualTo(oldLabel);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tasks", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM task_tests", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM problem_versions WHERE legacy_snapshot", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM problem_test_cases WHERE legacy_test_id IS NOT NULL", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM problem_language_configs WHERE runtime_verified", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM problem_versions WHERE problem_id = ?", String.class, unpublished)).isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject("SELECT visibility FROM tasks WHERE id = ?", String.class, task)).isEqualTo("PUBLIC");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM student_task_sessions WHERE problem_id IS NOT NULL", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT context_source FROM interaction_logs WHERE interaction_id = ?", String.class, interaction))
                .isEqualTo("LEGACY_UNKNOWN");
        assertThat(flyway("9").migrate().migrationsExecuted).isZero();
        migrate("10");
        assertThat(jdbc.queryForMap("SELECT * FROM interaction_logs WHERE interaction_id = ?", interaction)).containsAllEntriesOf(oldLog);
        assertThat(jdbc.queryForMap("SELECT * FROM expert_action_labels WHERE interaction_id = ?", interaction)).isEqualTo(oldLabel);
    }

    @Test
    void roundTripsNewEntitiesAndSourceFreeOwnerScopedRepositoryProjections() {
        migrate("9");
        long teacher = insertUser("TEACHER");
        long student = insertUser("STUDENT");
        try (var context = repositories()) {
            var transactions = transactions(context);
            UUID submissionId = transactions.execute(ignored -> {
                Task problem = context.getBean(TaskRepository.class).saveAndFlush(Task.builder()
                        .ownerId(teacher).title("Sum").topic("Arithmetic").difficulty(TaskDifficulty.EASY)
                        .language("python3").description("Read two integers").published(false)
                        .createdAt(LocalDateTime.now()).build());
                ProblemVersion version = context.getBean(ProblemVersionRepository.class).saveAndFlush(ProblemVersion.builder()
                        .problem(problem).versionNumber(1).title("Sum").topic("Arithmetic")
                        .difficulty(TaskDifficulty.EASY).statement("Read two integers").build());
                context.getBean(ProblemLanguageConfigRepository.class).saveAndFlush(ProblemLanguageConfig.builder()
                        .problemVersion(version).languageKey("python3").runtimeKey("fixture-python")
                        .runtimeVersion("fixture").runtimeVerified(true).timeLimitMs(2000).wallTimeLimitMs(5000)
                        .memoryLimitKb(262144).outputLimitBytes(65536).build());
                ProblemTestCase test = context.getBean(ProblemTestCaseRepository.class).saveAndFlush(ProblemTestCase.builder()
                        .problemVersion(version).position(0).inputData("1 2").expectedOutput("3").hidden(false).build());
                ProblemCollection collection = context.getBean(ProblemCollectionRepository.class).saveAndFlush(
                        ProblemCollection.builder().ownerId(teacher).title("Week 1").build());
                context.getBean(CollectionProblemRepository.class).saveAndFlush(CollectionProblem.builder()
                        .collection(collection).problemId(problem.getId()).problemVersion(version).position(0).build());
                version.setStatus(ProblemVersionStatus.PUBLISHED);
                version.setPublishedAt(OffsetDateTime.now());
                context.getBean(ProblemVersionRepository.class).saveAndFlush(version);
                problem.setPublished(true);
                problem.setStatus(ContentStatus.PUBLISHED);
                problem.setPublishedVersionId(version.getId());
                context.getBean(TaskRepository.class).saveAndFlush(problem);

                ExecutionSnapshot snapshot = snapshot(problem.getId(), version.getId());
                Submission submission = context.getBean(SubmissionRepository.class).saveAndFlush(Submission.builder()
                        .studentId(student).snapshot(snapshot).collectionId(collection.getId()).attemptNumber(1)
                        .idempotencyKey(UUID.randomUUID()).build());
                ExecutionRun run = context.getBean(ExecutionRunRepository.class).saveAndFlush(ExecutionRun.builder()
                        .requestedBy(student).snapshot(snapshot).mode(ExecutionMode.SAMPLE_RUN)
                        .expiresAt(OffsetDateTime.now().plusHours(1)).build());
                ExecutionJob job = context.getBean(ExecutionJobRepository.class).saveAndFlush(ExecutionJob.builder()
                        .problemVersionId(version.getId()).submissionId(submission.getId()).mode(ExecutionMode.SUBMISSION)
                        .deadlineAt(OffsetDateTime.now().plusMinutes(5)).build());
                context.getBean(ExecutionJobRepository.class).saveAndFlush(ExecutionJob.builder()
                        .problemVersionId(version.getId()).runId(run.getId()).mode(ExecutionMode.SAMPLE_RUN)
                        .deadlineAt(OffsetDateTime.now().plusMinutes(5)).build());
                context.getBean(ExecutionCaseResultRepository.class).saveAndFlush(ExecutionCaseResult.builder()
                        .job(job).problemVersionId(version.getId()).testCase(test).stdout("3")
                        .status(CaseExecutionStatus.FINISHED).verdict(ExecutionVerdict.ACCEPTED).build());

                var sql = context.getBean(SqlCapture.class);
                sql.statements.clear();
                assertThat(context.getBean(TaskRepository.class).findAllByOwnerId(teacher, PageRequest.of(0, 10)))
                        .extracting(summary -> summary.getTitle()).containsExactly("Sum");
                assertThat(sql.statements).noneMatch(statement -> statement.contains("description")
                        || statement.contains("starter_code") || statement.contains("task_tests"));
                assertThat(context.getBean(TaskRepository.class).findByIdAndOwnerId(problem.getId(), student)).isEmpty();
                assertThat(context.getBean(ProblemCollectionRepository.class).findAllByOwnerId(teacher, PageRequest.of(0, 10)))
                        .extracting(summary -> summary.getTitle()).containsExactly("Week 1");
                assertThat(context.getBean(CollectionProblemRepository.class).findAllByCollection_IdOrderByPosition(collection.getId()))
                        .singleElement().extracting(member -> member.getProblemVersion().getTitle()).isEqualTo("Sum");
                assertThat(context.getBean(ProblemTestCaseRepository.class).findAllByProblemVersion_IdAndHiddenFalseOrderByPosition(version.getId()))
                        .hasSize(1);
                assertThat(context.getBean(ProblemLanguageConfigRepository.class).findByProblemVersion_IdAndLanguageKey(version.getId(), "python3"))
                        .isPresent();
                assertThat(context.getBean(ExecutionCaseResultRepository.class).findAllByJob_IdAndGenerationOrderByTestCase_Position(job.getId(), 0))
                        .singleElement().extracting(ExecutionCaseResult::getStdout).isEqualTo("3");
                assertThat(context.getBean(ExecutionRunRepository.class).findByIdAndRequestedBy(run.getId(), teacher)).isEmpty();
                assertThat(context.getBean(ExecutionJobRepository.class).findByRunId(run.getId())).isPresent();
                return submission.getId();
            });

            transactions.executeWithoutResult(ignored -> {
                var repository = context.getBean(SubmissionRepository.class);
                Submission submission = repository.findByIdAndStudentId(submissionId, student).orElseThrow();
                assertThat(submission.getSnapshot().getSourceCode()).isEqualTo("print(3)");
                assertThat(submission.getLockVersion()).isZero();
                assertThat(submission.getCreatedAt()).isNotNull();
                assertThat(repository.findByIdAndStudentId(submissionId, teacher)).isEmpty();
                assertThat(repository.findByStudentIdAndIdempotencyKey(student, submission.getIdempotencyKey())).isPresent();
                assertThat(repository.countByStudentIdAndStatusIn(student, List.of(ExecutionStatus.QUEUED))).isEqualTo(1);
                var sql = context.getBean(SqlCapture.class);
                sql.statements.clear();
                assertThat(repository.findSummariesByStudentId(student, PageRequest.of(0, 10)))
                        .singleElement().satisfies(summary -> {
                            assertThat(summary.id()).isEqualTo(submissionId);
                            assertThat(summary.status()).isEqualTo(ExecutionStatus.QUEUED);
                            assertThat(summary.verdict()).isNull();
                        });
                assertThat(sql.statements).isNotEmpty().noneMatch(statement -> statement.contains("source_code"));
                assertThat(repository.findSummariesByStudentId(teacher, PageRequest.of(0, 10))).isEmpty();
            });
        }
    }

    @Test
    void preventsCrossProblemReferencesDuplicateKeysAndIncompleteAcceptedResults() {
        migrate("9");
        Fixture fixture = seedFixture();
        UUID key = UUID.randomUUID();
        UUID submission = insertSubmission(fixture, key, 1);
        reject(() -> insertSubmission(fixture, key, 2), "uq_submission_idempotency");
        reject(() -> insertSubmission(fixture, UUID.randomUUID(), 1), "uq_submission_attempt");
        reject(() -> jdbc.update("UPDATE submissions SET status = 'FINISHED', verdict = 'ACCEPTED', finished_at = NOW() WHERE id = ?", submission),
                "chk_submission_accepted");
        reject(() -> jdbc.update("UPDATE submissions SET tests_passed = 1 WHERE id = ?", submission), "chk_submission_counts");
        reject(() -> jdbc.update("UPDATE submissions SET source_code = 'tampered' WHERE id = ?", submission), "immutable");
        Fixture other = seedFixture();
        reject(() -> jdbc.update("INSERT INTO collection_problems(collection_id, problem_id, problem_version_id, position) VALUES (?, ?, ?, 0)",
                fixture.collection(), fixture.problem(), other.version()), "fk_collection_problem_version");
        UUID job = insertJob(fixture.version(), submission);
        reject(() -> jdbc.update("INSERT INTO execution_case_results(job_id, problem_version_id, test_case_id) VALUES (?, ?, ?)",
                job, fixture.version(), other.test()), "fk_case_result_test");
        reject(() -> jdbc.update("UPDATE execution_jobs SET status = 'RUNNING' WHERE id = ?", job), "chk_job_lease");
        reject(() -> jdbc.update("UPDATE execution_jobs SET run_id = ? WHERE id = ?", UUID.randomUUID(), job), "chk_job_target");
        jdbc.update("UPDATE submissions SET tests_total = 1, tests_passed = 1, status = 'FINISHED', verdict = 'ACCEPTED', finished_at = NOW() WHERE id = ?", submission);
        assertThat(jdbc.queryForObject("SELECT verdict FROM submissions WHERE id = ?", String.class, submission)).isEqualTo("ACCEPTED");
    }

    @Test
    void protectsPublishedSnapshotsAndOnlyDeduplicatesExplicitlyNativeActiveSessions() {
        migrate("9");
        Fixture fixture = seedFixture();
        jdbc.update("UPDATE problem_versions SET status = 'PUBLISHED', published_at = NOW() WHERE id = ?", fixture.version());
        reject(() -> jdbc.update("UPDATE problem_versions SET statement = 'changed' WHERE id = ?", fixture.version()), "immutable");
        reject(() -> jdbc.update("UPDATE problem_test_cases SET expected_output = 'changed' WHERE id = ?", fixture.test()), "immutable");
        reject(() -> jdbc.update("DELETE FROM problem_language_configs WHERE problem_version_id = ?", fixture.version()), "immutable");
        reject(() -> jdbc.update("INSERT INTO problem_test_cases(problem_version_id, position, input_data, expected_output, hidden) VALUES (?, 1, '', '', TRUE)",
                fixture.version()), "immutable");

        jdbc.update("INSERT INTO student_task_sessions(session_id, student_id, task_id) VALUES (?, ?, ?)", UUID.randomUUID(), fixture.student(), fixture.problem());
        jdbc.update("INSERT INTO student_task_sessions(session_id, student_id, task_id) VALUES (?, ?, ?)", UUID.randomUUID(), fixture.student(), fixture.problem());
        insertNativeSession(fixture);
        reject(() -> insertNativeSession(fixture), "uq_native_active_session");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM student_task_sessions", Integer.class)).isEqualTo(3);
    }

    @Test
    void locksDifferentQueuedJobsAcrossConcurrentWorkersAndRequiresATransaction() throws Exception {
        migrate("9");
        Fixture fixture = seedFixture();
        UUID first = insertJob(fixture.version(), insertSubmission(fixture, UUID.randomUUID(), 1));
        UUID second = insertJob(fixture.version(), insertSubmission(fixture, UUID.randomUUID(), 2));
        try (var context = repositories(); var executor = Executors.newSingleThreadExecutor()) {
            var repository = context.getBean(ExecutionJobRepository.class);
            var tx = transactions(context);
            assertThatThrownBy(() -> repository.lockNextJobs(OffsetDateTime.now(), 1))
                    .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
            tx.executeWithoutResult(ignored -> {
                List<ExecutionJob> locked = repository.lockNextJobs(OffsetDateTime.now(), 1);
                assertThat(locked).hasSize(1);
                try {
                    UUID other = executor.submit(() -> tx.execute(status -> repository.lockNextJobs(OffsetDateTime.now(), 1).getFirst().getId()))
                            .get(5, TimeUnit.SECONDS);
                    assertThat(List.of(locked.getFirst().getId(), other)).containsExactlyInAnyOrder(first, second);
                } catch (Exception ex) {
                    throw new AssertionError("Concurrent worker must skip a locked job", ex);
                }
            });
        }
    }

    @Test
    void detectsStaleAuthorEditsThroughJpaOptimisticLocking() {
        migrate("9");
        long owner = insertUser("TEACHER");
        try (var context = repositories()) {
            var tx = transactions(context);
            var repository = context.getBean(ProblemCollectionRepository.class);
            ProblemCollection original = tx.execute(ignored -> repository.saveAndFlush(ProblemCollection.builder()
                    .ownerId(owner).title("Original").build()));
            ProblemCollection stale = tx.execute(ignored -> repository.findById(original.getId()).orElseThrow());
            tx.executeWithoutResult(ignored -> {
                ProblemCollection current = repository.findById(original.getId()).orElseThrow();
                current.setTitle("Updated");
                repository.saveAndFlush(current);
            });
            stale.setTitle("Stale edit");
            assertThatThrownBy(() -> tx.executeWithoutResult(ignored -> repository.saveAndFlush(stale)))
                    .isInstanceOf(org.springframework.orm.ObjectOptimisticLockingFailureException.class);
        }
    }

    private Flyway flyway(String target) {
        return Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").target(target).load();
    }

    private Flyway migrate(String target) {
        Flyway flyway = flyway(target);
        flyway.migrate();
        return flyway;
    }

    private AnnotationConfigApplicationContext repositories() {
        migrate("11");
        var context = new AnnotationConfigApplicationContext();
        context.registerBean("dataSource", DataSource.class, () -> dataSource);
        context.register(RepositoryConfig.class);
        context.refresh();
        return context;
    }

    private TransactionTemplate transactions(AnnotationConfigApplicationContext context) {
        return new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
    }

    private long insertUser(String role) {
        return jdbc.queryForObject("""
                INSERT INTO users(email, password_hash, full_name, role, created_at)
                VALUES (?, 'unused-fixture-hash', 'Synthetic account', ?, NOW()) RETURNING id
                """, Long.class, UUID.randomUUID() + "@example.test", role);
    }

    private Fixture seedFixture() {
        long teacher = insertUser("TEACHER");
        long student = insertUser("STUDENT");
        long problem = jdbc.queryForObject("""
                INSERT INTO tasks(owner_id, title, topic, difficulty, language, description, published, created_at)
                VALUES (?, 'Sum', 'Arithmetic', 'EASY', 'python3', 'Read two integers', FALSE, NOW()) RETURNING id
                """, Long.class, teacher);
        long version = jdbc.queryForObject("""
                INSERT INTO problem_versions(problem_id, version_number, title, topic, difficulty, statement)
                VALUES (?, 1, 'Sum', 'Arithmetic', 'EASY', 'Read two integers') RETURNING id
                """, Long.class, problem);
        jdbc.update("""
                INSERT INTO problem_language_configs(problem_version_id, language_key, runtime_key, runtime_version,
                    time_limit_ms, wall_time_limit_ms, memory_limit_kb, output_limit_bytes)
                VALUES (?, 'python3', 'fixture-python', 'fixture', 2000, 5000, 262144, 65536)
                """, version);
        long test = jdbc.queryForObject("""
                INSERT INTO problem_test_cases(problem_version_id, position, input_data, expected_output, hidden)
                VALUES (?, 0, '1 2', '3', FALSE) RETURNING id
                """, Long.class, version);
        long collection = jdbc.queryForObject("INSERT INTO collections(owner_id, title) VALUES (?, 'Week 1') RETURNING id", Long.class, teacher);
        return new Fixture(problem, version, test, student, collection);
    }

    private UUID insertSubmission(Fixture fixture, UUID key, int attempt) {
        return jdbc.queryForObject("""
                INSERT INTO submissions(student_id, problem_id, problem_version_id, language_key, runtime_key,
                    runtime_version, source_code, source_hash, time_limit_ms, wall_time_limit_ms, memory_limit_kb,
                    output_limit_bytes, attempt_number, idempotency_key)
                VALUES (?, ?, ?, 'python3', 'fixture-python', 'fixture', 'print(3)', repeat('a', 64),
                    2000, 5000, 262144, 65536, ?, ?) RETURNING id
                """, UUID.class, fixture.student(), fixture.problem(), fixture.version(), attempt, key);
    }

    private UUID insertJob(long version, UUID submission) {
        return jdbc.queryForObject("""
                INSERT INTO execution_jobs(problem_version_id, submission_id, mode, deadline_at)
                VALUES (?, ?, 'SUBMISSION', NOW() + INTERVAL '5 minutes') RETURNING id
                """, UUID.class, version, submission);
    }

    private void insertNativeSession(Fixture fixture) {
        jdbc.update("""
                INSERT INTO student_task_sessions(session_id, student_id, task_id, problem_id, problem_version_id, language_key, context_source)
                VALUES (?, ?, ?, ?, ?, 'python3', 'NATIVE')
                """, UUID.randomUUID(), fixture.student(), fixture.problem(), fixture.problem(), fixture.version());
    }

    private static ExecutionSnapshot snapshot(long problem, long version) {
        return ExecutionSnapshot.builder().problemId(problem).problemVersionId(version).languageKey("python3")
                .runtimeKey("fixture-python").runtimeVersion("fixture").sourceCode("print(3)").sourceHash("a".repeat(64))
                .timeLimitMs(2000).wallTimeLimitMs(5000).memoryLimitKb(262144).outputLimitBytes(65536).build();
    }

    private void reject(Runnable action, String constraint) {
        assertThatThrownBy(action::run).isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining(constraint);
    }

    private record Fixture(long problem, long version, long test, long student, long collection) {}

    static class SqlCapture implements StatementInspector {
        private final List<String> statements = new CopyOnWriteArrayList<>();

        @Override
        public String inspect(String sql) {
            statements.add(sql);
            return sql;
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackages = "com.masters.socratesai")
    static class RepositoryConfig {

        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource, SqlCapture sqlCapture) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPackagesToScan("com.masters.socratesai");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of(
                    "hibernate.hbm2ddl.auto", "validate",
                    "hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl",
                    "hibernate.jdbc.time_zone", "UTC",
                    "hibernate.session_factory.statement_inspector", sqlCapture));
            return factory;
        }

        @Bean
        SqlCapture sqlCapture() {
            return new SqlCapture();
        }

        @Bean
        PlatformTransactionManager transactionManager(EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }
    }
}
