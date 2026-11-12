package com.bakaroo.bingo.backend.common.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestCompletionLoggingFilterTest {

    @Test
    void logsEscapedServletExceptionAsFailureWithoutMisleadingSuccessRecord() {
        RequestCompletionLoggingFilter filter = new RequestCompletionLoggingFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();
        ServletException failure = new ServletException("synthetic chain failure");
        Logger logger = (Logger) LoggerFactory.getLogger(RequestCompletionLoggingFilter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertThatThrownBy(() -> filter.doFilter(request, response,
                    (currentRequest, currentResponse) -> { throw failure; })).isSameAs(failure);

            assertThat(appender.list).hasSize(1);
            ILoggingEvent event = appender.list.getFirst();
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getMessage()).isEqualTo("Unhandled request failure");
            assertThat(event.getThrowableProxy()).isNotNull();
            assertThat(event.getKeyValuePairs()).anySatisfy(pair -> {
                assertThat(pair.key).isEqualTo("outcome");
                assertThat(pair.value).isEqualTo("failed");
            }).anySatisfy(pair -> {
                assertThat(pair.key).isEqualTo("status");
                assertThat(pair.value).isEqualTo(500);
            });
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
