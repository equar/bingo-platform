package com.equar.bingo.backend.config;

import java.net.URI;

/** Pure pre-connection policy for the destructive Maven integration-test datasource. */
public final class MavenTestDatabasePolicy {
    public static final String DATABASE = "bingo_qa_maven_test";
    public static final String USERNAME = "bingo_qa_maven_runner";
    private static final int PORT = 3306;

    private MavenTestDatabasePolicy() {
    }

    public static Target validate(String jdbcUrl, String username) {
        if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:mysql://")) {
            throw new IllegalStateException("Maven integration tests require a local MySQL URL for the disposable schema");
        }
        if (!USERNAME.equals(username)) {
            throw new IllegalStateException("Maven integration tests require the dedicated disposable-schema account");
        }

        URI uri;
        try {
            uri = URI.create(jdbcUrl.substring("jdbc:".length()));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Maven integration-test MySQL URL is malformed", exception);
        }

        String path = uri.getPath();
        boolean localHost = "127.0.0.1".equals(uri.getHost()) || "localhost".equalsIgnoreCase(uri.getHost());
        if (!localHost || uri.getPort() != PORT || !("/" + DATABASE).equals(path)
                || createsDatabaseIfMissing(uri.getRawQuery())) {
            throw new IllegalStateException(
                    "Maven integration tests are restricted to local bingo_qa_maven_test on port 3306 with database creation disabled");
        }
        return new Target(uri.getHost(), uri.getPort(), DATABASE, username);
    }

    private static boolean createsDatabaseIfMissing(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        for (String parameter : query.split("&")) {
            String[] pair = parameter.split("=", 2);
            if ("createdatabaseifnotexist".equalsIgnoreCase(pair[0])
                    && pair.length == 2
                    && Boolean.parseBoolean(pair[1])) {
                return true;
            }
        }
        return false;
    }

    public record Target(String host, int port, String schema, String username) {
    }
}
