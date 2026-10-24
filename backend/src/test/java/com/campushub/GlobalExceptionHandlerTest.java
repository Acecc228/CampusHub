package com.campushub;

import com.campushub.error.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerTest {
    @Test void databaseErrorsAndLogsDoNotExposeExceptionDetails(CapturedOutput output) {
        var response = new GlobalExceptionHandler().databaseUnavailable(
                new DataAccessResourceFailureException("private-database-marker", new RuntimeException("private-cause-marker")));
        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getBody().code()).isEqualTo("SERVICE_UNAVAILABLE");
        assertThat(response.getBody().message()).doesNotContain("private-");
        assertThat(output.getAll()).contains("Database request failed").doesNotContain("private-database-marker", "private-cause-marker");
    }

    @Test void unexpectedErrorsHaveSafeJsonAndLogs(CapturedOutput output) {
        var response = new GlobalExceptionHandler().unexpected(new Exception("private-request-marker"));
        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().code()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().message()).doesNotContain("private-");
        assertThat(output.getAll()).contains("Request failed").doesNotContain("private-request-marker");
    }
}
