package com.masters.socratesai.execution.infrastructure.judge0;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.masters.socratesai.execution.domain.ExecutionResultAggregator;
import com.masters.socratesai.execution.domain.OutputComparator;
import com.masters.socratesai.execution.domain.port.CodeExecutionPort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Clock;
import java.util.concurrent.*;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(Judge0Properties.class)
public class Judge0Configuration {

    @Bean
    Clock judge0Clock() {
        return Clock.systemUTC();
    }

    @Bean(destroyMethod = "shutdownNow")
    ExecutorService judge0HttpExecutor() {
        return new ThreadPoolExecutor(4, 4, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(128),
                Thread.ofPlatform().daemon().name("judge0-http-", 0).factory(), new ThreadPoolExecutor.AbortPolicy());
    }

    @Bean(destroyMethod = "shutdownNow")
    HttpClient judge0HttpTransport(Judge0Properties properties, @Qualifier("judge0HttpExecutor") ExecutorService executor) {
        properties.validate();
        return HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(properties.getConnectTimeout()).executor(executor).build();
    }

    @Bean
    Judge0HttpClient judge0HttpClient(Judge0Properties properties, @Qualifier("judge0HttpTransport") HttpClient transport,
                                    @Qualifier("judge0Clock") Clock clock) {
        return new Judge0HttpClient(properties, transport, objectMapper(properties), clock);
    }

    ObjectMapper objectMapper(Judge0Properties properties) {
        var factory = JsonFactory.builder().disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(16).maxNumberLength(32)
                        .maxStringLength(properties.getMaxResponseBytes()).build()).build();
        var mapper = new ObjectMapper(factory).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        mapper.coercionConfigFor(LogicalType.Integer).setCoercion(CoercionInputShape.String, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
        mapper.coercionConfigFor(LogicalType.Textual).setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail).setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
        return mapper;
    }

    @Bean(destroyMethod = "close")
    SandboxAsyncRuntime judge0AsyncRuntime(Judge0Properties properties, @Qualifier("judge0Clock") Clock clock) {
        var scheduler = new ScheduledThreadPoolExecutor(2, Thread.ofPlatform().daemon().name("judge0-poll-", 0).factory());
        scheduler.setRemoveOnCancelPolicy(true);
        return new SandboxAsyncRuntime(properties, scheduler, clock, new Semaphore(properties.getMaxConcurrentOperations()));
    }

    @Bean
    CodeExecutionPort codeExecutionPort(Judge0Properties properties, Judge0HttpClient client,
                                       SandboxAsyncRuntime runtime, @Qualifier("judge0Clock") Clock clock) {
        return new Judge0ExecutionAdapter(properties, client, new Judge0RuntimeRegistry(properties, clock),
                new SandboxRequestValidator(properties), new Judge0ResultMapper(new OutputComparator()),
                new ExecutionResultAggregator(), runtime, new BackoffPolicy(properties.getInitialBackoff(), properties.getMaxBackoff()));
    }
}
