package com.masters.socratesai.mentor.nativecontext;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import java.util.concurrent.*;

@Configuration(proxyBeanMethods = false) @EnableConfigurationProperties(MentorRealtimeProperties.class)
public class NativeMentorConfiguration {
    @Bean org.springframework.boot.web.server.WebServerFactoryCustomizer<org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory> mentorSocketBuffers() {
        return factory -> factory.addContextCustomizers(context -> context.addLifecycleListener(event -> {
            if (org.apache.catalina.Lifecycle.AFTER_START_EVENT.equals(event.getType())) {
                var container = context.getServletContext().getAttribute("jakarta.websocket.server.ServerContainer");
                if (container instanceof jakarta.websocket.server.ServerContainer sockets) {
                    // SockJS JSON escaping can double the outer text frame. The inner STOMP limit remains 128 KiB.
                    sockets.setDefaultMaxTextMessageBufferSize(262144); sockets.setDefaultMaxBinaryMessageBufferSize(131072);
                }
            }
        }));
    }
    @Bean(destroyMethod = "shutdownNow") ExecutorService mentorInferenceExecutor(MentorRealtimeProperties properties) {
        return new ThreadPoolExecutor(properties.getConcurrency(), properties.getConcurrency(), 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(64),
                Thread.ofPlatform().daemon().name("mentor-inference-", 0).factory(), new ThreadPoolExecutor.AbortPolicy());
    }
    @Bean(destroyMethod = "shutdownNow") ScheduledExecutorService mentorDebounceScheduler() {
        var executor = new ScheduledThreadPoolExecutor(2, Thread.ofPlatform().daemon().name("mentor-debounce-", 0).factory());
        executor.setRemoveOnCancelPolicy(true); return executor;
    }
}
