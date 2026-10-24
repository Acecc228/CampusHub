package com.campushub;

import java.net.URI;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "campushub.auth.demo-enabled=true")
@ActiveProfiles("test")
class AuthenticationIT extends MySqlIntegrationSupport {
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext serverContext;

    class Browser {
        final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        final HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).build();
        String csrf;
        HttpResponse<String> request(String method, String path, String form) throws Exception {
            var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
            if (csrf != null) builder.header("X-CSRF-TOKEN", csrf);
            if (method.equals("GET")) builder.GET();
            else builder.header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form));
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        }
        void csrf() throws Exception {
            var response = request("GET", "/api/v1/auth/csrf", "");
            assertThat(response.statusCode()).isEqualTo(200);
            csrf = json.readTree(response.body()).get("token").asText();
        }
        HttpResponse<String> login(String role, String extra) throws Exception {
            csrf();
            String password = System.getenv("CH_DEMO_" + role.toUpperCase() + "_PASSWORD");
            assertThat(password).as("Use the protected demo credentials via Invoke-Backend").isNotBlank();
            return request("POST", "/api/v1/auth/demo-login", "username=demo_" + role + "&password="
                    + URLEncoder.encode(password, StandardCharsets.UTF_8) + extra);
        }
    }

    @Test void threeRolesLoginAndOnlyAccessOwnRoleEndpoints() throws Exception {
        for (String role : new String[]{"student", "organizer", "admin"}) {
            var browser = new Browser();
            assertThat(browser.login(role, "").statusCode()).isEqualTo(200);
            var me = browser.request("GET", "/api/v1/auth/me", "");
            assertThat(me.statusCode()).isEqualTo(200);
            var body = json.readTree(me.body());
            assertThat(body.get("loginName").asText()).isEqualTo("demo_" + role);
            assertThat(body.get("roles").toString()).isEqualTo("[\"" + role.toUpperCase() + "\"]");
            assertThat(body.get("id").asLong()).isPositive();
            assertThat(me.body()).doesNotContain("password", "hash", "session");
            for (String target : new String[]{"student", "organizer", "admin"}) {
                assertThat(browser.request("GET", "/api/v1/" + target + "/access", "").statusCode())
                        .as(role + " -> " + target).isEqualTo(role.equals(target) ? 200 : 403);
            }
        }
    }

    @Test void anonymousAndForgedSessionAre401() throws Exception {
        for (String path : new String[]{"/api/v1/auth/me", "/api/v1/student/access", "/api/v1/organizer/access", "/api/v1/admin/access"}) {
            assertThat(new Browser().request("GET", path, "").statusCode()).isEqualTo(401);
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                    .header("Cookie", "JSESSIONID=forged").header("Authorization", "Bearer forged").GET().build();
            assertThat(HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
        }
    }

    @Test void roleAndUserIdTamperingNeverChangesIdentity() throws Exception {
        for (String role : new String[]{"student", "organizer"}) {
            var browser = new Browser();
            assertThat(browser.login(role, "&userId=1&role=ADMIN&roles=ADMIN").statusCode()).isEqualTo(200);
            var me = browser.request("GET", "/api/v1/auth/me?userId=1&role=ADMIN", "");
            assertThat(json.readTree(me.body()).get("loginName").asText()).isEqualTo("demo_" + role);
            assertThat(browser.request("GET", "/api/v1/admin/access?role=ADMIN", "").statusCode()).isEqualTo(403);
        }
    }

    @Test void wrongPasswordUnknownUserAndIdOnlyLoginAre401() throws Exception {
        for (String form : new String[]{"username=demo_admin&password=wrong", "username=missing&password=wrong", "userId=1&role=ADMIN", "username=demo_student%27%20OR%201%3D1&password=wrong"}) {
            var browser = new Browser(); browser.csrf();
            assertThat(browser.request("POST", "/api/v1/auth/demo-login", form).statusCode()).isEqualTo(401);
            assertThat(browser.request("GET", "/api/v1/auth/me", "").statusCode()).isEqualTo(401);
        }
    }

    @Test void csrfRequiredAndLogoutRevokesOldCookie() throws Exception {
        var browser = new Browser();
        assertThat(browser.request("POST", "/api/v1/auth/demo-login", "username=demo_admin").statusCode()).isEqualTo(401);
        browser.csrf();
        String before = browser.cookies.getCookieStore().getCookies().stream().filter(c -> c.getName().equals("JSESSIONID")).findFirst().orElseThrow().getValue();
        assertThat(browser.login("student", "").statusCode()).isEqualTo(200);
        String after = browser.cookies.getCookieStore().getCookies().stream().filter(c -> c.getName().equals("JSESSIONID")).findFirst().orElseThrow().getValue();
        assertThat(after).isNotEqualTo(before);
        browser.csrf = null;
        assertThat(browser.request("POST", "/api/v1/auth/logout", "").statusCode()).isEqualTo(403);
        browser.csrf();
        assertThat(browser.request("POST", "/api/v1/auth/logout", "").statusCode()).isEqualTo(204);
        var replay = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/auth/me"))
                .header("Cookie", "JSESSIONID=" + after).GET().build();
        assertThat(HttpClient.newHttpClient().send(replay, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
        assertThat(browser.request("GET", "/api/v1/auth/me", "").statusCode()).isEqualTo(401);
    }

    @Test void failedLoginClearsPreviouslyAuthenticatedIdentity() throws Exception {
        var browser = new Browser();
        assertThat(browser.login("admin", "").statusCode()).isEqualTo(200);
        browser.csrf();
        assertThat(browser.request("POST", "/api/v1/auth/demo-login", "username=demo_student&password=wrong").statusCode()).isEqualTo(401);
        assertThat(browser.request("GET", "/api/v1/admin/access", "").statusCode()).isEqualTo(401);
    }

    @Test void anonymousLogoutWithValidCsrfIs401() throws Exception {
        var browser = new Browser(); browser.csrf();
        assertThat(browser.request("POST", "/api/v1/auth/logout", "").statusCode()).isEqualTo(401);
    }

    @Test void disabledAndNonDemoAccountsCannotAuthenticate() throws Exception {
        // Only the test schema is available to this process. Restore both flags even on failure.
        for (String column : new String[]{"enabled", "demo_account"}) {
            try {
                jdbc.update("UPDATE app_user SET " + column + "=0 WHERE login_name='demo_student'");
                var browser = new Browser();
                assertThat(browser.login("student", "").statusCode()).isEqualTo(401);
                assertThat(browser.request("GET", "/api/v1/auth/me", "").statusCode()).isEqualTo(401);
            } finally {
                jdbc.update("UPDATE app_user SET " + column + "=1 WHERE login_name='demo_student'");
            }
        }
    }

    @Test void expiredSessionCannotAccessProtectedEndpoint() throws Exception {
        var browser = new Browser();
        assertThat(browser.login("student", "").statusCode()).isEqualTo(200);
        String id = browser.cookies.getCookieStore().getCookies().stream()
                .filter(c -> c.getName().equals("JSESSIONID")).findFirst().orElseThrow().getValue();
        var webServer = (org.springframework.boot.tomcat.TomcatWebServer) serverContext.getWebServer();
        var context = (org.apache.catalina.Context) webServer.getTomcat().getHost().findChildren()[0];
        var session = context.getManager().findSession(id);
        assertThat(session.getMaxInactiveInterval()).isEqualTo(1800);
        // Shorten this one real server-side session; do not change production defaults or fake authentication.
        session.setMaxInactiveInterval(1);
        Thread.sleep(1200);
        assertThat(browser.request("GET", "/api/v1/auth/me", "").statusCode()).isEqualTo(401);
    }
}
