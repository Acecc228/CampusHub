package com.campushub.auth;

import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

public final class ProductionAuthPolicy {
    private ProductionAuthPolicy() {}

    public static void validate(Environment env) {
        if (!env.matchesProfiles("prod")) return;
        if (!Arrays.equals(env.getActiveProfiles(), new String[]{"prod"})
                || env.getProperty("campushub.auth.demo-enabled", Boolean.class, false)) reject();
        validateEffectiveSettings(env);
        String url = required(env, "spring.datasource.url");
        String username = required(env, "spring.datasource.username");
        required(env, "spring.datasource.password");
        String database = required(env, "campushub.database.production.name");
        String account = required(env, "campushub.database.production.account");
        if (!database.matches("[a-zA-Z0-9_]+") || database.equals("campushub") || database.equals("campushub_test")
                || username.equals("campushub_app") || username.equals("campushub_test_app")
                || username.toLowerCase(Locale.ROOT).contains("root") || !account.startsWith(username + "@")) reject();
        try {
            if (!url.startsWith("jdbc:mysql://") || url.contains("%")) reject();
            var uri = URI.create(url.substring(5));
            String query = uri.getRawQuery();
            if (uri.getHost() == null || uri.getUserInfo() != null || uri.getPort() < 1 || uri.getPort() > 65535
                    || uri.getPort() == 3307 || uri.getFragment() != null
                    || !("/" + database).equals(uri.getRawPath()) || query == null) reject();
            var allowed = Map.of("sslMode","VERIFY_IDENTITY","characterEncoding","UTF-8","connectionTimeZone","UTC");
            Set<String> seen = new HashSet<>();
            for (String part : query.split("&",-1)) {
                String[] pair = part.split("=",2);
                if (pair.length != 2 || !pair[1].equals(allowed.get(pair[0])) || !seen.add(pair[0])) reject();
            }
            if (!seen.contains("sslMode")) reject();
        } catch (IllegalArgumentException exception) { reject(); }
        if (!required(env, "campushub.wechat.app-id").matches("wx[a-zA-Z0-9]{16}")
                || !required(env, "campushub.wechat.app-secret").matches("[a-fA-F0-9]{32}")) reject();
    }

    private static void validateEffectiveSettings(Environment env) {
        var binder = Binder.get(env);
        String address = binder.bind("server.address",String.class).orElse("");
        var modes = binder.bind("server.servlet.session.tracking-modes",Bindable.setOf(String.class)).orElse(Set.of());
        if (!(address.equals("127.0.0.1") || address.equals("::1"))
                || !binder.bind("server.servlet.session.cookie.secure",Boolean.class).orElse(false)
                || !binder.bind("server.servlet.session.cookie.http-only",Boolean.class).orElse(false)
                || !binder.bind("server.servlet.session.cookie.same-site",String.class).orElse("").equalsIgnoreCase("strict")
                || modes.size() != 1 || !modes.iterator().next().equalsIgnoreCase("cookie")
                || !binder.bind("server.forward-headers-strategy",String.class).orElse("").equalsIgnoreCase("none")
                || binder.bind("spring.flyway.enabled",Boolean.class).orElse(true)) reject();
        var hikari = binder.bind("spring.datasource.hikari",Bindable.mapOf(String.class,Object.class)).orElse(Map.of());
        for (String key : hikari.keySet()) {
            String normalized = key.replaceAll("[^a-zA-Z0-9]","").toLowerCase(Locale.ROOT);
            if (Set.of("jdbcurl","username","password","datasourceclassname","datasourcejndi","datasource").contains(normalized)
                    || normalized.startsWith("datasourceproperties")) reject();
        }
    }

    private static String required(Environment env, String key) {
        String value = env.getProperty(key, "");
        if (value.isBlank() || value.contains("${")) reject();
        return value;
    }

    private static void reject() { throw new IllegalStateException("Production authentication configuration rejected"); }
}
