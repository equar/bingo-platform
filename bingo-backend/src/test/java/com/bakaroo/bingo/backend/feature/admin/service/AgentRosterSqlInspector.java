package com.bakaroo.bingo.backend.feature.admin.service;

import java.util.ArrayList;
import java.util.List;
import org.hibernate.resource.jdbc.spi.StatementInspector;

/** Captures SQL only while a pagination test explicitly requests a capture. */
public final class AgentRosterSqlInspector implements StatementInspector {
    private static final ThreadLocal<List<String>> ACTIVE_CAPTURE = new ThreadLocal<>();

    public static void beginCapture() {
        ACTIVE_CAPTURE.set(new ArrayList<>());
    }

    public static List<String> endCapture() {
        List<String> statements = ACTIVE_CAPTURE.get();
        ACTIVE_CAPTURE.remove();
        return statements == null ? List.of() : List.copyOf(statements);
    }

    @Override
    public String inspect(String sql) {
        List<String> statements = ACTIVE_CAPTURE.get();
        if (statements != null) {
            statements.add(sql);
        }
        return sql;
    }
}
