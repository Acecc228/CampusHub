package com.campushub;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.SQLException;
import java.util.UUID;
import com.campushub.config.DatabaseIdentityGuard;
import com.campushub.category.ActivityCategoryService;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class BackendIT extends MySqlIntegrationSupport {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired ObjectMapper json;
    @Autowired ActivityCategoryService categories;
    private final HttpClient client = HttpClient.newHttpClient();

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test void categoriesComeFromRealMySqlInStableOrder() throws Exception {
        var response = get("/api/v1/activity-categories");
        assertThat(response.statusCode()).isEqualTo(200);
        var body = json.readTree(response.body());
        var rows = jdbc.queryForList("SELECT id,code,name,sort_order FROM activity_category ORDER BY sort_order,id");
        assertThat(body.size()).isEqualTo(rows.size()).isGreaterThanOrEqualTo(4);
        for (int i = 0; i < rows.size(); i++) {
            assertThat(body.get(i).get("code").asText()).isEqualTo(rows.get(i).get("code"));
            assertThat(body.get(i).get("name").asText()).isEqualTo(rows.get(i).get("name"));
            assertThat(body.get(i).get("id").asLong()).isEqualTo(((Number) rows.get(i).get("id")).longValue());
        }
        assertThat(body.get(0).get("name").asText()).isEqualTo("学术科技");
    }

    @Test void healthIsPublicAndDoesNotDiscloseDatabaseDetails() throws Exception {
        var response = get("/actuator/health");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json.readTree(response.body()).get("status").asText()).isEqualTo("UP");
        assertThat(response.body()).doesNotContain("components", "jdbc:", "password");
    }

    @Test void protectedAndUnknownAnonymousRequestsReturnJson401() throws Exception {
        for (String path : new String[]{"/actuator/info", "/api/v1/unknown", "/api/v1/activity-categories/1", "/error"}) {
            var response = get(path);
            assertThat(response.statusCode()).as(path).isEqualTo(401);
            assertThat(json.readTree(response.body()).get("code").asText()).isEqualTo("UNAUTHORIZED");
            assertThat(response.body()).doesNotContain("trace", "exception", "jdbc:");
        }
    }

    @Test void categoryWriteMethodIsNotPublic() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/activity-categories"))
                .POST(HttpRequest.BodyPublishers.noBody()).build();
        assertThat(client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
    }

    @Test void devWithoutExplicitOptInCannotUseDemoLogin() throws Exception {
        var cookies = new java.net.CookieManager(null, java.net.CookiePolicy.ACCEPT_ALL);
        var browser = HttpClient.newBuilder().cookieHandler(cookies).build();
        String base = "http://127.0.0.1:" + port;
        var csrf = browser.send(HttpRequest.newBuilder(URI.create(base + "/api/v1/auth/csrf")).GET().build(), HttpResponse.BodyHandlers.ofString());
        String token = json.readTree(csrf.body()).get("token").asText();
        var request = HttpRequest.newBuilder(URI.create(base + "/api/v1/auth/demo-login"))
                .header("X-CSRF-TOKEN", token).header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("username=demo_student&password=" + System.getenv("CH_DEMO_STUDENT_PASSWORD"))).build();
        assertThat(browser.send(request, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
        assertThat(get("/login").statusCode()).isEqualTo(401);
    }

    @Test void developmentOpenApiAndSwaggerUiAreReal() throws Exception {
        var response = get("/v3/api-docs");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json.readTree(response.body()).get("paths").has("/api/v1/activity-categories")).isTrue();
        assertThat(get("/swagger-ui/index.html").statusCode()).isEqualTo(200);
    }

    @Test void realDatabaseIdentityAndTlsAreCorrect() throws Exception {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            var row = statement.executeQuery("SELECT DATABASE(),CURRENT_USER(),@@port,VERSION(),@@datadir");
            assertThat(row.next()).isTrue();
            assertThat(row.getString(1)).isEqualTo("campushub_test");
            assertThat(row.getString(2)).isEqualTo("campushub_test_app@127.0.0.1");
            assertThat(row.getInt(3)).isEqualTo(3307);
            assertThat(row.getString(4)).isEqualTo("8.4.11");
            assertThat(row.getString(5).replace('\\','/')).isEqualToIgnoringCase("E:/CampusHubRuntime/mysql84/data/");
            row = statement.executeQuery("SHOW SESSION STATUS LIKE 'Ssl_cipher'");
            assertThat(row.next()).isTrue();
            assertThat(row.getString(2)).isNotBlank();
        }
    }

    @Test void applicationPurposeRejectsActualTestAccountAndUnknownPurpose() {
        assertThatThrownBy(() -> new DatabaseIdentityGuard(dataSource, "application", new org.springframework.mock.env.MockEnvironment()).run(null))
                .isInstanceOf(IllegalStateException.class).hasMessage("Database identity guard rejected the connection");
        assertThatThrownBy(() -> new DatabaseIdentityGuard(dataSource, "unknown", new org.springframework.mock.env.MockEnvironment()).run(null))
                .isInstanceOf(IllegalStateException.class).hasMessage("Unsupported database purpose");
    }

    @Test void applicationRuntimeContainsNoFlyway() {
        assertThatThrownBy(() -> Class.forName("org.flywaydb.core.Flyway")).isInstanceOf(ClassNotFoundException.class);
    }

    @Test void unicodeRoundTripsAndSpringTransactionRollsBack() {
        String code = "T_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        String name = "校园活动|☃|😀|𠀀|e\u0301";
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).execute(status -> {
            jdbc.update("INSERT INTO activity_category(code,name,sort_order) VALUES (?,?,?)", code, name, 99);
            assertThat(jdbc.queryForObject("SELECT name FROM activity_category WHERE code=?", String.class, code)).isEqualTo(name);
            assertThat(jdbc.queryForObject("SELECT HEX(name) FROM activity_category WHERE code=?", String.class, code))
                    .isEqualTo("E6A0A1E59BADE6B4BBE58AA87CE298837CF09F98807CF0A080807C65CC81");
            assertThat(categories.findAll()).anySatisfy(category -> {
                assertThat(category.code()).isEqualTo(code);
                assertThat(category.name()).isEqualTo(name);
            });
            jdbc.update("UPDATE activity_category SET sort_order=100 WHERE code=?", code);
            throw new IllegalStateException("rollback-probe");
        })).isInstanceOf(IllegalStateException.class).hasMessage("rollback-probe");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM activity_category WHERE code=?", Integer.class, code)).isZero();
    }

    @Test void applicationCannotDdlReadHistoryProbeOrDevelopmentSchema() throws Exception {
        // Do not target real tables with destructive statements; failed permission probes leave no data.
        String[] queries = {"CREATE TABLE m10c_forbidden_probe(id INT)",
                "DROP TABLE m10c_nonexistent_forbidden", "SELECT * FROM flyway_schema_history",
                "SELECT * FROM m10b_unicode_probe", "SELECT * FROM campushub.activity_category"};
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            for (String sql : queries) {
                SQLException failure = catchThrowableOfType(SQLException.class, () -> statement.execute(sql));
                assertThat((Object) failure).as("Permission probe").isNotNull();
                assertThat(failure.getErrorCode()).isIn(1142, 1044);
            }
        }
    }
}
