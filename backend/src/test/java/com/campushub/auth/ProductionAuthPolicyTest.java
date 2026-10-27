package com.campushub.auth;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;

class ProductionAuthPolicyTest {
    MockEnvironment valid() {
        var env = new MockEnvironment().withProperty("spring.datasource.url", "jdbc:mysql://127.0.0.1:3306/campushub_prod?sslMode=VERIFY_IDENTITY&characterEncoding=UTF-8&connectionTimeZone=UTC")
                .withProperty("spring.datasource.username", "campushub_prod_app")
                .withProperty("spring.datasource.password", "private-test-value")
                .withProperty("campushub.database.production.name", "campushub_prod")
                .withProperty("campushub.database.production.account", "campushub_prod_app@localhost")
                .withProperty("campushub.wechat.app-id", "wx1234567890abcdef")
                .withProperty("campushub.wechat.app-secret", "0123456789abcdef0123456789abcdef")
                .withProperty("server.address","127.0.0.1")
                .withProperty("server.servlet.session.cookie.secure","true")
                .withProperty("server.servlet.session.cookie.http-only","true")
                .withProperty("server.servlet.session.cookie.same-site","strict")
                .withProperty("server.servlet.session.tracking-modes","cookie")
                .withProperty("server.forward-headers-strategy","none")
                .withProperty("spring.flyway.enabled","false");
        env.setActiveProfiles("prod");
        return env;
    }

    @Test void productionRequiresExclusiveProfileAndExplicitSecrets() {
        assertThatCode(() -> ProductionAuthPolicy.validate(valid())).doesNotThrowAnyException();
        var env = valid(); env.setActiveProfiles("prod", "dev");
        assertThatThrownBy(() -> ProductionAuthPolicy.validate(env)).isInstanceOf(IllegalStateException.class);
        for (String key : new String[]{"campushub.wechat.app-secret", "spring.datasource.password", "campushub.database.production.account"}) {
            var missing = valid().withProperty(key, "");
            assertThatThrownBy(() -> ProductionAuthPolicy.validate(missing)).isInstanceOf(IllegalStateException.class).hasMessageNotContaining("private-test-value");
        }
    }

    @Test void productionRejectsDemoLocalDevelopmentIdentityAndWeakTls() {
        for (var entry : new String[][]{{"campushub.auth.demo-enabled","true"}, {"spring.datasource.username","campushub_app"},
                {"campushub.database.production.name","campushub_test"}, {"spring.datasource.url","jdbc:mysql://127.0.0.1:3307/campushub_prod?sslMode=VERIFY_CA"},
                {"spring.datasource.url","jdbc:mysql://db:3306/campushub_prod?sslMode=REQUIRED"}}) {
            assertThatThrownBy(() -> ProductionAuthPolicy.validate(valid().withProperty(entry[0],entry[1])))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test void productionRejectsEffectiveCookieBindForwardingAndMigrationOverrides() {
        for (var entry:new String[][]{{"server.address","0.0.0.0"},{"server.address","localhost"},
                {"server.servlet.session.cookie.secure","false"},{"server.servlet.session.cookie.http-only","false"},
                {"server.servlet.session.cookie.same-site","lax"},{"server.servlet.session.tracking-modes","cookie,url"},
                {"server.forward-headers-strategy","framework"},{"spring.flyway.enabled","true"}}) {
            assertThatThrownBy(()->ProductionAuthPolicy.validate(valid().withProperty(entry[0],entry[1])))
                    .as(entry[0]).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test void productionRejectsUntrustedJdbcQueryOverridesAndEncodedTlsNames() {
        String base="jdbc:mysql://127.0.0.1:3306/campushub_prod?";
        for(String query:new String[]{"sslMode=VERIFY_CA","sslMode=VERIFY_IDENTITY&sslMode=DISABLED",
                "sslMode=VERIFY_IDENTITY&ssl%4dode=DISABLED","sslMode=VERIFY_IDENTITY&user=root",
                "sslMode=VERIFY_IDENTITY&password=private-marker","sslMode=VERIFY_IDENTITY&sessionVariables=sql_mode=''",
                "sslMode=VERIFY_IDENTITY&allowPublicKeyRetrieval=true","sslMode=VERIFY_IDENTITY&connectionTimeZone=LOCAL",
                "sslMode=VERIFY_IDENTITY&trustCertificateKeyStorePassword=private-marker","sslMode=VERIFY_IDENTITY#fragment"}) {
            assertThatThrownBy(()->ProductionAuthPolicy.validate(valid().withProperty("spring.datasource.url",base+query)))
                    .as("reject query override").isInstanceOf(IllegalStateException.class).hasMessageNotContaining("private-marker");
        }
    }

    @Test void productionRejectsHikariEffectiveDataSourceOverridesIncludingRelaxedNames() {
        for(String key:new String[]{"jdbc-url","jdbcUrl","username","password","data-source-class-name",
                "dataSourceClassName","data-source-jndi","data-source-properties.sslMode","dataSourceProperties.user"}) {
            assertThatThrownBy(()->ProductionAuthPolicy.validate(valid().withProperty("spring.datasource.hikari."+key,"private-marker")))
                    .as(key).isInstanceOf(IllegalStateException.class).hasMessageNotContaining("private-marker");
        }
        assertThatCode(()->ProductionAuthPolicy.validate(valid().withProperty("spring.datasource.hikari.maximum-pool-size","5")))
                .doesNotThrowAnyException();
    }
}
