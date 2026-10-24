package com.campushub.auth;

import java.util.Arrays;
import org.springframework.core.env.Environment;

public record DemoPolicy(boolean enabled) {
    public static DemoPolicy from(Environment environment) {
        boolean enabled = environment.getProperty("campushub.auth.demo-enabled", Boolean.class, false);
        String[] profiles = environment.getActiveProfiles();
        String address = environment.getProperty("server.address", "");
        if (enabled && (profiles.length == 0
                || Arrays.stream(profiles).anyMatch(p -> !p.equals("dev") && !p.equals("test"))
                || !(address.equals("127.0.0.1") || address.equals("::1")))) {
            throw new IllegalStateException("Demo authentication requires exclusive dev/test profiles and loopback binding");
        }
        return new DemoPolicy(enabled);
    }
}
