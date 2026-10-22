package com.masters.socratesai.execution.service;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;
import java.util.concurrent.*;

@Configuration(proxyBeanMethods = false) @EnableScheduling @EnableConfigurationProperties(JudgingProperties.class)
public class JudgingConfiguration {
    @Bean(destroyMethod = "shutdownNow")
    ExecutorService judgingExecutor(JudgingProperties properties, com.masters.socratesai.execution.infrastructure.judge0.Judge0Properties provider) {
        if (properties.getBatchSize() > provider.getMaxBatchSize()) throw new IllegalArgumentException("Invalid judging batch size");
        return new ThreadPoolExecutor(properties.getConcurrency(), properties.getConcurrency(), 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(128), Thread.ofPlatform().daemon().name("judging-", 0).factory(), new ThreadPoolExecutor.AbortPolicy());
    }
}
