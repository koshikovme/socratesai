package com.masters.socratesai.execution.infrastructure.judge0;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.masters.socratesai.execution.domain.SandboxException;
import com.masters.socratesai.execution.domain.SandboxFailureCode;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor
public final class Judge0HttpClient {
    private final Judge0Properties properties;
    private final HttpClient client;
    private final ObjectMapper mapper;
    private final Clock clock;

    public <T> CompletableFuture<T> get(String path, Instant deadline, Class<T> responseType) {
        return send("GET", path, null, deadline, responseType);
    }

    public <T> CompletableFuture<T> post(String path, Object body, Instant deadline, Class<T> responseType) {
        return send("POST", path, body, deadline, responseType);
    }

    private <T> CompletableFuture<T> send(String method, String path, Object requestBody, Instant deadline, Class<T> type) {
        Duration remaining = Duration.between(clock.instant(), deadline);
        if (remaining.isNegative() || remaining.isZero()) return CompletableFuture.failedFuture(new SandboxException(SandboxFailureCode.DEADLINE_EXCEEDED));
        try {
            Duration timeout = remaining.compareTo(properties.getRequestTimeout()) < 0 ? remaining : properties.getRequestTimeout();
            HttpRequest.Builder builder = HttpRequest.newBuilder(properties.endpoint().resolve(path)).timeout(timeout)
                    .header("Accept", "application/json").header(properties.getAuthHeader(), properties.getApiKey());
            if ("POST".equals(method)) builder.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(requestBody)));
            else builder.GET();
            var raw = client.sendAsync(builder.build(), info -> new BoundedBodySubscriber(properties.getMaxResponseBytes()));
            var result = new CompletableFuture<T>();
            result.whenComplete((value, error) -> { if (result.isCancelled()) raw.cancel(true); });
            raw.whenComplete((response, error) -> {
                if (result.isDone()) return;
                if (error != null) {
                    result.completeExceptionally(safeFailure(error));
                } else if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    result.completeExceptionally(new Judge0HttpException(response.statusCode()));
                } else {
                    try {
                        result.complete(mapper.readValue(response.body(), type));
                    } catch (IOException | RuntimeException ex) {
                        result.completeExceptionally(new SandboxException(SandboxFailureCode.INVALID_RESPONSE));
                    }
                }
            });
            return result;
        } catch (IOException | RuntimeException ex) {
            return CompletableFuture.failedFuture(new SandboxException(SandboxFailureCode.TRANSPORT_ERROR));
        }
    }

    static RuntimeException safeFailure(Throwable error) {
        Throwable value = error;
        for (int depth = 0; depth < 8 && value != null; depth++) {
            if (value instanceof SandboxException exception) return exception;
            if (value instanceof Judge0HttpException exception) return exception;
            value = value.getCause();
        }
        return new SandboxException(SandboxFailureCode.TRANSPORT_ERROR);
    }
}
