package com.masters.socratesai.execution.infrastructure.judge0;

import com.masters.socratesai.execution.domain.SandboxException;
import com.masters.socratesai.execution.domain.SandboxFailureCode;
import com.masters.socratesai.execution.infrastructure.judge0.dto.Judge0ResultResponse;
import com.masters.socratesai.execution.infrastructure.judge0.dto.Judge0TokenResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static com.masters.socratesai.execution.infrastructure.judge0.SandboxFixtures.*;
import static org.assertj.core.api.Assertions.*;

class SandboxConfigurationTest {
    @Test
    void disabledDefaultsNeedNeitherCredentialsNorEndpoint() {
        assertThatCode(() -> new Judge0Properties().validate()).doesNotThrowAnyException();
        assertThatCode(() -> properties().validate()).doesNotThrowAnyException();
        assertThat(properties().toString()).doesNotContain(SECRET);
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://remote.invalid", "https://user:password@host.invalid", "https://host.invalid?key=secret", "https://host.invalid#secret", "file:///tmp/test", "bad endpoint"})
    void rejectsUnsafeEndpointsWithoutEchoingThem(String endpoint) {
        var props = properties();
        props.setBaseUrl(endpoint);
        assertThatThrownBy(props::validate).isInstanceOf(IllegalArgumentException.class).hasNoCause()
                .hasMessageNotContaining(endpoint).hasMessageNotContaining("password");
    }

    @Test
    void supportsLoopbackStubAndExplicitPrivatePlainHttpOptIn() {
        var props = properties();
        props.setBaseUrl("http://127.0.0.1:1234/api");
        props.validate();
        assertThat(props.endpoint().toString()).endsWith("/api/");
        props.setBaseUrl("http://sandbox.internal");
        props.setAllowPlainHttp(true);
        props.validate();
    }

    @Test
    void rejectsInvalidLimitsHeadersCredentialsAndRuntimeAllowlist() {
        Stream.<Consumer<Judge0Properties>>of(
                p -> p.setConnectTimeout(null), p -> p.setRequestTimeout(Duration.ZERO),
                p -> p.setOperationTimeout(Duration.ofMinutes(3)), p -> p.setMaxBackoff(Duration.ofMillis(1)),
                p -> p.setRequestTimeout(Duration.ofSeconds(60)), p -> p.setMaxBatchSize(21),
                p -> p.setMaxConcurrentOperations(0), p -> p.setMaxResponseBytes(10), p -> p.setMaxSourceBytes(80001),
                p -> p.setMaxTestBytes(0), p -> p.setMaxProcesses(129), p -> p.setMaxPollAttempts(0),
                p -> p.setAuthHeader("Host"), p -> p.setAuthHeader("Auth\r\nInjected"),
                p -> p.setApiKey(""), p -> p.setApiKey("secret\nkey"), p -> p.setRuntimes(List.of()),
                p -> p.getRuntimes().getFirst().setVersion("missing-version"),
                p -> p.setRuntimes(List.of(p.getRuntimes().getFirst(), p.getRuntimes().getFirst()))
        ).forEach(change -> {
            var props = properties();
            change.accept(props);
            assertThatThrownBy(props::validate).isInstanceOf(IllegalArgumentException.class).hasNoCause().hasMessageNotContaining(SECRET);
        });
    }

    @Test
    void refusesJdkWireLoggingWhenEnabled() {
        String previous = System.getProperty("jdk.httpclient.HttpClient.log");
        try {
            System.setProperty("jdk.httpclient.HttpClient.log", "headers,content");
            assertThatThrownBy(() -> properties().validate()).isInstanceOf(IllegalArgumentException.class);
        } finally {
            if (previous == null) System.clearProperty("jdk.httpclient.HttpClient.log");
            else System.setProperty("jdk.httpclient.HttpClient.log", previous);
        }
    }

    @Test
    void jsonParsingRejectsAmbiguousOrCoercedProviderFields() {
        var mapper = new Judge0Configuration().objectMapper(properties());
        List.of("{\"token\":123}", "{\"token\":\"a\",\"token\":\"b\"}", "{\"token\":\"a\"} {}")
                .forEach(json -> assertThatThrownBy(() -> mapper.readValue(json, Judge0TokenResponse.class)).isInstanceOf(Exception.class));
        List.of("{\"status\":{\"id\":\"3\"}}", "{\"status\":{\"id\":3.1}}", "{\"memory\":\"123\"}", "{\"time\":0.1}")
                .forEach(json -> assertThatThrownBy(() -> mapper.readValue(json, Judge0ResultResponse.class)).isInstanceOf(Exception.class));
    }

    @Test
    void backoffDoublesThenSaturatesWithoutOverflow() {
        var backoff = new BackoffPolicy(Duration.ofMillis(10), Duration.ofMillis(80));
        assertThat(Stream.of(-1, 0, 1, 2, 3, 4, 63, Integer.MAX_VALUE).map(backoff::delay).map(Duration::toMillis).toList())
                .containsExactly(10L, 10L, 20L, 40L, 80L, 80L, 80L, 80L);
    }

    @Test
    void catalogMustMatchPinnedIdAndFullNameAndHaveBoundedFreshness() {
        var props = properties();
        var now = Instant.parse("2026-01-01T00:00:00Z");
        var registry = new Judge0RuntimeRegistry(props, Clock.fixed(now, ZoneOffset.UTC));
        assertThat(registry.hasFreshCatalog()).isFalse();
        var spec = registry.configuredRuntime(batch(now, List.of()));
        var mapper = new Judge0Configuration().objectMapper(props);
        assertThatCode(() -> registry.acceptCatalog(mapper.readValue("[{\"id\":71,\"name\":\"Python (3.fixture)\"}]",
                com.masters.socratesai.execution.infrastructure.judge0.dto.Judge0LanguageResponse[].class))).doesNotThrowAnyException();
        registry.verify(spec);
        spec.setProviderName("Python (different)");
        assertThatThrownBy(() -> registry.verify(spec)).isInstanceOf(SandboxException.class);
        assertThatThrownBy(() -> registry.acceptCatalog(null)).isInstanceOf(SandboxException.class);
        assertThatThrownBy(() -> registry.acceptCatalog(new com.masters.socratesai.execution.infrastructure.judge0.dto.Judge0LanguageResponse[0]))
                .isInstanceOf(SandboxException.class);
        var wrong = batch(now, List.of());
        props.getRuntimes().getFirst().setVersion("unavailable");
        assertThatThrownBy(() -> registry.configuredRuntime(wrong)).isInstanceOf(SandboxException.class)
                .extracting(ex -> ((SandboxException) ex).getCode()).isEqualTo(SandboxFailureCode.RUNTIME_UNAVAILABLE);
    }
}
