package com.campushub;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "campushub.auth.demo-enabled=true")
@ActiveProfiles("test")
class ActivityIT extends MySqlIntegrationSupport {
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired javax.sql.DataSource dataSource;
    @Autowired org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext serverContext;
    final List<Long> created = new ArrayList<>();
    final List<Long> users = new ArrayList<>();
    static final String MANAGE = "/api/v1/manage/activities";
    static final String PUBLIC = "/api/v1/activities";

    class Browser {
        final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        final HttpClient client = HttpClient.newBuilder()
                .cookieHandler(cookies).build();
        String csrf;
        HttpResponse<String> request(String method, String path, Object body) throws Exception {
            var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
            if (csrf != null) builder.header("X-CSRF-TOKEN", csrf);
            builder.header("Content-Type", "application/json").method(method, body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        }
        void csrf() throws Exception {
            csrf = json.readTree(request("GET", "/api/v1/auth/csrf", null).body()).get("token").asText();
        }
        void login(String role) throws Exception {
            loginAs("demo_" + role, role);
        }
        void loginAs(String name, String role) throws Exception {
            csrf();
            String password = System.getenv("CH_DEMO_" + role.toUpperCase() + "_PASSWORD");
            assertThat(password).isNotBlank();
            var req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/auth/demo-login"))
                    .header("X-CSRF-TOKEN", csrf).header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString("username=" + name + "&password="
                            + URLEncoder.encode(password, StandardCharsets.UTF_8))).build();
            assertThat(client.send(req, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);
            csrf();
        }
    }

    Browser browser(String role) throws Exception {
        var browser = new Browser();
        if (role != null) browser.login(role);
        return browser;
    }

    Map<String, Object> input() {
        Instant base = Instant.now().truncatedTo(ChronoUnit.SECONDS).plus(10, ChronoUnit.DAYS);
        var value = new LinkedHashMap<String, Object>();
        value.put("title", "校园活动 😀");
        value.put("summary", "真实数据库测试");
        value.put("content", "详细内容\n第二行");
        value.put("categoryId", jdbc.queryForObject("SELECT MIN(id) FROM activity_category", Long.class));
        value.put("location", "图书馆");
        value.put("startTime", base.toString());
        value.put("endTime", base.plus(2, ChronoUnit.HOURS).toString());
        value.put("registrationStart", base.minus(2, ChronoUnit.DAYS).toString());
        value.put("registrationEnd", base.minus(1, ChronoUnit.DAYS).toString());
        value.put("capacity", 30);
        return value;
    }

    JsonNode expect(HttpResponse<String> response, int status) {
        assertThat(response.statusCode()).as("HTTP status; body=%s", response.body()).isEqualTo(status);
        return json.readTree(response.body());
    }

    JsonNode create(Browser browser, Map<String, Object> body) throws Exception {
        var result = expect(browser.request("POST", MANAGE, body), 201);
        created.add(result.get("id").asLong());
        return result;
    }

    @AfterEach void removeOnlyThisTestsActivities() {
        for (Long id : created) jdbc.update("DELETE FROM activity WHERE id=?", id);
        for (Long id : users) {
            jdbc.update("DELETE FROM app_user_role WHERE user_id=?", id);
            jdbc.update("DELETE FROM app_user WHERE id=?", id);
        }
    }

    @Test void lifecycleAndPublicVisibility() throws Exception {
        var owner = browser("organizer");
        var student = browser("student");
        var body = input();
        var draft = create(owner, body);
        long id = draft.get("id").asLong();
        assertThat(draft.get("status").asText()).isEqualTo("DRAFT");
        expect(student.request("GET", PUBLIC + "/" + id, null), 404);
        body.put("version", 0);
        body.put("title", "更新后的活动");
        var edited = expect(owner.request("PUT", MANAGE + "/" + id, body), 200);
        assertThat(edited.get("version").asLong()).isEqualTo(1);
        expect(owner.request("POST", MANAGE + "/" + id + "/publish", Map.of("version", 1)), 200);
        assertThat(expect(student.request("GET", PUBLIC + "/" + id, null), 200).get("title").asText())
                .isEqualTo("更新后的活动");
        expect(owner.request("POST", MANAGE + "/" + id + "/unpublish", Map.of("version", 2)), 200);
        expect(student.request("GET", PUBLIC + "/" + id, null), 404);
        expect(owner.request("POST", MANAGE + "/" + id + "/publish", Map.of("version", 3)), 200);
    }

    @Test void managementRequiresAuthenticationAndRoleAndCsrf() throws Exception {
        var anonymous = browser(null);
        expect(anonymous.request("GET", MANAGE, null), 401);
        expect(anonymous.request("GET", PUBLIC, null), 401);
        expect(anonymous.request("POST", MANAGE, input()), 401);
        var student = browser("student");
        expect(student.request("POST", MANAGE, input()), 403);
        expect(student.request("GET", MANAGE, null), 403);
        var owner = browser("organizer");
        owner.csrf = null;
        expect(owner.request("POST", MANAGE, input()), 403);
    }

    @Test void ownershipIsEnforcedAndAdminCanManageOtherOrganizers() throws Exception {
        String name = "m12_" + java.util.UUID.randomUUID().toString().replace("-", "");
        jdbc.update("""
                INSERT INTO app_user(login_name,display_name,password_hash,enabled,demo_account)
                SELECT ?, 'M1.2 isolated organizer',password_hash,1,1 FROM app_user WHERE login_name='demo_organizer'
                """, name);
        long userId = jdbc.queryForObject("SELECT id FROM app_user WHERE login_name=?", Long.class, name);
        users.add(userId);
        jdbc.update("INSERT INTO app_user_role(user_id,role_id) SELECT ?,id FROM app_role WHERE code='ORGANIZER'", userId);
        var other = new Browser(); other.loginAs(name, "organizer");
        var owner = browser("organizer");
        var admin = browser("admin");
        var body = input();
        body.put("creatorId", userId); body.put("status", "PUBLISHED"); body.put("role", "ADMIN");
        var draft = create(owner, body);
        long id = draft.get("id").asLong();
        assertThat(draft.get("creatorId").asLong()).isNotEqualTo(userId);
        assertThat(draft.get("status").asText()).isEqualTo("DRAFT");
        body.put("version", 0);
        expect(other.request("GET", MANAGE + "/" + id, null), 403);
        expect(other.request("PUT", MANAGE + "/" + id, body), 403);
        expect(other.request("POST", MANAGE + "/" + id + "/publish", Map.of("version", 0)), 403);
        expect(other.request("POST", MANAGE + "/" + id + "/unpublish", Map.of("version", 0)), 403);
        assertThat(expect(other.request("GET", MANAGE, null), 200).get("total").asLong()).isZero();
        body.put("title", "管理员修订");
        var edit = expect(admin.request("PUT", MANAGE + "/" + id, body), 200);
        assertThat(edit.get("creatorId")).isEqualTo(draft.get("creatorId"));
        expect(admin.request("POST", MANAGE + "/" + id + "/publish", Map.of("version", 1)), 200);
        expect(admin.request("POST", MANAGE + "/" + id + "/unpublish", Map.of("version", 2)), 200);
        assertThat(expect(admin.request("GET", MANAGE + "/" + id, null), 200).get("status").asText()).isEqualTo("UNPUBLISHED");
        create(admin, input());
    }

    @Test void publishedFieldsFreezeAndInvalidOrStaleTransitionsConflict() throws Exception {
        var owner = browser("organizer");
        var admin = browser("admin");
        var body = input();
        long id = create(owner, body).get("id").asLong();
        expect(owner.request("POST", MANAGE + "/" + id + "/unpublish", Map.of("version", 0)), 409);
        expect(owner.request("POST", MANAGE + "/" + id + "/publish", Map.of("version", 9)), 409);
        expect(owner.request("POST", MANAGE + "/" + id + "/publish", Map.of("version", 0)), 200);
        expect(owner.request("POST", MANAGE + "/" + id + "/publish", Map.of("version", 1)), 409);
        body.put("version", 1); body.put("capacity", 60);
        expect(owner.request("PUT", MANAGE + "/" + id, body), 409);
        expect(admin.request("PUT", MANAGE + "/" + id, body), 409);
        expect(owner.request("POST", MANAGE + "/" + id + "/unpublish", Map.of("version", 1)), 200);
        body.put("version", 2);
        expect(owner.request("PUT", MANAGE + "/" + id, body), 200);
        expect(owner.request("PUT", MANAGE + "/" + id, body), 409);
        expect(owner.request("POST", MANAGE + "/" + id + "/publish", Map.of("version", 3)), 200);
        // PUBLISHED -> UNPUBLISHED -> PUBLISHED must not accept the original publication's version.
        expect(owner.request("POST", MANAGE + "/" + id + "/unpublish", Map.of("version", 1)), 409);
    }

    @Test void listsFilterPaginateAndNeverRevealUnpublishedContent() throws Exception {
        var owner = browser("organizer");
        var student = browser("student");
        String marker = java.util.UUID.randomUUID().toString();
        long category = jdbc.queryForObject("SELECT MIN(id) FROM activity_category", Long.class);
        for (int i = 0; i < 5; i++) {
            var body = input(); body.put("title", marker + " " + i);
            long id = create(owner, body).get("id").asLong();
            if (i != 0) expect(owner.request("POST", MANAGE + "/" + id + "/publish", Map.of("version", 0)), 200);
            if (i == 1) expect(owner.request("POST", MANAGE + "/" + id + "/unpublish", Map.of("version", 1)), 200);
        }
        String query = "?keyword=" + marker + "&categoryId=" + category + "&size=2";
        var first = expect(student.request("GET", PUBLIC + query, null), 200);
        var second = expect(student.request("GET", PUBLIC + query + "&page=2", null), 200);
        assertThat(first.get("total").asLong()).isEqualTo(3);
        assertThat(first.get("items").size()).isEqualTo(2);
        assertThat(second.get("items").size()).isEqualTo(1);
        assertThat(first.get("items").get(0).get("id").asLong()).isGreaterThan(first.get("items").get(1).get("id").asLong());
        assertThat(first.get("items").get(1).get("id").asLong()).isGreaterThan(second.get("items").get(0).get("id").asLong());
        for (var item : first.get("items")) {
            assertThat(item.has("content")).isFalse();
            assertThat(item.get("categoryName").asText()).isNotBlank();
            assertThat(item.get("status").asText()).isEqualTo("PUBLISHED");
        }
        assertThat(expect(owner.request("GET", MANAGE + "?keyword=" + marker + "&status=DRAFT", null), 200).get("total").asLong()).isEqualTo(1);
        assertThat(expect(owner.request("GET", MANAGE + "?keyword=" + marker + "&status=UNPUBLISHED", null), 200).get("total").asLong()).isEqualTo(1);
        assertThat(expect(student.request("GET", PUBLIC + query + "&page=3", null), 200).get("items").size()).isZero();
        assertThat(expect(student.request("GET", PUBLIC + query + "&status=PUBLISHED", null), 200).get("total").asLong()).isEqualTo(3);
        for (String status : List.of("DRAFT", "UNPUBLISHED", "UNKNOWN")) {
            expect(student.request("GET", PUBLIC + "?status=" + status, null), 400);
        }
        expect(student.request("GET", PUBLIC + "/" + created.get(0), null), 404);
        expect(student.request("GET", PUBLIC + "/" + created.get(1), null), 404);
        expect(student.request("GET", PUBLIC + "/9223372036854775807", null), 404);
    }

    @Test void keywordUsesLiteralWildcardsAndBoundParameters() throws Exception {
        var owner = browser("organizer");
        var body = input(); body.put("title", "literal%_!校园");
        long id = create(owner, body).get("id").asLong();
        expect(owner.request("POST", MANAGE + "/" + id + "/publish", Map.of("version", 0)), 200);
        assertThat(expect(owner.request("GET", PUBLIC + "?keyword=" + URLEncoder.encode("%_!", StandardCharsets.UTF_8), null), 200)
                .get("total").asLong()).isEqualTo(1);
        assertThat(expect(owner.request("GET", PUBLIC + "?keyword=" + URLEncoder.encode("' OR 1=1 --", StandardCharsets.UTF_8), null), 200)
                .get("total").asLong()).isZero();
    }

    @Test void invalidInputsAndQueryBoundsAre400() throws Exception {
        var owner = browser("organizer");
        Map<String, List<Object>> invalid = Map.ofEntries(
                Map.entry("title", List.of("", " ", "a".repeat(121))),
                Map.entry("summary", List.of("", "a".repeat(501))),
                Map.entry("content", List.of("", "a".repeat(20001))),
                Map.entry("location", List.of("", "a".repeat(201))),
                Map.entry("capacity", List.of(0, -1, 2147483648L, 1.5, "3")),
                Map.entry("categoryId", List.of(0, -1, Long.MAX_VALUE, 1.5)),
                Map.entry("startTime", List.of("invalid", "0999-01-01T00:00:00Z", "2030-01-01T00:00:00.001Z")));
        for (var field : invalid.entrySet()) {
            for (Object value : field.getValue()) {
                var body = input(); body.put(field.getKey(), value);
                expect(owner.request("POST", MANAGE, body), 400);
            }
        }
        for (String field : input().keySet()) {
            var body = input(); body.remove(field);
            expect(owner.request("POST", MANAGE, body), 400);
        }
        for (String query : List.of("page=0", "page=10001", "page=2147483648", "size=0", "size=101",
                "size=bad", "categoryId=-1", "status=bad", "keyword=" + "a".repeat(101))) {
            expect(owner.request("GET", MANAGE + "?" + query, null), 400);
        }
        for (String id : List.of("0", "-1", "bad", "9223372036854775808")) {
            expect(owner.request("GET", MANAGE + "/" + id, null), 400);
        }
        long id = create(owner, input()).get("id").asLong();
        for (Object v : List.of(-1, 0.5, "0", Long.MAX_VALUE)) {
            expect(owner.request("POST", MANAGE + "/" + id + "/publish", Map.of("version", v)), 400);
        }
        expect(owner.request("POST", MANAGE + "/" + id + "/publish", Map.of()), 400);
        expect(owner.request("PUT", MANAGE + "/" + id, input()), 400);
    }

    @Test void timeOrderingExpiryAndTimezoneRoundTrip() throws Exception {
        var owner = browser("organizer");
        for (String field : List.of("endTime", "registrationStart", "registrationEnd")) {
            var body = input();
            body.put(field, field.equals("registrationStart") ? body.get("registrationEnd")
                    : field.equals("registrationEnd") ? body.get("endTime") : body.get("startTime"));
            expect(owner.request("POST", MANAGE, body), 400);
        }
        var past = input();
        Instant base = Instant.now().truncatedTo(ChronoUnit.SECONDS).minus(2, ChronoUnit.DAYS);
        past.put("registrationStart", base.minus(2, ChronoUnit.DAYS).toString());
        past.put("registrationEnd", base.minus(1, ChronoUnit.DAYS).toString());
        past.put("startTime", base.toString()); past.put("endTime", base.plus(1, ChronoUnit.HOURS).toString());
        long id = create(owner, past).get("id").asLong();
        expect(owner.request("POST", MANAGE + "/" + id + "/publish", Map.of("version", 0)), 400);
        var future = input();
        Instant start = Instant.parse((String) future.get("startTime"));
        future.put("startTime", start.atOffset(java.time.ZoneOffset.ofHours(8)).toString());
        future.put("registrationEnd", future.get("startTime")); // End == start is legal.
        future.put("capacity", 1);
        future.put("title", "😀".repeat(120));
        var result = create(owner, future);
        assertThat(Instant.parse(result.get("startTime").asText())).isEqualTo(start);
        assertThat(result.get("title").asText()).isEqualTo("😀".repeat(120));
        expect(owner.request("POST", MANAGE + "/" + result.get("id").asLong() + "/publish", Map.of("version", 0)), 200);
        expect(owner.request("POST", MANAGE + "/" + result.get("id").asLong() + "/unpublish", Map.of("version", 1)), 200);
        past.put("version", 2);
        expect(owner.request("PUT", MANAGE + "/" + result.get("id").asLong(), past), 200);
        expect(owner.request("POST", MANAGE + "/" + result.get("id").asLong() + "/publish", Map.of("version", 3)), 400);
    }

    @Test void csrfAndLogoutProtectEveryMutation() throws Exception {
        var owner = browser("organizer");
        long id = create(owner, input()).get("id").asLong();
        var body = input(); body.put("version", 0);
        owner.csrf = "invalid";
        expect(owner.request("PUT", MANAGE + "/" + id, body), 403);
        expect(owner.request("POST", MANAGE + "/" + id + "/publish", Map.of("version", 0)), 403);
        expect(owner.request("POST", MANAGE + "/" + id + "/unpublish", Map.of("version", 0)), 403);
        owner.csrf();
        assertThat(owner.request("POST", "/api/v1/auth/logout", null).statusCode()).isEqualTo(204);
        expect(owner.request("PUT", MANAGE + "/" + id, body), 401);
        expect(owner.request("POST", MANAGE + "/" + id + "/publish", Map.of("version", 0)), 401);
        expect(owner.request("GET", MANAGE + "/" + id, null), 401);
    }

    @Test void expiredManagerSessionCannotCreateEditPublishOrUnpublish() throws Exception {
        var owner = browser("organizer");
        var body = input();
        long draft = create(owner, body).get("id").asLong();
        long published = create(owner, body).get("id").asLong();
        expect(owner.request("POST", MANAGE + "/" + published + "/publish", Map.of("version", 0)), 200);
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM activity", Long.class);
        String cookie = owner.cookies.getCookieStore().getCookies().stream()
                .filter(c -> c.getName().equals("JSESSIONID")).findFirst().orElseThrow().getValue();
        var server = (org.springframework.boot.tomcat.TomcatWebServer) serverContext.getWebServer();
        var context = (org.apache.catalina.Context) server.getTomcat().getHost().findChildren()[0];
        var session = context.getManager().findSession(cookie);
        assertThat(session.getMaxInactiveInterval()).isEqualTo(1800);
        session.setMaxInactiveInterval(1);
        Thread.sleep(1200);
        expect(owner.request("POST", MANAGE, body), 401);
        body.put("version", 0);
        expect(owner.request("PUT", MANAGE + "/" + draft, body), 401);
        expect(owner.request("POST", MANAGE + "/" + draft + "/publish", Map.of("version", 0)), 401);
        expect(owner.request("POST", MANAGE + "/" + published + "/unpublish", Map.of("version", 1)), 401);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM activity", Long.class)).isEqualTo(total);
        assertThat(jdbc.queryForObject("SELECT status FROM activity WHERE id=?", String.class, draft)).isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject("SELECT version FROM activity WHERE id=?", Long.class, draft)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM activity WHERE id=?", String.class, published)).isEqualTo("PUBLISHED");
        assertThat(jdbc.queryForObject("SELECT version FROM activity WHERE id=?", Long.class, published)).isEqualTo(1);
    }

    @Test void simultaneousTransitionsAndEditPublishHaveExactlyOneWinner() throws Exception {
        var first = browser("organizer"); var second = browser("organizer");
        for (boolean raceEdit : List.of(false, true)) {
            long id = create(first, input()).get("id").asLong();
            var body = input(); body.put("version", 0); body.put("title", "并发编辑");
            var ready = new java.util.concurrent.CountDownLatch(2);
            var start = new java.util.concurrent.CountDownLatch(1);
            try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
                var a = executor.submit(() -> { ready.countDown(); start.await(); return first.request("POST", MANAGE + "/" + id + "/publish", Map.of("version", 0)).statusCode(); });
                var b = executor.submit(() -> { ready.countDown(); start.await(); return raceEdit
                        ? second.request("PUT", MANAGE + "/" + id, body).statusCode()
                        : second.request("POST", MANAGE + "/" + id + "/publish", Map.of("version", 0)).statusCode(); });
                assertThat(ready.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue(); start.countDown();
                assertThat(List.of(a.get(10, java.util.concurrent.TimeUnit.SECONDS), b.get(10, java.util.concurrent.TimeUnit.SECONDS)))
                        .containsExactlyInAnyOrder(200, 409);
            }
            assertThat(expect(first.request("GET", MANAGE + "/" + id, null), 200).get("version").asLong()).isEqualTo(1);
        }
    }

    @Test void databaseConstraintsAndUtcStorageAreReal() throws Exception {
        var owner = browser("organizer");
        var input = input();
        long id = create(owner, input).get("id").asLong();
        assertThat(jdbc.queryForObject("SELECT DATE_FORMAT(start_time,'%Y-%m-%dT%H:%i:%sZ') FROM activity WHERE id=?", String.class, id))
                .isEqualTo(input.get("startTime"));
        for (String assignment : List.of("capacity=0", "status='INVALID'", "version=-1",
                "registration_start=registration_end", "end_time=start_time",
                "category_id=9223372036854775807", "creator_id=9223372036854775807")) {
            var failure = catchThrowableOfType(org.springframework.dao.DataAccessException.class,
                    () -> jdbc.update("UPDATE activity SET " + assignment + " WHERE id=?", id));
            assertThat(failure).isNotNull();
            assertThat(failure.getMostSpecificCause()).isInstanceOf(java.sql.SQLException.class);
            assertThat(((java.sql.SQLException) failure.getMostSpecificCause()).getErrorCode()).isIn(3819, 1452);
        }
        assertThat(jdbc.queryForObject("SELECT version FROM activity WHERE id=?", Long.class, id)).isZero();
    }

    @Test void jsonTextAndTimesMustHaveTheirDeclaredTypes() throws Exception {
        var owner = browser("organizer");
        long id = create(owner, input()).get("id").asLong();
        for (String field : List.of("title", "summary", "content", "location")) {
            for (Object value : List.of(123, true, List.of("text"))) {
                var body = input(); body.put(field, value);
                var response = owner.request("POST", MANAGE, body);
                if (response.statusCode() == 201) created.add(json.readTree(response.body()).get("id").asLong());
                expect(response, 400);
                body.put("version", 0);
                expect(owner.request("PUT", MANAGE + "/" + id, body), 400);
            }
        }
        for (String field : List.of("startTime", "endTime", "registrationStart", "registrationEnd")) {
            for (Object value : List.of(Instant.parse((String) input().get(field)).getEpochSecond(), "2030-01-01T00:00:00")) {
                var body = input(); body.put(field, value);
                expect(owner.request("POST", MANAGE, body), 400);
                body.put("version", 0);
                expect(owner.request("PUT", MANAGE + "/" + id, body), 400);
            }
        }
        assertThat(expect(owner.request("GET", MANAGE + "/" + id, null), 200).get("version").asLong()).isZero();
    }

    @Test void studentCannotReadOrMutateManagementDetails() throws Exception {
        var owner = browser("organizer"); var student = browser("student");
        long id = create(owner, input()).get("id").asLong();
        var body = input(); body.put("version", 0);
        expect(student.request("GET", MANAGE + "/" + id, null), 403);
        expect(student.request("PUT", MANAGE + "/" + id, body), 403);
        expect(student.request("POST", MANAGE + "/" + id + "/publish", Map.of("version", 0)), 403);
        expect(student.request("POST", MANAGE + "/" + id + "/unpublish", Map.of("version", 0)), 403);
        assertThat(expect(owner.request("GET", MANAGE + "/" + id, null), 200).get("version").asLong()).isZero();
    }

    @Test void publishRechecksDeadlineAfterWaitingForRowLock() throws Exception {
        var owner = browser("organizer");
        var body = input();
        Instant deadline = Instant.now().truncatedTo(ChronoUnit.SECONDS).plusSeconds(3);
        body.put("registrationStart", deadline.minusSeconds(20).toString());
        body.put("registrationEnd", deadline.toString());
        long id = create(owner, body).get("id").asLong();
        try (var connection = dataSource.getConnection();
                var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            connection.setAutoCommit(false);
            try (var lock = connection.prepareStatement("SELECT id FROM activity WHERE id=? FOR UPDATE")) {
                lock.setLong(1, id);
                try (var row = lock.executeQuery()) { assertThat(row.next()).isTrue(); }
            }
            var response = executor.submit(() -> owner.request("POST", MANAGE + "/" + id + "/publish", Map.of("version", 0)));
            try {
                assertThatThrownBy(() -> response.get(400, java.util.concurrent.TimeUnit.MILLISECONDS))
                        .isInstanceOf(java.util.concurrent.TimeoutException.class);
                long wait = java.time.Duration.between(Instant.now(), deadline.plusMillis(200)).toMillis();
                if (wait > 0) Thread.sleep(wait);
            } finally { connection.rollback(); }
            expect(response.get(10, java.util.concurrent.TimeUnit.SECONDS), 400);
        }
        assertThat(expect(owner.request("GET", MANAGE + "/" + id, null), 200).get("status").asText()).isEqualTo("DRAFT");
    }

    @Test void supportedDateExtremesRoundTripWithoutCalendarDrift() throws Exception {
        var owner = browser("organizer");
        for (String date : List.of("1000-01-01", "1582-10-04", "1582-10-15", "9999-12-31")) {
            var body = input();
            body.put("registrationStart", date + "T00:00:00Z");
            body.put("registrationEnd", date + "T01:00:00Z");
            body.put("startTime", date + "T02:00:00Z");
            body.put("endTime", date + "T03:00:00Z");
            var result = create(owner, body);
            assertThat(result.get("startTime").asText()).isEqualTo(body.get("startTime"));
            assertThat(jdbc.queryForObject("SELECT DATE_FORMAT(start_time,'%Y-%m-%dT%H:%i:%sZ') FROM activity WHERE id=?",
                    String.class, result.get("id").asLong())).isEqualTo(body.get("startTime"));
        }
    }
}
