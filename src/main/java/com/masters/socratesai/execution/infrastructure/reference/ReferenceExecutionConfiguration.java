package com.masters.socratesai.execution.infrastructure.reference;

import com.masters.socratesai.execution.domain.ExecutionResultAggregator;
import com.masters.socratesai.execution.domain.port.*;
import com.masters.socratesai.execution.infrastructure.judge0.*;
import com.masters.socratesai.task.verification.VerificationProperties;
import com.masters.socratesai.task.verification.ReferenceVerificationDispatcher;
import com.masters.socratesai.task.verification.ReferenceVerificationStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;

import java.time.Clock;
import java.util.concurrent.*;

@Configuration(proxyBeanMethods = false) @EnableConfigurationProperties(VerificationProperties.class)
public class ReferenceExecutionConfiguration {
    @Bean(destroyMethod = "shutdownNow")
    ExecutorService referenceDispatchExecutor() {
        return new ThreadPoolExecutor(2, 2, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(16),
                Thread.ofPlatform().daemon().name("reference-dispatch-", 0).factory(), new ThreadPoolExecutor.AbortPolicy());
    }

    @Bean
    ReferenceVerificationDispatcher referenceDispatcher(ReferenceExecutionPort execution, ReferenceVerificationStore store,
                                                       @Qualifier("referenceDispatchExecutor") ExecutorService executor) {
        return new ReferenceVerificationDispatcher(execution, store, executor);
    }
    @Bean(destroyMethod = "close")
    SandboxAsyncRuntime referenceAsyncRuntime(VerificationProperties properties, Judge0Properties judge0, Clock clock) {
        properties.validate();
        if (properties.getBatchSize() > judge0.getMaxBatchSize()) throw new IllegalArgumentException("Invalid reference batch configuration");
        var limits = new Judge0Properties();
        limits.setOperationTimeout(properties.getTimeout());
        var scheduler = new ScheduledThreadPoolExecutor(2, Thread.ofPlatform().daemon().name("reference-deadline-", 0).factory());
        scheduler.setRemoveOnCancelPolicy(true);
        return new SandboxAsyncRuntime(limits, scheduler, clock, new Semaphore(properties.getMaxConcurrent()));
    }

    @Bean
    ReferenceExecutionPort referenceExecutionPort(CodeExecutionPort execution, @Qualifier("referenceAsyncRuntime") SandboxAsyncRuntime runtime) {
        return new BatchedReferenceExecutionAdapter(execution, runtime, new ExecutionResultAggregator());
    }
}
