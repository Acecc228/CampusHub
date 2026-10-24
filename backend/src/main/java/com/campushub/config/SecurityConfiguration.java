package com.campushub.config;

import com.campushub.auth.CampusPrincipal;
import com.campushub.auth.DemoPolicy;
import com.campushub.auth.IdentityService;
import com.campushub.error.ApiError;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class SecurityConfiguration {
    @Bean
    DemoPolicy demoPolicy(Environment environment) { return DemoPolicy.from(environment); }

    @Bean
    UserDetailsService userDetailsService(DemoPolicy policy, IdentityService identities) {
        return username -> {
            if (!policy.enabled()) throw new UsernameNotFoundException("Invalid credentials");
            return identities.loadDemoAccount(username);
        };
    }

    @Bean
    DaoAuthenticationProvider demoAuthenticationProvider(UserDetailsService users) {
        var provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(new Pbkdf2PasswordEncoder("", 16, 600000,
                Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256));
        return provider;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, Environment environment, ObjectMapper json,
            DemoPolicy policy, DaoAuthenticationProvider provider) throws Exception {
        AccessDeniedHandler denied = (request, response, exception) -> {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            boolean anonymous = authentication == null || !authentication.isAuthenticated()
                    || authentication instanceof AnonymousAuthenticationToken;
            response.setStatus(anonymous ? 401 : 403);
            response.setContentType("application/json;charset=UTF-8");
            json.writeValue(response.getOutputStream(), anonymous
                    ? new ApiError("UNAUTHORIZED", "请先认证") : new ApiError("FORBIDDEN", "无权访问"));
        };
        http.csrf(org.springframework.security.config.Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .requestCache(cache -> cache.disable())
                .httpBasic(basic -> basic.disable())
                .authenticationProvider(provider)
                .authorizeHttpRequests(auth -> {
                    auth.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll();
                    auth.requestMatchers(HttpMethod.GET, "/api/v1/activity-categories", "/actuator/health", "/api/v1/auth/csrf").permitAll();
                    if (policy.enabled()) auth.requestMatchers(HttpMethod.POST, "/api/v1/auth/demo-login").permitAll();
                    auth.requestMatchers("/api/v1/manage/activities", "/api/v1/manage/activities/**").hasAnyRole("ORGANIZER", "ADMIN");
                    auth.requestMatchers("/api/v1/student/**").hasRole("STUDENT");
                    auth.requestMatchers("/api/v1/organizer/**").hasRole("ORGANIZER");
                    auth.requestMatchers("/api/v1/admin/**").hasRole("ADMIN");
                    if (environment.matchesProfiles("dev") && !environment.matchesProfiles("prod")) {
                        auth.requestMatchers(HttpMethod.GET, "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").permitAll();
                    }
                    auth.anyRequest().authenticated();
                })
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> {
                            response.setStatus(401);
                            response.setContentType("application/json;charset=UTF-8");
                            json.writeValue(response.getOutputStream(), new ApiError("UNAUTHORIZED", "请先认证"));
                        }).accessDeniedHandler(denied))
                .logout(logout -> logout.logoutUrl("/api/v1/auth/logout")
                        .deleteCookies("JSESSIONID").logoutSuccessHandler((request, response, authentication) -> {
                            if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
                                response.setStatus(401);
                                response.setContentType("application/json;charset=UTF-8");
                                json.writeValue(response.getOutputStream(), new ApiError("UNAUTHORIZED", "请先认证"));
                            } else {
                                response.setStatus(204);
                            }
                        }));
        if (policy.enabled()) {
            http.formLogin(form -> form.loginPage("/api/v1/auth/demo-login").loginProcessingUrl("/api/v1/auth/demo-login")
                    .successHandler((request, response, authentication) -> {
                        response.setContentType("application/json;charset=UTF-8");
                        json.writeValue(response.getOutputStream(), ((CampusPrincipal) authentication.getPrincipal()).identity());
                    }).failureHandler((request, response, exception) -> {
                        var session = request.getSession(false);
                        if (session != null) session.invalidate();
                        response.setStatus(401);
                        response.setContentType("application/json;charset=UTF-8");
                        json.writeValue(response.getOutputStream(), new ApiError("UNAUTHORIZED", "账号或密码错误"));
                    }));
        } else {
            http.formLogin(form -> form.disable());
        }
        return http.build();
    }
}
