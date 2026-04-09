package com.masters.socratesai.session.service;

import com.masters.socratesai.session.model.StudentTaskSession;
import com.masters.socratesai.session.repo.StudentTaskSessionRepository;
import com.masters.socratesai.support.JpaIntegrationTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringJUnitConfig(JpaIntegrationTestConfig.class)
class StudentTaskSessionServiceConcurrencyTest {

    @Autowired
    private StudentTaskSessionService service;

    @Autowired
    private StudentTaskSessionRepository repository;

    @Test
    void shouldCreateSingleActiveSessionUnderConcurrentRequests() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<StudentTaskSession>> tasks = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                tasks.add(() -> service.getOrCreateActiveSession(77L, 88L));
            }

            List<Future<StudentTaskSession>> futures = executor.invokeAll(tasks);
            Set<?> sessionIds = futures.stream()
                    .map(future -> {
                        try {
                            return future.get().getSessionId();
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .collect(Collectors.toSet());

            assertThat(sessionIds).hasSize(1);
            assertThat(repository.findAll()).hasSize(1);
        } finally {
            executor.shutdownNow();
        }
    }
}
