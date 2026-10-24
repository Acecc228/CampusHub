package com.campushub;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

abstract class MySqlIntegrationSupport {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        String password = System.getenv("CH_TEST_DB_PASSWORD");
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("Real MySQL test credentials required; use scripts/Invoke-Backend.ps1 -Apply");
        }
        if (System.getenv("FLYWAY_PASSWORD") != null || System.getenv("CH_DB_PASSWORD") != null) {
            throw new IllegalStateException("Test process must not inherit migration or development credentials");
        }
        properties.add("spring.datasource.url", () -> "jdbc:mysql://127.0.0.1:3307/campushub_test?sslMode=REQUIRED&characterEncoding=UTF-8&connectionTimeZone=UTC");
        properties.add("spring.datasource.username", () -> "campushub_test_app");
        properties.add("spring.datasource.password", () -> password);
        properties.add("campushub.database-purpose", () -> "test");
    }
}
