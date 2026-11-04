package com.masters.socratesai.mentor.feedback.budget;

import com.masters.socratesai.mentor.feedback.gemini.GeminiProperties;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/** Uses only a disposable PostgreSQL container; real transactions exercise concurrent quota claims. */
@Testcontainers
class PostgresPhrasingQuotaTest {
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");
    private final GeminiProperties properties = new GeminiProperties();
    private final Clock clock = mock(Clock.class);
    private DriverManagerDataSource dataSource;
    private AnnotationConfigApplicationContext context;

    @BeforeEach void migrate() {
        String schema = "phrasing_" + UUID.randomUUID().toString().replace("-", "");
        dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl() + (POSTGRES.getJdbcUrl().contains("?") ? "&" : "?") + "currentSchema=" + schema,
                POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).load().migrate();
        when(clock.instant()).thenReturn(Instant.parse("2026-11-05T10:00:00Z"));
        context = startContext();
    }

    @AfterEach void close() { context.close(); }

    @Test void monthlyLimitIsAtomicUnderConcurrentClaimsAndSurvivesNewServiceInstance() throws Exception {
        properties.getBudget().setMonthlyRequests(7);
        var quota = context.getBean(PostgresPhrasingQuota.class);
        try (var pool = Executors.newFixedThreadPool(12)) {
            var futures = IntStream.range(0, 32).mapToObj(index -> pool.submit(() -> quota.acquire((long) index))).toList();
            int accepted = 0;
            for (var future : futures) if (future.get(15, TimeUnit.SECONDS)) accepted++;
            assertThat(accepted).isEqualTo(7);
        }
        context.close(); context = startContext();
        assertThat(context.getBean(PostgresPhrasingQuota.class).acquire(99L)).isFalse();
        assertThat(new JdbcTemplate(dataSource).queryForObject("SELECT request_count FROM phrasing_request_quotas WHERE scope='gemini/month'", Integer.class))
                .isEqualTo(7);
    }

    @Test void studentAndGlobalMinuteAllowancesDoNotIncrementOnRefusal() {
        properties.getBudget().setDailyRequestsPerStudent(1);
        properties.getBudget().setRequestsPerMinute(2);
        var quota = context.getBean(PostgresPhrasingQuota.class);
        assertThat(quota.acquire(1L)).isTrue();
        assertThat(quota.acquire(1L)).isFalse();
        assertThat(quota.acquire(2L)).isTrue();
        assertThat(quota.acquire(3L)).isFalse();
        when(clock.instant()).thenReturn(Instant.parse("2026-11-05T10:01:00Z"));
        assertThat(quota.acquire(1L)).isFalse();
        assertThat(quota.acquire(3L)).isTrue();
        assertThat(new JdbcTemplate(dataSource).queryForObject("SELECT request_count FROM phrasing_request_quotas WHERE scope='gemini/month'", Integer.class))
                .isEqualTo(3);
    }

    @Test void utcMonthAndDayRolloverOpenNewAllowanceWithoutResettingHistoricalCounters() {
        properties.getBudget().setMonthlyRequests(1);
        properties.getBudget().setDailyRequestsPerStudent(1);
        var quota = context.getBean(PostgresPhrasingQuota.class);
        assertThat(quota.acquire(1L)).isTrue();
        when(clock.instant()).thenReturn(Instant.parse("2026-11-06T10:00:00Z"));
        assertThat(quota.acquire(1L)).isFalse();
        when(clock.instant()).thenReturn(Instant.parse("2026-12-01T00:00:00Z"));
        assertThat(quota.acquire(1L)).isTrue();
    }

    private AnnotationConfigApplicationContext startContext() {
        var result = new AnnotationConfigApplicationContext();
        result.registerBean(JdbcTemplate.class, () -> new JdbcTemplate(dataSource));
        result.registerBean(GeminiProperties.class, () -> properties);
        result.registerBean(Clock.class, () -> clock);
        result.registerBean(DataSourceTransactionManager.class, () -> new DataSourceTransactionManager(dataSource));
        result.register(Transactions.class, PostgresPhrasingQuota.class);
        result.refresh(); return result;
    }

    @Configuration @EnableTransactionManagement(proxyTargetClass = true) static class Transactions {}
}
