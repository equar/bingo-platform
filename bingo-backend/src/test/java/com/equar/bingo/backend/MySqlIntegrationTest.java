package com.equar.bingo.backend;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

public abstract class MySqlIntegrationTest {

    private static final String externalUrl = System.getenv("BINGO_TEST_MYSQL_URL");
    private static final MySQLContainer<?> mysql = externalUrl == null || externalUrl.isBlank()
            ? new MySQLContainer<>("mysql:8.0.36")
            : null;

    static {
        if (mysql != null) {
            mysql.start();
        }
    }

    @DynamicPropertySource
    public static void registerMySqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> mysql == null ? externalUrl : mysql.getJdbcUrl());
        registry.add("spring.datasource.username",
                () -> mysql == null ? requiredExternalProperty("BINGO_TEST_MYSQL_USERNAME") : mysql.getUsername());
        registry.add("spring.datasource.password",
                () -> mysql == null ? requiredExternalProperty("BINGO_TEST_MYSQL_PASSWORD") : mysql.getPassword());
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    private static String requiredExternalProperty(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required when BINGO_TEST_MYSQL_URL is set");
        }
        return value;
    }
}
