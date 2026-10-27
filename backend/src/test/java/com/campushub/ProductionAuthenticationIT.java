package com.campushub;

import com.campushub.auth.WechatHttpClient;
import com.campushub.error.BusinessException;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;

/** Real HTTP + real MySQL, FAKE upstream contract test. This is NOT real WeChat validation. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ProductionAuthenticationIT extends MySqlIntegrationSupport {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;

    @TestConfiguration(proxyBeanMethods=false)
    static class FakeWechat {
        @Bean @Primary WechatHttpClient contractClient(ObjectMapper json) {
            return new WechatHttpClient(json,"wxcontracttest0000","") {
                @Override public String exchange(String code) {
                    if (code.equals("expired")) throw new BusinessException(401,"UNAUTHORIZED","微信登录失败");
                    if (code.equals("timeout")) throw new BusinessException(503,"SERVICE_UNAVAILABLE","微信登录暂时不可用");
                    if (!code.startsWith("contract-")) throw new BusinessException(400,"INVALID_INPUT","请求格式或参数无效");
                    return code;
                }
            };
        }
    }

    class Browser {
        final CookieManager cookies=new CookieManager(null,CookiePolicy.ACCEPT_ALL);
        final HttpClient client=HttpClient.newBuilder().cookieHandler(cookies).build();
        String csrf;
        HttpResponse<String> request(String method,String path,String body,boolean jsonBody) throws Exception {
            var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(java.time.Duration.ofSeconds(15));
            if(csrf!=null) builder.header("X-CSRF-TOKEN",csrf);
            if(method.equals("GET")) builder.GET();
            else builder.header("Content-Type",jsonBody?"application/json":"application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(body));
            return client.send(builder.build(),HttpResponse.BodyHandlers.ofString());
        }
        void csrf() throws Exception {
            var response=request("GET","/api/v1/auth/csrf","",false);
            assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
            csrf=json.readTree(response.body()).path("token").asText();
        }
        HttpResponse<String> wechat(String code) throws Exception { return request("POST","/api/v1/auth/wechat-login","{\"code\":\""+code+"\",\"userId\":1,\"role\":\"ADMIN\"}",true); }
        String session() { return cookies.getCookieStore().getCookies().stream().filter(c->c.getName().equals("JSESSIONID")).findFirst().orElseThrow().getValue(); }
    }

    @Test void studentSessionRotatesCsrfResetsIdentityIsServerOwnedAndLogoutRevokes() throws Exception {
        var browser=new Browser();
        assertThat(browser.wechat("contract-"+UUID.randomUUID()).statusCode()).isEqualTo(401);
        browser.csrf(); String before=browser.session(); String oldCsrf=browser.csrf;
        var login=browser.wechat("contract-"+UUID.randomUUID());
        assertThat(login.statusCode()).isEqualTo(200);
        assertThat(login.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        assertThat(browser.session()).isNotEqualTo(before);
        assertThat(login.body()).doesNotContain("openid","session_key","password","appid");
        var identity=json.readTree(login.body());
        assertThat(identity.path("roles").toString()).isEqualTo("[\"STUDENT\"]");
        assertThat(browser.request("GET","/api/v1/student/access","",false).statusCode()).isEqualTo(200);
        assertThat(browser.request("GET","/api/v1/admin/access","",false).statusCode()).isEqualTo(403);
        browser.csrf=oldCsrf;
        assertThat(browser.request("POST","/api/v1/auth/logout","",false).statusCode()).isEqualTo(403);
        browser.csrf();
        assertThat(browser.request("POST","/api/v1/auth/logout","",false).statusCode()).isEqualTo(204);
        assertThat(browser.request("GET","/api/v1/auth/me","",false).statusCode()).isEqualTo(401);
    }

    @Test void concurrentFirstLoginCreatesExactlyOneUserAndStudentRole() throws Exception {
        String code="contract-"+UUID.randomUUID();
        var barrier=new CyclicBarrier(6);
        var executor=Executors.newFixedThreadPool(6);
        var futures=new ArrayList<Future<Long>>();
        try {
            for(int i=0;i<6;i++) futures.add(executor.submit(()->{
                var browser=new Browser(); browser.csrf(); barrier.await(15,TimeUnit.SECONDS);
                var response=browser.wechat(code); assertThat(response.statusCode()).isEqualTo(200);
                return json.readTree(response.body()).path("id").asLong();
            }));
            var ids=new HashSet<Long>();
            for(var future:futures) ids.add(future.get(30,TimeUnit.SECONDS));
            assertThat(ids).hasSize(1);
            long id=ids.iterator().next();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_wechat_identity WHERE appid='wxcontracttest0000' AND openid=? AND user_id=?",Long.class,code,id)).isEqualTo(1);
            assertThat(jdbc.queryForList("SELECT r.code FROM app_role r JOIN app_user_role ur ON ur.role_id=r.id WHERE ur.user_id=?",String.class,id)).containsExactly("STUDENT");
        } finally { executor.shutdownNow(); }
    }

    @Test void disabledOrRoleChangedStudentIsRevokedAndNeverPromotedOnLogin() throws Exception {
        for(boolean disabled:new boolean[]{true,false}) {
            String code="contract-"+UUID.randomUUID(); var browser=new Browser(); browser.csrf();
            long id=json.readTree(browser.wechat(code).body()).path("id").asLong();
            if(disabled) jdbc.update("UPDATE app_user SET enabled=FALSE WHERE id=?",id);
            else jdbc.update("INSERT INTO app_user_role(user_id,role_id) SELECT ?,id FROM app_role WHERE code='ADMIN'",id);
            assertThat(browser.request("GET","/api/v1/auth/me","",false).statusCode()).isEqualTo(401);
            browser.csrf(); assertThat(browser.wechat(code).statusCode()).isEqualTo(401);
        }
    }

    @Test void formalManagementLoginRejectsDemoStudentAndWrongPasswordAndRevokesChangedRole() throws Exception {
        String password="contract-test-password";
        var encoder=new Pbkdf2PasswordEncoder("",16,600000,Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256);
        for(String role:new String[]{"ORGANIZER","ADMIN","STUDENT"}) {
            String name="m1_prod_"+UUID.randomUUID().toString().replace("-","");
            jdbc.update("INSERT INTO app_user(login_name,display_name,password_hash,enabled,demo_account) VALUES(?,'Contract manager',?,TRUE,FALSE)",name,encoder.encode(password));
            long id=jdbc.queryForObject("SELECT id FROM app_user WHERE login_name=?",Long.class,name);
            jdbc.update("INSERT INTO app_user_role(user_id,role_id) SELECT ?,id FROM app_role WHERE code=?",id,role);
            var browser=new Browser(); browser.csrf();
            String form="username="+name+"&password="+URLEncoder.encode(password,StandardCharsets.UTF_8)+"&role=ADMIN&userId=1";
            var response=browser.request("POST","/api/v1/auth/login",form,false);
            assertThat(response.statusCode()).isEqualTo(role.equals("STUDENT")?401:200);
            if(!role.equals("STUDENT")) {
                assertThat(json.readTree(response.body()).path("roles").toString()).isEqualTo("[\""+role+"\"]");
                jdbc.update("UPDATE app_user SET enabled=FALSE WHERE id=?",id);
                assertThat(browser.request("GET","/api/v1/auth/me","",false).statusCode()).isEqualTo(401);
            }
        }
        for(String username:new String[]{"demo_admin","missing"}) {
            var browser=new Browser(); browser.csrf();
            assertThat(browser.request("POST","/api/v1/auth/login","username="+username+"&password=wrong",false).statusCode()).isEqualTo(401);
        }
    }

    @Test void upstreamFailuresHaveSafeStatusAndNoAuthenticatedSession() throws Exception {
        for(String code:new String[]{"expired","timeout","invalid"}) {
            var browser=new Browser(); browser.csrf(); var response=browser.wechat(code);
            assertThat(response.statusCode()).isEqualTo(code.equals("expired")?401:code.equals("timeout")?503:400);
            assertThat(response.body()).doesNotContain("secret","session_key","openid");
            assertThat(browser.request("GET","/api/v1/auth/me","",false).statusCode()).isEqualTo(401);
        }
    }

    @Test void managementPasswordResetRevokesErasedCredentialsSessionAndFailedReloginClearsIdentity() throws Exception {
        var encoder=new Pbkdf2PasswordEncoder("",16,600000,Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256);
        String name="m1_reset_"+UUID.randomUUID().toString().replace("-","");
        jdbc.update("INSERT INTO app_user(login_name,display_name,password_hash,enabled,demo_account) VALUES(?,'Reset contract manager',?,TRUE,FALSE)",name,encoder.encode("before-reset"));
        long id=jdbc.queryForObject("SELECT id FROM app_user WHERE login_name=?",Long.class,name);
        jdbc.update("INSERT INTO app_user_role(user_id,role_id) SELECT ?,id FROM app_role WHERE code='ORGANIZER'",id);
        var browser=new Browser(); browser.csrf(); String before=browser.session();
        var login=browser.request("POST","/api/v1/auth/login","username="+name+"&password=before-reset",false);
        assertThat(login.statusCode()).isEqualTo(200);
        assertThat(login.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        assertThat(browser.session()).isNotEqualTo(before);
        assertThat(browser.request("GET","/api/v1/auth/me","",false).statusCode()).isEqualTo(200);
        jdbc.update("UPDATE app_user SET password_hash=? WHERE id=?",encoder.encode("after-reset"),id);
        assertThat(browser.request("GET","/api/v1/auth/me","",false).statusCode()).isEqualTo(401);
        browser.csrf();
        assertThat(browser.request("POST","/api/v1/auth/login","username="+name+"&password=after-reset",false).statusCode()).isEqualTo(200);
        browser.csrf();
        assertThat(browser.request("POST","/api/v1/auth/login","username="+name+"&password=wrong",false).statusCode()).isEqualTo(401);
        assertThat(browser.request("GET","/api/v1/auth/me","",false).statusCode()).isEqualTo(401);
    }

    @Test void failedWechatReloginClearsPreviouslyAuthenticatedIdentity() throws Exception {
        var browser=new Browser(); browser.csrf();
        assertThat(browser.wechat("contract-"+UUID.randomUUID()).statusCode()).isEqualTo(200);
        browser.csrf();
        assertThat(browser.wechat("expired").statusCode()).isEqualTo(401);
        assertThat(browser.request("GET","/api/v1/auth/me","",false).statusCode()).isEqualTo(401);
    }
}
