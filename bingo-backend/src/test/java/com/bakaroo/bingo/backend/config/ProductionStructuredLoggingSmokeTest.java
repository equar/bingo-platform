package com.bakaroo.bingo.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import ch.qos.logback.core.ContextBase;
import com.bakaroo.bingo.backend.common.api.RequestLogSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.logging.logback.StructuredLogEncoder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;

/** Exercises Boot's Logstash encoder directly with synthetic properties and no application services. */
class ProductionStructuredLoggingSmokeTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void logstashEncoderEmitsParseableSafeEventsWithExceptionFormatting() throws Exception {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Environment previousEnvironment = (Environment) context.getObject(Environment.class.getName());
        ConfigurableEnvironment environment = new MockEnvironment();
        var productionProperties = new YamlPropertySourceLoader()
                .load("application-prod", new ClassPathResource("application-prod.yml"));
        productionProperties.forEach(environment.getPropertySources()::addFirst);
        assertThat(environment.getProperty("logging.structured.format.console")).isEqualTo("logstash");
        assertThat(environment.getProperty("logging.structured.json.add.service")).isEqualTo("bingo-backend");
        assertThat(environment.getProperty("logging.structured.json.add.environment")).isEqualTo("production");
        context.putObject(Environment.class.getName(), environment);

        Logger logger = (Logger) LoggerFactory.getLogger(ProductionStructuredLoggingSmokeTest.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.setContext(context);
        appender.start();
        logger.addAppender(appender);
        StructuredLogEncoder encoder = new StructuredLogEncoder();
        encoder.setContext(context);
        encoder.setFormat("logstash");
        encoder.start();
        try {
            MDC.put("correlationId", "smoke-correlation-42");
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/person@example.test");
            request.setQueryString("token=SYNTHETIC_DO_NOT_LOG");
            RequestLogSupport.withRequestFields(logger.atInfo(), request, "http_request", "success", 200)
                    .log("Synthetic business request completed");
            logger.atError().setCause(new IllegalStateException("synthetic controlled failure"))
                    .addKeyValue("operation", "synthetic_failure")
                    .addKeyValue("outcome", "failed")
                    .log("Synthetic controlled failure");

            List<JsonNode> events = appender.list.stream()
                    .map(event -> parseJson(new String(encoder.encode(event), StandardCharsets.UTF_8)))
                    .toList();
            JsonNode success = events.stream()
                    .filter(event -> "Synthetic business request completed".equals(event.path("message").asText()))
                    .findFirst().orElseThrow();
            assertThat(success.path("service").asText()).isEqualTo("bingo-backend");
            assertThat(success.path("environment").asText()).isEqualTo("production");
            assertThat(success.path("level").asText()).isEqualTo("INFO");
            assertThat(success.path("correlationId").asText()).isEqualTo("smoke-correlation-42");
            assertThat(success.path("operation").asText()).isEqualTo("http_request");
            assertThat(success.path("outcome").asText()).isEqualTo("success");
            assertThat(success.path("route").asText()).isEqualTo("/api/v1/users/{id}");

            JsonNode failure = events.stream()
                    .filter(event -> "Synthetic controlled failure".equals(event.path("message").asText()))
                    .findFirst().orElseThrow();
            assertThat(failure.path("level").asText()).isEqualTo("ERROR");
            assertThat(failure.path("correlationId").asText()).isEqualTo("smoke-correlation-42");
            assertThat(failure.toString()).contains("IllegalStateException", "synthetic controlled failure");
            assertThat(events.toString()).doesNotContain("SYNTHETIC_DO_NOT_LOG", "person@example.test");
        } finally {
            MDC.clear();
            logger.detachAppender(appender);
            appender.stop();
            encoder.stop();
            if (previousEnvironment == null) {
                ((ContextBase) context).removeObject(Environment.class.getName());
            } else {
                context.putObject(Environment.class.getName(), previousEnvironment);
            }
        }
    }

    private JsonNode parseJson(String line) {
        try {
            return objectMapper.readTree(line);
        } catch (Exception exception) {
            throw new AssertionError("Logstash formatter emitted a non-JSON line", exception);
        }
    }
}
