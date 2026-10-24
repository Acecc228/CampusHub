package com.campushub;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import static org.assertj.core.api.Assertions.*;

class DemoEnvironmentIT {
    @Test void demoCannotBeEnabledOutsideExclusiveLocalDevOrTest() {
        for (String profiles : new String[]{"prod", "dev,prod", "test,prod", "staging", "dev,staging", ""}) {
            assertRejected(profiles, "127.0.0.1");
        }
        assertRejected("dev", "0.0.0.0");
    }

    private void assertRejected(String profiles, String address) {
        String password = System.getenv("CH_TEST_DB_PASSWORD");
        assertThat(password).isNotBlank();
        Throwable failure = catchThrowable(() -> {
            try (var context = new SpringApplicationBuilder(CampusHubApplication.class)
                    .initializers(initializing -> initializing.getEnvironment().getPropertySources().addFirst(
                            new org.springframework.core.env.MapPropertySource("test-credentials", Map.of("spring.datasource.password", password))))
                    .run("--spring.profiles.active=" + profiles, "--server.port=0", "--server.address=" + address,
                            "--campushub.auth.demo-enabled=true", "--campushub.database-purpose=test",
                            "--spring.datasource.username=campushub_test_app",
                            "--spring.datasource.url=jdbc:mysql://127.0.0.1:3307/campushub_test?sslMode=REQUIRED&characterEncoding=UTF-8&connectionTimeZone=UTC")) {
                // A successfully started context is the regression this test rejects.
            }
        });
        assertThat(failure).as("demo guard: " + profiles + "/" + address).isNotNull();
        assertThat(failure).hasStackTraceContaining("Demo authentication requires exclusive dev/test profiles and loopback binding");
    }
}
