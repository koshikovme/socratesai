package com.masters.socratesai.mentor.nativecontext;

import com.masters.socratesai.common.api.*;
import com.masters.socratesai.websocket.config.MentorSocketSessions;
import com.masters.socratesai.websocket.dto.CodeUpdateMessage;
import jakarta.validation.Validation;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.UUID;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NativeMentorCoordinatorTest {
    private final NativeMentorService service = mock(NativeMentorService.class);
    private final MentorSocketSessions sessions = mock(MentorSocketSessions.class);
    private final MentorRealtimeProperties properties = new MentorRealtimeProperties();
    private final ExecutorService workers = Executors.newFixedThreadPool(2);
    private final ScheduledThreadPoolExecutor timer = new ScheduledThreadPoolExecutor(2);
    private final jakarta.validation.ValidatorFactory validation = Validation.buildDefaultValidatorFactory();
    private final MentorIdentity actor = MentorIdentity.builder().studentId(1L).username("fixture@example.test").expiresAt(Instant.now().plusSeconds(60)).build();
    private final UUID editor = UUID.randomUUID();
    private NativeMentorCoordinator coordinator;
    @BeforeEach void setUp() {
        properties.setDebounceMs(30); properties.setMinIntervalMs(100); properties.setTimeoutMs(1000);
        timer.setRemoveOnCancelPolicy(true);
        coordinator = new NativeMentorCoordinator(service, sessions, properties, validation.getValidator(), Clock.systemUTC(), workers, timer);
        when(service.compute(any(), any())).thenAnswer(call -> new ComputedMentorFeedback(null, feedback(call.getArgument(1)), 0, 0));
        when(service.save(any(), any(), any())).thenAnswer(call -> feedback(call.getArgument(1)));
    }
    @AfterEach void stop() { coordinator.close(); workers.shutdownNow(); timer.shutdownNow(); validation.close(); }
    @Test void rapidBuffersOnlyComputeTheLatestSnapshotAndCannotUseClientAttempts() throws Exception {
        var first = coordinator.submit(actor, null, message(1));
        var second = coordinator.submit(actor, null, message(2));
        var last = coordinator.submit(actor, null, message(3));
        assertThat(last.get(2, TimeUnit.SECONDS).getCodeRevision()).isEqualTo(3);
        assertThat(first).isCompletedExceptionally(); assertThat(second).isCompletedExceptionally();
        verify(service, times(1)).compute(any(), argThat(item -> item.getCodeRevision() == 3));
        assertThat(coordinator.submit(actor, null, message(2))).isCompletedExceptionally();
    }
    @Test void anOldInflightResultCannotPersistOrReplaceANewerRevision() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        doAnswer(call -> { entered.countDown(); assertThat(release.await(2, TimeUnit.SECONDS)).isTrue(); return new ComputedMentorFeedback(null, feedback(call.getArgument(1)), 0, 0); })
                .when(service).compute(any(), argThat(m -> m.getCodeRevision() == 1));
        var old = coordinator.submit(actor, null, message(1)); assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
        var current = coordinator.submit(actor, null, message(2)); release.countDown();
        assertThat(current.get(2, TimeUnit.SECONDS).getCodeRevision()).isEqualTo(2); assertThat(old).isCompletedExceptionally();
        verify(service, never()).save(any(), argThat(m -> m.getCodeRevision() == 1), any());
    }
    @Test void timeBudgetCancelsInferenceAndDoesNotSaveALateResult() throws Exception {
        var entered = new CountDownLatch(1);
        doAnswer(call -> { entered.countDown(); new CountDownLatch(1).await(); return null; }).when(service).compute(any(), any());
        var result = coordinator.submit(actor, null, message(1)); assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
        assertThatThrownBy(() -> result.get(2, TimeUnit.SECONDS)).hasCauseInstanceOf(ApiException.class);
        verify(service, never()).save(any(), any(), any());
    }
    @Test void closedSocketAndShutdownCannotSaveFeedback() throws Exception {
        when(sessions.active(anyString(), any())).thenReturn(false);
        assertThatThrownBy(() -> coordinator.submit(actor, "disconnected-session", message(1)).get(1, TimeUnit.SECONDS)).hasCauseInstanceOf(SupersededMentorSnapshot.class);
        verifyNoInteractions(service); coordinator.close();
        assertThatThrownBy(() -> coordinator.submit(actor, null, message(2))).isInstanceOf(ApiException.class);
    }
    @Test void editorQuotasAndContextValidationAreBoundedBeforeInference() {
        properties.setMaxEditors(1); coordinator.submit(actor, null, message(1));
        var other = message(2); other.setEditorSessionId(UUID.randomUUID());
        assertThatThrownBy(() -> coordinator.submit(actor, null, other)).isInstanceOf(ApiException.class);
        var switched = message(2); switched.setLanguageKey("java");
        assertThatThrownBy(() -> coordinator.submit(actor, null, switched)).isInstanceOf(ApiException.class);
        var tooLarge = message(2); tooLarge.setCode("x".repeat(20001));
        assertThatThrownBy(() -> coordinator.submit(actor, null, tooLarge)).isInstanceOf(ApiException.class);
    }
    private CodeUpdateMessage message(long revision) { return CodeUpdateMessage.builder().problemId(1L).problemVersionId(2L).languageKey("python3")
            .editorSessionId(editor).codeRevision(revision).code("print("+revision+")").build(); }
    private NativeMentorFeedback feedback(CodeUpdateMessage m) { return NativeMentorFeedback.builder().codeRevision(m.getCodeRevision()).editorSessionId(m.getEditorSessionId()).build(); }
}
