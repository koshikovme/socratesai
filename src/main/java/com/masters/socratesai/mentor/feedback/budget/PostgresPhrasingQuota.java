package com.masters.socratesai.mentor.feedback.budget;

import com.masters.socratesai.mentor.feedback.gemini.GeminiProperties;
import com.masters.socratesai.mentor.feedback.port.PhrasingQuotaPort;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Short, ordered row locks; no provider request is made within this transaction. */
@Service @RequiredArgsConstructor
public class PostgresPhrasingQuota implements PhrasingQuotaPort {
    private final JdbcTemplate jdbc;
    private final GeminiProperties properties;
    private final Clock clock;

    @Override @Transactional
    public boolean acquire(Long studentId) {
        var now = clock.instant().atOffset(ZoneOffset.UTC);
        var limits = properties.getBudget();
        var buckets = new ArrayList<>(List.of(
                new Bucket("gemini/month", now.withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS), limits.getMonthlyRequests()),
                new Bucket("gemini/minute", now.truncatedTo(ChronoUnit.MINUTES), limits.getRequestsPerMinute())));
        if (studentId != null) buckets.add(new Bucket("gemini/student/" + studentId, now.truncatedTo(ChronoUnit.DAYS), limits.getDailyRequestsPerStudent()));
        var counts = buckets.stream().map(bucket -> {
            jdbc.update("INSERT INTO phrasing_request_quotas(scope, period_start) VALUES (?, ?) ON CONFLICT DO NOTHING", bucket.scope(), bucket.start());
            return jdbc.queryForObject("SELECT request_count FROM phrasing_request_quotas WHERE scope = ? AND period_start = ? FOR UPDATE",
                    Integer.class, bucket.scope(), bucket.start());
        }).toList();
        if (java.util.stream.IntStream.range(0, buckets.size()).anyMatch(index -> counts.get(index) >= buckets.get(index).limit())) return false;
        buckets.forEach(bucket -> jdbc.update("UPDATE phrasing_request_quotas SET request_count = request_count + 1 WHERE scope = ? AND period_start = ?",
                bucket.scope(), bucket.start()));
        return true;
    }

    @Scheduled(fixedDelayString = "${app.gemini.budget.cleanup-ms:3600000}")
    @Transactional
    public void cleanup() {
        if (properties.isEnabled()) jdbc.update("DELETE FROM phrasing_request_quotas WHERE period_start < ?",
                clock.instant().minus(Duration.ofDays(90)).atOffset(ZoneOffset.UTC));
    }

    private record Bucket(String scope, OffsetDateTime start, int limit) {}
}
