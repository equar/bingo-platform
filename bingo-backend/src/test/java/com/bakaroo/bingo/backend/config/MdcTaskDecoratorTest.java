package com.bakaroo.bingo.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

class MdcTaskDecoratorTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void propagatesSubmittingContextAndRestoresWorkerContext() {
        TaskDecorator decorator = new AsyncLoggingConfiguration().mdcTaskDecorator();
        AtomicReference<String> observed = new AtomicReference<>();
        MDC.put("correlationId", "request-123");
        Runnable decorated = decorator.decorate(() -> observed.set(MDC.get("correlationId")));
        MDC.clear();
        MDC.put("correlationId", "worker-previous");

        decorated.run();

        assertThat(observed.get()).isEqualTo("request-123");
        assertThat(MDC.get("correlationId")).isEqualTo("worker-previous");
    }

    @Test
    void clearsWorkerContextWhenSubmittingThreadHasNoMdc() {
        TaskDecorator decorator = new AsyncLoggingConfiguration().mdcTaskDecorator();
        AtomicReference<String> observed = new AtomicReference<>("unset");
        MDC.clear();
        Runnable decorated = decorator.decorate(() -> observed.set(MDC.get("correlationId")));
        MDC.put("correlationId", "worker-previous");

        decorated.run();

        assertThat(observed.get()).isNull();
        assertThat(MDC.get("correlationId")).isEqualTo("worker-previous");
    }
}
