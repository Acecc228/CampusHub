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
        properties = {"campushub.auth.demo-enabled=true", "spring.datasource.hikari.maximum-pool-size=12"})
@ActiveProfiles("test")
@org.springframework.context.annotation.Import(RegistrationIT.BarrierConfiguration.class)
class RegistrationIT extends MySqlIntegrationSupport {
    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    static class BarrierConfiguration {
        @org.springframework.context.annotation.Bean
        RegistrationInsertBarrier registrationInsertBarrier() { return new RegistrationInsertBarrier(); }
    }
    @Autowired RegistrationInsertBarrier insertBarrier;
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired javax.sql.DataSource dataSource;
    @Autowired com.campushub.registration.RegistrationService registrations;
    @Autowired com.campushub.auth.IdentityService identities;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Autowired org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext serverContext;
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
        value.put("title", "M1.3 " + java.util.UUID.randomUUID());
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
        return result;
    }

    @Test void registerCancelRetryAndRejoin() throws Exception {
        var owner = browser("organizer"); var student = browser("student");
        var body = input(); body.put("registrationStart", Instant.now().minusSeconds(60).truncatedTo(ChronoUnit.SECONDS).toString());
        long id = create(owner, body).get("id").asLong();
        expect(owner.request("POST", MANAGE + "/" + id + "/publish", Map.of("version", 0)), 200);
        String path = PUBLIC + "/" + id + "/registrations";
        var registered = expect(student.request("POST", path, Map.of("version", 0)), 200);
        assertThat(registered.get("status").asText()).isEqualTo("CONFIRMED");
        expect(student.request("POST", path, Map.of("version", 0)), 200);
        expect(student.request("POST", path + "/cancel", Map.of("version", 1)), 200);
        expect(student.request("POST", path + "/cancel", Map.of("version", 1)), 200);
        expect(student.request("POST", path, Map.of("version", 2)), 200);
        expect(student.request("POST", path + "/cancel", Map.of("version", 1)), 409);
        var detail = expect(student.request("GET", PUBLIC + "/" + id, null), 200);
        assertThat(detail.get("registeredCount").asInt()).isEqualTo(1);
        assertThat(detail.get("remainingCapacity").asInt()).isEqualTo(29);
        assertThat(detail.get("myRegistration").get("version").asLong()).isEqualTo(3);
        consistent(id, 1);
    }

    long activity(int capacity) throws Exception {
        var owner = browser("organizer");
        var body = input(); body.put("capacity", capacity);
        body.put("registrationStart", Instant.now().minusSeconds(60).truncatedTo(ChronoUnit.SECONDS).toString());
        long id = create(owner, body).get("id").asLong();
        expect(owner.request("POST", MANAGE + "/" + id + "/publish", Map.of("version", 0)), 200);
        return id;
    }

    com.campushub.auth.CampusPrincipal newActor(String role) {
        String name = "m13_" + java.util.UUID.randomUUID().toString().replace("-", "");
        jdbc.update("""
                INSERT INTO app_user(login_name,display_name,password_hash,enabled,demo_account)
                SELECT ?, 'M1.3 verification',password_hash,1,1 FROM app_user WHERE login_name=?
                """, name, "demo_" + role);
        long userId = jdbc.queryForObject("SELECT id FROM app_user WHERE login_name=?", Long.class, name);
        jdbc.update("INSERT INTO app_user_role(user_id,role_id) SELECT ?,id FROM app_role WHERE code=?", userId, role.toUpperCase());
        return identities.loadDemoAccount(name);
    }

    void consistent(long id, int expected) {
        var state = jdbc.queryForMap("""
                SELECT a.registered_count,a.capacity,
                  (SELECT COUNT(*) FROM activity_registration r WHERE r.activity_id=a.id AND r.status='CONFIRMED') AS actual,
                  (SELECT COUNT(*)-COUNT(DISTINCT user_id) FROM activity_registration r WHERE r.activity_id=a.id) AS duplicates
                FROM activity a WHERE a.id=?
                """, id);
        assertThat(((Number) state.get("registered_count")).intValue()).isEqualTo(expected);
        assertThat(((Number) state.get("actual")).intValue()).isEqualTo(expected);
        assertThat(expected).isBetween(0, ((Number) state.get("capacity")).intValue());
        assertThat(((Number) state.get("duplicates")).intValue()).isZero();
    }

    // Transactions acquire separate physical MySQL sessions before the synchronized release.
    List<String> race(List<java.util.concurrent.Callable<String>> actions) throws Exception {
        var ready = new java.util.concurrent.CountDownLatch(actions.size());
        var start = new java.util.concurrent.CountDownLatch(1);
        var connectionIds = java.util.concurrent.ConcurrentHashMap.<Long>newKeySet();
        try (var pool = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new ArrayList<java.util.concurrent.Future<String>>();
            for (var action : actions) {
                futures.add(pool.submit(() -> {
                    try {
                        var transaction = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
                        transaction.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
                        return transaction.execute(tx -> {
                            connectionIds.add(jdbc.queryForObject("SELECT CONNECTION_ID()", Long.class));
                            ready.countDown();
                            try {
                                if (!start.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("Start timed out");
                                return action.call();
                            } catch (RuntimeException ex) { throw ex; }
                            catch (Exception ex) { throw new IllegalStateException(ex); }
                        });
                    } catch (com.campushub.error.BusinessException ex) { return ex.code(); }
                }));
            }
            try {
                assertThat(ready.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                assertThat(connectionIds).hasSize(actions.size());
            } finally { start.countDown(); }
            var results = new ArrayList<String>();
            for (var future : futures) results.add(future.get(15, java.util.concurrent.TimeUnit.SECONDS));
            return results;
        }
    }

    @org.junit.jupiter.api.RepeatedTest(3)
    void lastSeatHasOneWinnerAcrossIndependentConnections() throws Exception {
        long id = activity(1);
        var actions = new ArrayList<java.util.concurrent.Callable<String>>();
        for (int i = 0; i < 8; i++) {
            var actor = newActor("student");
            actions.add(() -> registrations.change(id, 0L, false, actor).status());
        }
        var result = race(actions);
        assertThat(result.stream().filter("CONFIRMED"::equals).count()).isEqualTo(1);
        assertThat(result.stream().filter("ACTIVITY_FULL"::equals).count()).isEqualTo(7);
        consistent(id, 1);
    }

    @org.junit.jupiter.api.RepeatedTest(3)
    void differentActivitiesFirstRegistrationsDoNotDeadlockOnMissingKeys() throws Exception {
        long firstId = activity(1), secondId = activity(1);
        var first = newActor("student"); var second = newActor("student");
        var gate = new RegistrationInsertBarrier.Gate(firstId, secondId);
        insertBarrier.gate = gate;
        try (var pool = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            // No outer test transaction: use the service's actual Spring transaction boundary.
            var a = pool.submit(() -> registrations.change(firstId, 0L, false, first));
            var b = pool.submit(() -> registrations.change(secondId, 0L, false, second));
            assertThat(a.get(15, java.util.concurrent.TimeUnit.SECONDS).status()).isEqualTo("CONFIRMED");
            assertThat(b.get(15, java.util.concurrent.TimeUnit.SECONDS).status()).isEqualTo("CONFIRMED");
            assertThat(gate.connections).hasSize(2);
            assertThat(gate.isolationLevels).containsExactly("READ-COMMITTED");
        } finally { insertBarrier.gate = null; }
        consistent(firstId, 1); consistent(secondId, 1);
    }

    @Test void concurrentAuthenticatedHttpRequestsCompeteForLastSeat() throws Exception {
        long id = activity(1);
        var a = newActor("student"); var b = newActor("student");
        var first = new Browser(); first.loginAs(a.identity().loginName(), "student");
        var second = new Browser(); second.loginAs(b.identity().loginName(), "student");
        var ready = new java.util.concurrent.CountDownLatch(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var pool = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var requests = new ArrayList<java.util.concurrent.Future<Integer>>();
            for (var browser : List.of(first, second)) {
                requests.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("Start timed out");
                    return browser.request("POST", PUBLIC + "/" + id + "/registrations", Map.of("version", 0)).statusCode();
                }));
            }
            try { assertThat(ready.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue(); }
            finally { start.countDown(); }
            assertThat(List.of(requests.get(0).get(10, java.util.concurrent.TimeUnit.SECONDS),
                    requests.get(1).get(10, java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(200, 409);
        }
        consistent(id, 1);
    }

    @Test void personalHistoryAndRosterPaginateCancelledAndConfirmedRecords() throws Exception {
        var actor = newActor("student");
        var student = new Browser(); student.loginAs(actor.identity().loginName(), "student");
        long firstId = activity(2), secondId = activity(2), thirdId = activity(2);
        for (long id : List.of(firstId, secondId, thirdId)) registrations.change(id, 0L, false, actor);
        registrations.change(firstId, 1L, true, actor);
        var firstPage = expect(student.request("GET", "/api/v1/student/registrations?size=2", null), 200);
        var secondPage = expect(student.request("GET", "/api/v1/student/registrations?size=2&page=2", null), 200);
        assertThat(firstPage.get("total").asInt()).isEqualTo(3);
        assertThat(firstPage.get("items").size()).isEqualTo(2);
        assertThat(secondPage.get("items").size()).isEqualTo(1);
        assertThat(secondPage.get("items").get(0).get("status").asText()).isEqualTo("CANCELLED");
        assertThat(firstPage.get("items").get(0).get("activityId").asLong()).isEqualTo(thirdId);
        var other = newActor("student"); registrations.change(firstId, 0L, false, other);
        var manager = browser("organizer");
        var roster1 = expect(manager.request("GET", MANAGE + "/" + firstId + "/registrations?size=1", null), 200);
        var roster2 = expect(manager.request("GET", MANAGE + "/" + firstId + "/registrations?size=1&page=2", null), 200);
        assertThat(roster1.get("total").asInt()).isEqualTo(2);
        assertThat(roster1.get("items").get(0).get("status").asText()).isEqualTo("CONFIRMED");
        assertThat(roster2.get("items").get(0).get("status").asText()).isEqualTo("CANCELLED");
        consistent(firstId, 1);
    }

    @Test void manyStudentsNeverExceedCapacity() throws Exception {
        long id = activity(3);
        var actions = new ArrayList<java.util.concurrent.Callable<String>>();
        for (int i = 0; i < 8; i++) {
            var actor = newActor("student");
            actions.add(() -> registrations.change(id, 0L, false, actor).status());
        }
        var result = race(actions);
        assertThat(result.stream().filter("CONFIRMED"::equals).count()).isEqualTo(3);
        assertThat(result.stream().filter("ACTIVITY_FULL"::equals).count()).isEqualTo(5);
        consistent(id, 3);
    }

    @Test void sameStudentConcurrentRetriesUseOneRecordAndOneSeat() throws Exception {
        long id = activity(2);
        var actor = newActor("student");
        var actions = new ArrayList<java.util.concurrent.Callable<String>>();
        for (int i = 0; i < 6; i++) actions.add(() -> registrations.change(id, 0L, false, actor).status());
        assertThat(race(actions)).containsOnly("CONFIRMED");
        consistent(id, 1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM activity_registration WHERE activity_id=?", Integer.class, id)).isEqualTo(1);
        actions.clear();
        for (int i = 0; i < 6; i++) actions.add(() -> registrations.change(id, 1L, true, actor).status());
        assertThat(race(actions)).containsOnly("CANCELLED");
        consistent(id, 0);
        assertThat(registrations.mine(id, actor).version()).isEqualTo(2);
    }

    @Test void registrationAndCancellationRacePreservesCountsAndRejectsOldLifecycle() throws Exception {
        long id = activity(1);
        var actor = newActor("student");
        registrations.change(id, 0L, false, actor);
        var result = race(List.of(
                () -> registrations.change(id, 1L, true, actor).status(),
                () -> registrations.change(id, 0L, false, actor).status()));
        assertThat(result).contains("CANCELLED");
        assertThat(result).allMatch(s -> List.of("CONFIRMED", "CANCELLED", "REGISTRATION_CONFLICT").contains(s));
        consistent(id, 0);
        registrations.change(id, 2L, false, actor);
        assertThatThrownBy(() -> registrations.change(id, 1L, true, actor))
                .isInstanceOf(com.campushub.error.BusinessException.class);
        consistent(id, 1);
    }

    @Test void cancelledStudentMustRecheckWindowPublicationAndRemainingSeats() throws Exception {
        long id = activity(1);
        var actor = newActor("student"); var other = newActor("student");
        registrations.change(id, 0L, false, actor);
        registrations.change(id, 1L, true, actor);
        registrations.change(id, 0L, false, other);
        assertThatThrownBy(() -> registrations.change(id, 2L, false, actor))
                .isInstanceOf(com.campushub.error.BusinessException.class).hasMessage("活动名额已满");
        registrations.change(id, 1L, true, other);
        jdbc.update("UPDATE activity SET registration_end=UTC_TIMESTAMP()-INTERVAL 1 SECOND WHERE id=?", id);
        assertThatThrownBy(() -> registrations.change(id, 2L, false, actor))
                .isInstanceOf(com.campushub.error.BusinessException.class).hasMessage("报名已截止");
        jdbc.update("UPDATE activity SET registration_start=UTC_TIMESTAMP()+INTERVAL 1 HOUR,registration_end=UTC_TIMESTAMP()+INTERVAL 2 HOUR WHERE id=?", id);
        assertThatThrownBy(() -> registrations.change(id, 2L, false, actor))
                .isInstanceOf(com.campushub.error.BusinessException.class).hasMessage("报名尚未开始");
        jdbc.update("UPDATE activity SET status='UNPUBLISHED' WHERE id=?", id);
        assertThatThrownBy(() -> registrations.change(id, 2L, false, actor))
                .isInstanceOf(com.campushub.error.BusinessException.class).hasMessage("活动未发布");
        consistent(id, 0);
    }

    @Test void publicWindowAndInvalidRequestsReturnBusinessErrors() throws Exception {
        var owner = browser("organizer"); var student = browser("student");
        long id = create(owner, input()).get("id").asLong();
        String path = PUBLIC + "/" + id + "/registrations";
        expect(student.request("POST", path, Map.of("version", 0)), 404);
        expect(owner.request("POST", MANAGE + "/" + id + "/publish", Map.of("version", 0)), 200);
        assertThat(expect(student.request("POST", path, Map.of("version", 0)), 409).get("code").asText()).isEqualTo("REGISTRATION_NOT_OPEN");
        jdbc.update("UPDATE activity SET registration_start=UTC_TIMESTAMP()-INTERVAL 2 HOUR,registration_end=UTC_TIMESTAMP()-INTERVAL 1 HOUR WHERE id=?", id);
        assertThat(expect(student.request("POST", path, Map.of("version", 0)), 409).get("code").asText()).isEqualTo("REGISTRATION_CLOSED");
        for (Object version : List.of(-1, 1.5, "0", Long.MAX_VALUE)) {
            expect(student.request("POST", path, Map.of("version", version)), 400);
        }
        expect(student.request("POST", path, Map.of()), 400);
        expect(student.request("POST", PUBLIC + "/-1/registrations", Map.of("version", 0)), 400);
        consistent(id, 0);
    }

    @Test void unpublishRepublishAndCapacityEditingPreserveExistingRegistrations() throws Exception {
        long id = activity(2);
        var actor = newActor("student"); var second = newActor("student");
        registrations.change(id, 0L, false, actor);
        registrations.change(id, 0L, false, second);
        var owner = browser("organizer");
        expect(owner.request("POST", MANAGE + "/" + id + "/unpublish", Map.of("version", 1)), 200);
        var body = input(); body.put("version", 2); body.put("capacity", 1);
        expect(owner.request("PUT", MANAGE + "/" + id, body), 409);
        registrations.change(id, 1L, true, second); // Cancellation allowed while unpublished.
        expect(owner.request("PUT", MANAGE + "/" + id, body), 200);
        expect(owner.request("POST", MANAGE + "/" + id + "/publish", Map.of("version", 3)), 200);
        consistent(id, 1);
        assertThat(registrations.mine(id, actor).status()).isEqualTo("CONFIRMED");
        assertThat(registrations.mine(id, second).status()).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT version FROM activity WHERE id=?", Long.class, id)).isEqualTo(4);
    }

    @Test void registrationAndUnpublishSerializeOnActivityRow() throws Exception {
        long id = activity(1); var actor = newActor("student");
        var manager = identities.loadDemoAccount("demo_organizer");
        var result = race(List.of(
                () -> registrations.change(id, 0L, false, actor).status(),
                () -> { activityService.transition(id, 1L, false, manager); return "UNPUBLISHED"; }));
        assertThat(result).contains("UNPUBLISHED");
        assertThat(result).allMatch(s -> List.of("CONFIRMED", "NOT_FOUND", "UNPUBLISHED").contains(s));
        int count = result.contains("CONFIRMED") ? 1 : 0;
        consistent(id, count);
        activityService.transition(id, 2L, true, manager);
        consistent(id, count);
    }

    @Autowired com.campushub.activity.ActivityService activityService;

    @Test void cancellationAndCapacityEditUseTheSameLockOrder() throws Exception {
        long id = activity(2);
        var first = newActor("student"); var second = newActor("student");
        registrations.change(id, 0L, false, first); registrations.change(id, 0L, false, second);
        var manager = identities.loadDemoAccount("demo_organizer");
        var current = activityService.transition(id, 1L, false, manager);
        var input = new com.campushub.activity.ActivityInput(current.title(), current.summary(), current.content(),
                current.categoryId(), current.location(), current.startTime(), current.endTime(),
                current.registrationStart(), current.registrationEnd(), 1L, current.version());
        var result = race(List.of(
                () -> registrations.change(id, 1L, true, second).status(),
                () -> { activityService.edit(id, input, manager); return "EDITED"; }));
        assertThat(result).contains("CANCELLED");
        assertThat(result).allMatch(s -> List.of("CANCELLED", "EDITED", "CAPACITY_BELOW_REGISTERED").contains(s));
        consistent(id, 1);
    }

    @Test void databaseFailureAfterSeatUpdateRollsBackBothInsertAndCancellation() throws Exception {
        long id = activity(2);
        // Real FK violation occurs after occupy() but before a registration is created.
        var impossible = new com.campushub.auth.CampusPrincipal(new com.campushub.auth.IdentityMapper.Account(
                Long.MAX_VALUE, "nonexistent", "fault injection", "unused", true, true), List.of("STUDENT"));
        assertThatThrownBy(() -> registrations.change(id, 0L, false, impossible))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        consistent(id, 0);
        var actor = newActor("student");
        var transaction = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        transaction.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
        assertThatThrownBy(() -> transaction.execute(tx -> {
            registrations.change(id, 0L, false, actor);
            jdbc.update("UPDATE activity SET registered_count=-1 WHERE id=?", id);
            return null;
        })).isInstanceOf(org.springframework.jdbc.UncategorizedSQLException.class)
                .satisfies(ex -> assertThat(((java.sql.SQLException) ex.getCause()).getErrorCode()).isEqualTo(3819));
        consistent(id, 0);
        assertThat(registrations.mine(id, actor).status()).isEqualTo("NONE");
        registrations.change(id, 0L, false, actor);
        assertThatThrownBy(() -> transaction.execute(tx -> {
            registrations.change(id, 1L, true, actor);
            jdbc.update("UPDATE activity_registration SET status='INVALID' WHERE activity_id=?", id);
            return null;
        })).isInstanceOf(org.springframework.jdbc.UncategorizedSQLException.class)
                .satisfies(ex -> assertThat(((java.sql.SQLException) ex.getCause()).getErrorCode()).isEqualTo(3819));
        consistent(id, 1);
        assertThat(registrations.mine(id, actor).version()).isEqualTo(1);
    }

    @Test void uniqueAndCounterConstraintsRejectInvalidDatabaseStates() throws Exception {
        long id = activity(1); var actor = newActor("student");
        registrations.change(id, 0L, false, actor);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO activity_registration(activity_id,user_id,status,version) VALUES(?,?,'CONFIRMED',1)", id, actor.identity().id()))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE activity SET registered_count=2 WHERE id=?", id))
                .isInstanceOf(org.springframework.jdbc.UncategorizedSQLException.class)
                .satisfies(ex -> assertThat(((java.sql.SQLException) ex.getCause()).getErrorCode()).isEqualTo(3819));
        assertThatThrownBy(() -> jdbc.update("UPDATE activity SET registered_count=-1 WHERE id=?", id))
                .isInstanceOf(org.springframework.jdbc.UncategorizedSQLException.class)
                .satisfies(ex -> assertThat(((java.sql.SQLException) ex.getCause()).getErrorCode()).isEqualTo(3819));
        consistent(id, 1);
    }

    @Test void rolesCsrfOwnershipAndCallerIdentityAreEnforced() throws Exception {
        long id = activity(2);
        String path = PUBLIC + "/" + id + "/registrations";
        var anonymous = browser(null);
        expect(anonymous.request("POST", path, Map.of("version", 0)), 401);
        expect(anonymous.request("POST", path + "/cancel", Map.of("version", 0)), 401);
        expect(anonymous.request("GET", PUBLIC + "/" + id + "/registration", null), 401);
        expect(anonymous.request("GET", "/api/v1/student/registrations", null), 401);
        for (String role : List.of("organizer", "admin")) {
            var disallowed = browser(role);
            expect(disallowed.request("POST", path, Map.of("version", 0)), 403);
            expect(disallowed.request("POST", path + "/cancel", Map.of("version", 0)), 403);
            expect(disallowed.request("GET", "/api/v1/student/registrations", null), 403);
        }
        var student = browser("student");
        student.csrf = null;
        expect(student.request("POST", path, Map.of("version", 0)), 403);
        student.csrf();
        var other = newActor("student");
        var registered = expect(student.request("POST", path, Map.of("version", 0, "userId", other.identity().id(), "role", "ADMIN")), 200);
        assertThat(registered.get("userId").asLong()).isEqualTo(identities.loadDemoAccount("demo_student").identity().id());
        var another = new Browser(); another.loginAs(other.identity().loginName(), "student");
        expect(another.request("POST", path + "/cancel", Map.of("version", 0, "userId", registered.get("userId").asLong())), 200);
        consistent(id, 1);
        expect(student.request("GET", MANAGE + "/" + id + "/registrations", null), 403);
        var otherManager = newActor("organizer");
        var stranger = new Browser(); stranger.loginAs(otherManager.identity().loginName(), "organizer");
        expect(stranger.request("GET", MANAGE + "/" + id + "/registrations", null), 403);
        for (String role : List.of("organizer", "admin")) {
            var allowed = browser(role);
            var roster = expect(allowed.request("GET", MANAGE + "/" + id + "/registrations", null), 200);
            assertThat(roster.get("total").asInt()).isEqualTo(1);
            assertThat(roster.toString()).doesNotContain("password", "loginName", "content", "displayName");
        }
        var mine = expect(another.request("GET", "/api/v1/student/registrations?userId=" + registered.get("userId").asLong(), null), 200);
        assertThat(mine.get("total").asInt()).isZero();
        for (String query : List.of("size=101", "size=0", "page=10001", "page=-1", "page=x")) {
            expect(student.request("GET", "/api/v1/student/registrations?" + query, null), 400);
            expect(browser("organizer").request("GET", MANAGE + "/" + id + "/registrations?" + query, null), 400);
        }
    }

    @Test void ownHistorySurvivesUnpublishWithoutLeakingPrivateActivityContent() throws Exception {
        long id = activity(2);
        var student = browser("student"); var owner = browser("organizer");
        expect(student.request("POST", PUBLIC + "/" + id + "/registrations", Map.of("version", 0)), 200);
        expect(owner.request("POST", MANAGE + "/" + id + "/unpublish", Map.of("version", 1)), 200);
        expect(student.request("GET", PUBLIC + "/" + id, null), 404);
        assertThat(expect(student.request("GET", PUBLIC + "/" + id + "/registration", null), 200).get("status").asText()).isEqualTo("CONFIRMED");
        var other = newActor("student");
        var stranger = new Browser(); stranger.loginAs(other.identity().loginName(), "student");
        expect(stranger.request("GET", PUBLIC + "/" + id + "/registration", null), 404);
        var mine = expect(student.request("GET", "/api/v1/student/registrations?size=100", null), 200);
        assertThat(mine.toString()).doesNotContain("content", "summary", "location", "password");
        assertThat(mine.get("items").isArray()).isTrue();
        consistent(id, 1);
    }

    @Test void cancellationAfterStartIsRejectedAndLockWaitRechecksDeadline() throws Exception {
        long id = activity(1); var actor = newActor("student");
        registrations.change(id, 0L, false, actor);
        jdbc.update("""
                UPDATE activity SET registration_start=UTC_TIMESTAMP()-INTERVAL 3 HOUR,
                registration_end=UTC_TIMESTAMP()-INTERVAL 2 HOUR,start_time=UTC_TIMESTAMP()-INTERVAL 1 HOUR WHERE id=?
                """, id);
        assertThatThrownBy(() -> registrations.change(id, 1L, true, actor))
                .isInstanceOf(com.campushub.error.BusinessException.class).hasMessage("活动已开始，不能取消");
        consistent(id, 1);
        long waitingId = activity(1);
        Instant deadline = Instant.now().truncatedTo(ChronoUnit.SECONDS).plusSeconds(3);
        jdbc.update("UPDATE activity SET registration_end=? WHERE id=?", java.sql.Timestamp.from(deadline), waitingId);
        try (var connection = dataSource.getConnection();
                var pool = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            connection.setAutoCommit(false);
            try (var lock = connection.prepareStatement("SELECT id FROM activity WHERE id=? FOR UPDATE")) {
                lock.setLong(1, waitingId); try (var rs = lock.executeQuery()) { assertThat(rs.next()).isTrue(); }
            }
            var future = pool.submit(() -> registrations.change(waitingId, 0L, false, actor));
            try {
                assertThatThrownBy(() -> future.get(300, java.util.concurrent.TimeUnit.MILLISECONDS))
                        .isInstanceOf(java.util.concurrent.TimeoutException.class);
                long wait = java.time.Duration.between(Instant.now(), deadline.plusMillis(200)).toMillis();
                if (wait > 0) Thread.sleep(wait);
            } finally { connection.rollback(); }
            assertThatThrownBy(() -> future.get(10, java.util.concurrent.TimeUnit.SECONDS))
                    .hasCauseInstanceOf(com.campushub.error.BusinessException.class)
                    .hasRootCauseMessage("报名已截止");
        }
        consistent(waitingId, 0);
    }

    @Test void invalidAndOtherSessionCsrfCannotRegisterOrCancel() throws Exception {
        long id = activity(2);
        var student = browser("student"); var other = browser("student");
        String valid = student.csrf;
        String path = PUBLIC + "/" + id + "/registrations";
        for (String token : List.of("invalid", other.csrf)) {
            student.csrf = token;
            expect(student.request("POST", path, Map.of("version", 0)), 403);
            consistent(id, 0);
        }
        student.csrf = valid;
        expect(student.request("POST", path, Map.of("version", 0)), 200);
        for (String token : List.of("invalid", other.csrf)) {
            student.csrf = token;
            expect(student.request("POST", path + "/cancel", Map.of("version", 1)), 403);
            consistent(id, 1);
        }
        student.csrf = valid;
        expect(student.request("POST", path + "/cancel", Map.of("version", 1)), 200);
        consistent(id, 0);
    }

    @Test void logoutAndExpiredCookiesCannotMutateAndDoNotRevokeOtherSession() throws Exception {
        for (boolean expire : List.of(false, true)) {
            long id = activity(2);
            var student = browser("student"); var survivor = browser("student");
            String path = PUBLIC + "/" + id + "/registrations";
            expect(student.request("POST", path, Map.of("version", 0)), 200);
            String cookie = student.cookies.getCookieStore().getCookies().stream()
                    .filter(c -> c.getName().equals("JSESSIONID")).findFirst().orElseThrow().getValue();
            String token = student.csrf;
            if (expire) {
                var server = (org.springframework.boot.tomcat.TomcatWebServer) serverContext.getWebServer();
                var context = (org.apache.catalina.Context) server.getTomcat().getHost().findChildren()[0];
                var session = context.getManager().findSession(cookie);
                assertThat(session.getMaxInactiveInterval()).isEqualTo(1800);
                session.setMaxInactiveInterval(1);
                Thread.sleep(1200);
            } else {
                assertThat(student.request("POST", "/api/v1/auth/logout", null).statusCode()).isEqualTo(204);
            }
            // Replay the actual old cookie and CSRF, not just the browser's emptied cookie jar.
            for (String suffix : List.of("", "/cancel")) {
                var replay = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path + suffix))
                        .header("Cookie", "JSESSIONID=" + cookie).header("X-CSRF-TOKEN", token)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"version\":1}")).build();
                expect(HttpClient.newHttpClient().send(replay, HttpResponse.BodyHandlers.ofString()), 401);
                consistent(id, 1);
            }
            expect(survivor.request("GET", "/api/v1/auth/me", null), 200);
            expect(survivor.request("POST", path + "/cancel", Map.of("version", 1)), 200);
            consistent(id, 0);
        }
    }

    @Test void unpublishedRejectionLeavesNoRecordAndRepublishAllowsFreshRegistration() throws Exception {
        long id = activity(2);
        var student = browser("student"); var owner = browser("organizer");
        String path = PUBLIC + "/" + id;
        expect(owner.request("POST", MANAGE + "/" + id + "/unpublish", Map.of("version", 1)), 200);
        assertThat(expect(student.request("POST", path + "/registrations", Map.of("version", 0)), 404)
                .get("code").asText()).isEqualTo("NOT_FOUND");
        expect(student.request("GET", path + "/registration", null), 404);
        expect(student.request("GET", path, null), 404);
        consistent(id, 0);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM activity_registration WHERE activity_id=?", Integer.class, id)).isZero();
        expect(owner.request("POST", MANAGE + "/" + id + "/publish", Map.of("version", 2)), 200);
        expect(student.request("GET", path, null), 200);
        expect(student.request("POST", path + "/registrations", Map.of("version", 0)), 200);
        consistent(id, 1);
    }

    @Test void unpublishAndRegistrationRespectBothControlledCommitOrders() throws Exception {
        for (boolean registrationFirst : List.of(false, true)) {
            long id = activity(1); var actor = newActor("student");
            var manager = identities.loadDemoAccount("demo_organizer");
            var locked = new java.util.concurrent.CountDownLatch(1);
            var release = new java.util.concurrent.CountDownLatch(1);
            var started = new java.util.concurrent.CountDownLatch(1);
            var connections = java.util.concurrent.ConcurrentHashMap.<Long>newKeySet();
            try (var pool = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
                var first = pool.submit(() -> {
                    var tx = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
                    tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
                    return tx.execute(status -> {
                        connections.add(jdbc.queryForObject("SELECT CONNECTION_ID()", Long.class));
                        if (registrationFirst) registrations.change(id, 0L, false, actor);
                        else activityService.transition(id, 1L, false, manager);
                        locked.countDown();
                        try { assertThat(release.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue(); }
                        catch (InterruptedException ex) { throw new IllegalStateException(ex); }
                        return true;
                    });
                });
                try {
                    assertThat(locked.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                    var second = pool.submit(() -> {
                        var tx = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
                        tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
                        try {
                            return tx.execute(status -> {
                                connections.add(jdbc.queryForObject("SELECT CONNECTION_ID()", Long.class));
                                started.countDown();
                                if (registrationFirst) { activityService.transition(id, 1L, false, manager); return "UNPUBLISHED"; }
                                return registrations.change(id, 0L, false, actor).status();
                            });
                        } catch (com.campushub.error.BusinessException ex) { return ex.code(); }
                    });
                    assertThat(started.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                    assertThat(connections).hasSize(2);
                    assertThatThrownBy(() -> second.get(300, java.util.concurrent.TimeUnit.MILLISECONDS))
                            .isInstanceOf(java.util.concurrent.TimeoutException.class);
                    release.countDown();
                    assertThat(first.get(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                    assertThat(second.get(10, java.util.concurrent.TimeUnit.SECONDS))
                            .isEqualTo(registrationFirst ? "UNPUBLISHED" : "NOT_FOUND");
                } finally { release.countDown(); }
            }
            consistent(id, registrationFirst ? 1 : 0);
            activityService.transition(id, 2L, true, manager);
            consistent(id, registrationFirst ? 1 : 0);
            assertThat(registrations.mine(id, actor).status()).isEqualTo(registrationFirst ? "CONFIRMED" : "NONE");
        }
    }

    @Test void realLockTimeoutRollsBackAndConnectionCanRetry() throws Exception {
        long id = activity(1); var actor = newActor("student");
        try (var blocker = dataSource.getConnection();
                var pool = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            blocker.setAutoCommit(false);
            try (var sql = blocker.prepareStatement("SELECT id FROM activity WHERE id=? FOR UPDATE")) {
                sql.setLong(1, id);
                try (var row = sql.executeQuery()) { assertThat(row.next()).isTrue(); }
            }
            long blockerId;
            try (var sql = blocker.createStatement(); var row = sql.executeQuery("SELECT CONNECTION_ID()")) {
                assertThat(row.next()).isTrue(); blockerId = row.getLong(1);
            }
            try {
                var waiter = pool.submit(() -> {
                    var tx = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
                    tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
                    return tx.execute(status -> {
                        assertThat(jdbc.queryForObject("SELECT CONNECTION_ID()", Long.class)).isNotEqualTo(blockerId);
                        int original = jdbc.queryForObject("SELECT @@SESSION.innodb_lock_wait_timeout", Integer.class);
                        jdbc.execute("SET SESSION innodb_lock_wait_timeout=1");
                        try { return registrations.change(id, 0L, false, actor); }
                        finally { jdbc.execute("SET SESSION innodb_lock_wait_timeout=" + original); }
                    });
                });
                assertThatThrownBy(() -> waiter.get(10, java.util.concurrent.TimeUnit.SECONDS))
                        .hasCauseInstanceOf(org.springframework.dao.DataAccessException.class)
                        .satisfies(ex -> {
                            Throwable cause = ex;
                            while (cause != null && !(cause instanceof java.sql.SQLException)) cause = cause.getCause();
                            assertThat(cause).isInstanceOf(java.sql.SQLException.class);
                            assertThat(((java.sql.SQLException) cause).getErrorCode()).isEqualTo(1205);
                        });
            } finally { blocker.rollback(); }
        }
        consistent(id, 0);
        assertThat(registrations.mine(id, actor).status()).isEqualTo("NONE");
        assertThat(registrations.change(id, 0L, false, actor).status()).isEqualTo("CONFIRMED");
        consistent(id, 1);
    }

    @Test void testApplicationGrantsAllowOnlyAuthorizedTableOperations() throws Exception {
        assertThat(jdbc.queryForObject("SELECT CURRENT_USER()", String.class)).isEqualTo("campushub_test_app@127.0.0.1");
        var grants = jdbc.queryForList("SHOW GRANTS", String.class);
        assertThat(grants).contains("GRANT SELECT, INSERT, UPDATE ON `campushub_test`.`activity_registration` TO `campushub_test_app`@`127.0.0.1`");
        // WHERE 1=0 and a no-op ALTER ensure even a misgrant cannot erase data or change the schema.
        for (String sql : List.of("DELETE FROM activity_registration WHERE 1=0",
                "ALTER TABLE activity_registration ENGINE=InnoDB",
                "SELECT * FROM campushub.activity_registration LIMIT 0")) {
            assertThatThrownBy(() -> jdbc.execute(sql)).isInstanceOf(org.springframework.dao.DataAccessException.class)
                    .satisfies(ex -> {
                        var cause = ex.getCause();
                        assertThat(cause).isInstanceOf(java.sql.SQLException.class);
                        assertThat(((java.sql.SQLException) cause).getErrorCode()).isIn(1142, 1044);
                    });
        }
    }
}
