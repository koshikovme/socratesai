package com.masters.socratesai.submission.repo;

import com.masters.socratesai.common.api.PageResponse;
import com.masters.socratesai.execution.model.*;
import com.masters.socratesai.submission.dto.*;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** Two bounded read queries, with indexed scope predicates and no source column in the list query. */
@Repository @RequiredArgsConstructor
public class SubmissionResultsReader {
    private final EntityManager entities;

    private static final String FROM = """
        from Submission s join User u on u.id = s.studentId
        join ProblemVersion v on v.id = s.snapshot.problemVersionId
        """;
    private static final String ROWS = """
        select s.id as id, s.studentId as studentId, u.fullName as studentName, u.email as studentEmail,
        u.groupName as groupName, s.snapshot.problemId as problemId, s.snapshot.problemVersionId as problemVersionId,
        v.versionNumber as versionNumber, v.title as problemTitle, s.collectionId as collectionId,
        s.snapshot.languageKey as languageKey, s.attemptNumber as attemptNumber, s.status as status, s.verdict as verdict,
        s.testsTotal as testsTotal, s.testsPassed as testsPassed, s.testsFailed as testsFailed,
        s.timeConsumedMs as timeConsumedMs, s.memoryConsumedBytes as memoryConsumedBytes,
        s.createdAt as createdAt, s.finishedAt as finishedAt
        """;
    private static final String TOTALS = """
        select count(s.id) as attempts, count(distinct s.studentId) as students,
        sum(case when s.status = 'FINISHED' and s.verdict = 'ACCEPTED' then 1 else 0 end) as accepted,
        sum(case when s.status in ('QUEUED', 'DISPATCHING', 'RUNNING') then 1 else 0 end) as pending
        """;

    public SubmissionResultsResponse read(Long problemId, Long collectionId, SubmissionResultsFilter filter, int page, int size) {
        if (problemId == null && collectionId == null) throw new com.masters.socratesai.common.api.ApiException(com.masters.socratesai.common.api.ApiErrorCode.INVALID_REQUEST);
        var paging = PageResponse.request(page, size);
        var conditions = Stream.of(
                new Condition("problem", "s.snapshot.problemId = :problem", problemId),
                new Condition("collection", "s.collectionId = :collection", collectionId),
                new Condition("version", "s.snapshot.problemVersionId = :version", filter.problemVersionId()),
                new Condition("student", "s.studentId = :student", filter.studentId()),
                new Condition("language", "s.snapshot.languageKey = :language", filter.languageKey()),
                new Condition("status", "s.status = :status", filter.status()),
                new Condition("verdict", "s.verdict = :verdict", filter.verdict()),
                new Condition("search", "(lower(u.fullName) like :search escape '!' or lower(u.email) like :search escape '!' or lower(u.groupName) like :search escape '!')", search(filter.query()))
        ).filter(condition -> condition.value() != null).toList();
        String where = " where " + conditions.stream().map(Condition::expression).collect(Collectors.joining(" and "));
        var totals = bind(entities.createQuery(TOTALS + FROM + where, Tuple.class), conditions).getSingleResult();
        long count = number(totals, "attempts");
        var rows = bind(entities.createQuery(ROWS + FROM + where + " order by s.createdAt desc, s.id desc", Tuple.class), conditions)
                .setFirstResult(Math.toIntExact(paging.getOffset())).setMaxResults(size).getResultStream().map(this::row).toList();
        return new SubmissionResultsResponse(new PageResponse<>(rows, page, size, count),
                new SubmissionResultsResponse.Statistics(count, number(totals, "students"), number(totals, "accepted"), number(totals, "pending")));
    }

    private <T> TypedQuery<T> bind(TypedQuery<T> query, List<Condition> conditions) {
        conditions.forEach(condition -> query.setParameter(condition.name(), condition.value()));
        return query;
    }

    private String search(String value) {
        return value == null || value.isBlank() ? null : "%" + value.strip().toLowerCase(Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }

    private long number(Tuple value, String key) { return value.get(key) == null ? 0 : ((Number) value.get(key)).longValue(); }

    private StaffSubmissionResponse row(Tuple value) {
        return StaffSubmissionResponse.builder().id(value.get("id", UUID.class))
                .studentId(value.get("studentId", Long.class)).studentName(value.get("studentName", String.class))
                .studentEmail(value.get("studentEmail", String.class)).groupName(value.get("groupName", String.class))
                .problemId(value.get("problemId", Long.class)).problemVersionId(value.get("problemVersionId", Long.class))
                .versionNumber(value.get("versionNumber", Integer.class)).problemTitle(value.get("problemTitle", String.class))
                .collectionId(value.get("collectionId", Long.class)).languageKey(value.get("languageKey", String.class))
                .attemptNumber(value.get("attemptNumber", Integer.class)).status(value.get("status", ExecutionStatus.class))
                .verdict(value.get("verdict", ExecutionVerdict.class)).testsTotal(value.get("testsTotal", Integer.class))
                .testsPassed(value.get("testsPassed", Integer.class)).testsFailed(value.get("testsFailed", Integer.class))
                .timeConsumedMs(value.get("timeConsumedMs", Integer.class)).memoryConsumedBytes(value.get("memoryConsumedBytes", Long.class))
                .createdAt(value.get("createdAt", OffsetDateTime.class)).finishedAt(value.get("finishedAt", OffsetDateTime.class)).build();
    }

    private record Condition(String name, String expression, Object value) {}
}
