package com.bakaroo.bingo.backend.common.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.regex.Pattern;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.web.servlet.HandlerMapping;

/** Shared, bounded request fields for diagnostic logs. Never includes query strings or bodies. */
public final class RequestLogSupport {

    public static final String EXCEPTION_LOGGED_ATTRIBUTE = RequestLogSupport.class.getName() + ".exceptionLogged";
    public static final String FAILURE_LOGGED_ATTRIBUTE = RequestLogSupport.class.getName() + ".failureLogged";
    private static final Pattern UUID_SEGMENT = Pattern.compile("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern EMAIL_SEGMENT = Pattern.compile("[^/]{1,128}@[^/]{1,128}");
    private static final Pattern NON_LOG_PATH_CHARACTERS = Pattern.compile("[^A-Za-z0-9_./{}:-]");

    private RequestLogSupport() {
    }

    public static LoggingEventBuilder withRequestFields(LoggingEventBuilder event,
            HttpServletRequest request,
            String operation,
            String outcome,
            int status) {
        return event.addKeyValue("operation", operation)
                .addKeyValue("outcome", outcome)
                .addKeyValue("method", safeMethod(request.getMethod()))
                .addKeyValue("route", safeRoute(request))
                .addKeyValue("status", status)
                .addKeyValue("durationMs", durationMillis(request));
    }

    public static String safeRoute(HttpServletRequest request) {
        Object bestPattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String path = bestPattern instanceof String pattern && !pattern.isBlank()
                ? pattern
                : request.getRequestURI();
        String contextPath = request.getContextPath();
        if (!contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        StringBuilder safe = new StringBuilder(Math.min(path.length(), 160));
        String[] segments = path.split("/", -1);
        for (int index = 1; index < segments.length; index++) {
            String segment = segments[index];
            if (segment.matches("\\d{1,20}") || UUID_SEGMENT.matcher(segment).matches()
                    || EMAIL_SEGMENT.matcher(segment).matches() || segment.length() > 24) {
                segment = "{id}";
            }
            String boundedSegment = segment.length() > 64 ? segment.substring(0, 64) : segment;
            safe.append('/').append(NON_LOG_PATH_CHARACTERS.matcher(boundedSegment).replaceAll("_"));
            if (safe.length() >= 160) {
                safe.setLength(160);
                break;
            }
        }
        return safe.length() == 0 ? "/" : safe.toString();
    }

    public static long durationMillis(HttpServletRequest request) {
        Object started = request.getAttribute(CorrelationIdFilter.START_NANOS_ATTRIBUTE);
        if (!(started instanceof Long startNanos)) {
            return 0;
        }
        return Math.max(0, (System.nanoTime() - startNanos) / 1_000_000L);
    }

    private static String safeMethod(String method) {
        if (method != null && method.matches("[A-Z]{1,12}")) {
            return method;
        }
        return "UNKNOWN";
    }
}
