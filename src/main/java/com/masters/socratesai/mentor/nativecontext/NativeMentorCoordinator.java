package com.masters.socratesai.mentor.nativecontext;

import com.masters.socratesai.common.api.*;
import com.masters.socratesai.common.validation.ContesterLimits;
import com.masters.socratesai.task.service.AuthoringValidation;
import com.masters.socratesai.websocket.config.MentorSocketSessions;
import com.masters.socratesai.websocket.dto.CodeUpdateMessage;
import jakarta.annotation.PreDestroy;
import jakarta.validation.Validator;
import lombok.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;

/** One latest full buffer per editor; no ML/LLM call runs on the STOMP inbound thread. */
@Component @RequiredArgsConstructor
public class NativeMentorCoordinator {
    private final NativeMentorService mentor;
    private final MentorSocketSessions sessions;
    private final MentorRealtimeProperties properties;
    private final Validator validator;
    private final Clock clock;
    @Qualifier("mentorInferenceExecutor") private final ExecutorService mentorInferenceExecutor;
    @Qualifier("mentorDebounceScheduler") private final ScheduledExecutorService mentorDebounceScheduler;
    private final Map<Key, Editor> editors = new ConcurrentHashMap<>();
    private volatile boolean closed;

    public CompletableFuture<NativeMentorFeedback> submit(MentorIdentity actor, String connectionId, CodeUpdateMessage message) {
        if (!validator.validate(message).isEmpty()) throw new ApiException(ApiErrorCode.INVALID_REQUEST);
        AuthoringValidation.text(message.getCode(), ContesterLimits.SOURCE_BYTES);
        var key = new Key(actor.getStudentId(), message.getEditorSessionId());
        Editor editor;
        synchronized (editors) {
            if (closed) throw new ApiException(ApiErrorCode.CAPACITY_EXCEEDED);
            editor = editors.get(key);
            if (editor == null) {
                if (editors.size() >= properties.getMaxEditors() || editors.keySet().stream().filter(item -> item.studentId().equals(actor.getStudentId())).count() >= properties.getEditorsPerUser())
                    throw new ApiException(ApiErrorCode.CAPACITY_EXCEEDED);
                editor = new Editor(); editors.put(key, editor);
            }
        }
        synchronized (editor) {
            if (editor.latest != null && (!editor.latest.getProblemId().equals(message.getProblemId()) || !editor.latest.getProblemVersionId().equals(message.getProblemVersionId())
                    || !editor.latest.getLanguageKey().equals(message.getLanguageKey()))) throw new ApiException(ApiErrorCode.CONFLICT);
            if (editor.latest != null && message.getCodeRevision() <= editor.latest.getCodeRevision()) return CompletableFuture.failedFuture(new SupersededMentorSnapshot());
            if (editor.pending != null) editor.pending.completeExceptionally(new SupersededMentorSnapshot());
            editor.latest = message; editor.actor = actor; editor.connection = connectionId; editor.touched = clock.millis();
            editor.pending = new CompletableFuture<>();
            var result = editor.pending;
            if (editor.timer != null) editor.timer.cancel(false);
            if (editor.running == null) schedule(editor);
            return result;
        }
    }

    private void schedule(Editor editor) {
        long delay = Math.max(properties.getDebounceMs(), editor.lastStarted + properties.getMinIntervalMs() - clock.millis());
        editor.timer = mentorDebounceScheduler.schedule(() -> dispatch(editor), delay, TimeUnit.MILLISECONDS);
    }
    private void dispatch(Editor editor) {
        synchronized (editor) {
            if (closed || editor.pending == null || editor.running != null) return;
            var work = new Work(editor.actor, editor.connection, editor.latest, editor.pending);
            editor.pending = null; editor.running = work; editor.lastStarted = clock.millis();
            try {
                work.task = mentorInferenceExecutor.submit(() -> run(editor, work));
                work.timeout = mentorDebounceScheduler.schedule(() -> timeout(editor, work), properties.getTimeoutMs(), TimeUnit.MILLISECONDS);
            } catch (RejectedExecutionException ex) { finish(editor, work, null, new ApiException(ApiErrorCode.CAPACITY_EXCEEDED)); }
        }
    }
    private void run(Editor editor, Work work) {
        try {
            synchronized (editor) { if (!current(editor, work)) { finish(editor, work, null, new SupersededMentorSnapshot()); return; } }
            var computed = mentor.compute(work.actor, work.message);
            NativeMentorFeedback saved;
            synchronized (editor) {
                if (!current(editor, work)) { finish(editor, work, null, new SupersededMentorSnapshot()); return; }
                saved = mentor.save(work.actor, work.message, computed);
            }
            finish(editor, work, saved, null);
        } catch (RuntimeException ex) { finish(editor, work, null, ex instanceof ApiException ? ex : new ApiException(ApiErrorCode.INTERNAL_ERROR)); }
    }
    private boolean current(Editor editor, Work work) {
        return !closed && editor.running == work && editor.latest.getCodeRevision().equals(work.message.getCodeRevision())
                && Objects.equals(editor.connection, work.connection) && work.actor.getExpiresAt().isAfter(clock.instant())
                && (work.connection == null || sessions.active(work.connection, work.actor));
    }
    private void timeout(Editor editor, Work work) {
        synchronized (editor) {
            if (editor.running != work) return;
            if (work.task != null) work.task.cancel(true);
            finish(editor, work, null, new ApiException(ApiErrorCode.MENTOR_UNAVAILABLE));
        }
    }
    private void finish(Editor editor, Work work, NativeMentorFeedback feedback, RuntimeException error) {
        synchronized (editor) {
            if (editor.running != work) return;
            editor.running = null; if (work.timeout != null) work.timeout.cancel(false);
            if (editor.pending != null && !closed) schedule(editor);
        }
        if (error == null) work.result.complete(feedback); else work.result.completeExceptionally(error);
    }
    public boolean current(MentorIdentity actor, String connectionId, CodeUpdateMessage message) {
        var editor = editors.get(new Key(actor.getStudentId(), message.getEditorSessionId()));
        if (editor == null) return false;
        synchronized (editor) { return editor.latest.getCodeRevision().equals(message.getCodeRevision()) && Objects.equals(editor.connection, connectionId)
                && actor.getExpiresAt().isAfter(clock.instant()) && (connectionId == null || sessions.active(connectionId, actor)); }
    }
    @EventListener public void disconnected(SessionDisconnectEvent event) {
        editors.entrySet().removeIf(entry -> { var editor = entry.getValue(); synchronized (editor) {
            if (!Objects.equals(editor.connection, event.getSessionId())) return false; cancel(editor); return true;
        }});
    }
    @Scheduled(fixedDelay = 60000) public void reap() {
        editors.entrySet().removeIf(entry -> { var editor = entry.getValue(); synchronized (editor) {
            if (editor.touched + 300000 > clock.millis() && editor.actor.getExpiresAt().isAfter(clock.instant())) return false;
            cancel(editor); return true;
        }});
    }
    private void cancel(Editor editor) {
        if (editor.timer != null) editor.timer.cancel(false);
        if (editor.pending != null) editor.pending.completeExceptionally(new SupersededMentorSnapshot());
        var work = editor.running; editor.running = null;
        if (work != null) { if (work.task != null) work.task.cancel(true); if (work.timeout != null) work.timeout.cancel(false); work.result.completeExceptionally(new SupersededMentorSnapshot()); }
    }
    @PreDestroy public void close() { closed = true; editors.values().forEach(editor -> { synchronized (editor) { cancel(editor); }}); editors.clear(); }
    private record Key(Long studentId, UUID editorId) {}
    private static class Editor {
        private MentorIdentity actor; private String connection; private CodeUpdateMessage latest;
        private CompletableFuture<NativeMentorFeedback> pending; private ScheduledFuture<?> timer; private Work running; private long lastStarted; private long touched;
    }
    @RequiredArgsConstructor private static class Work {
        private final MentorIdentity actor; private final String connection; private final CodeUpdateMessage message;
        private final CompletableFuture<NativeMentorFeedback> result; private Future<?> task; private ScheduledFuture<?> timeout;
    }
}
