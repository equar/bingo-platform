package com.bakaroo.bingo.backend.config;

import java.util.Map;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
public class AsyncLoggingConfiguration {

    @Bean
    TaskDecorator mdcTaskDecorator() {
        return task -> {
            Map<String, String> submittingContext = MDC.getCopyOfContextMap();
            return () -> {
                Map<String, String> previousContext = MDC.getCopyOfContextMap();
                try {
                    setContext(submittingContext);
                    task.run();
                } finally {
                    setContext(previousContext);
                }
            };
        };
    }

    @Bean(name = "taskExecutor")
    ThreadPoolTaskExecutor taskExecutor(
            TaskDecorator mdcTaskDecorator,
            @Value("${spring.task.execution.pool.core-size:4}") int coreSize,
            @Value("${spring.task.execution.pool.max-size:8}") int maxSize,
            @Value("${spring.task.execution.pool.queue-capacity:200}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("bingo-async-");
        executor.setTaskDecorator(mdcTaskDecorator);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }

    private static void setContext(Map<String, String> context) {
        if (context == null || context.isEmpty()) {
            MDC.clear();
        } else {
            MDC.setContextMap(context);
        }
    }
}
