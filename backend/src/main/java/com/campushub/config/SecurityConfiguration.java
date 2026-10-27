package com.campushub.config;

import com.campushub.auth.CampusPrincipal;
import com.campushub.auth.DemoPolicy;
import com.campushub.auth.IdentityService;
import com.campushub.auth.ProductionAuthPolicy;
import com.campushub.auth.CurrentIdentityFilter;
import com.campushub.auth.AuthenticationRateLimitFilter;
import com.campushub.auth.WechatLoginController;
import com.campushub.error.ApiError;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class SecurityConfiguration {
    @Bean
    DemoPolicy demoPolicy(Environment environment) {
        var policy = DemoPolicy.from(environment);
        ProductionAuthPolicy.validate(environment);
        return policy;
    }

    @Bean
    Pbkdf2PasswordEncoder passwordEncoder() {
        return new Pbkdf2PasswordEncoder("", 16, 600000,
                Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256);
    }

    @Bean
    DaoAuthenticationProvider managementAuthenticationProvider(IdentityService identities, Pbkdf2PasswordEncoder encoder) {
        var provider = new DaoAuthenticationProvider(identities::loadManagementAccount);
        provider.setPasswordEncoder(encoder);
        return provider;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, Environment environment, ObjectMapper json,
            DemoPolicy policy, DaoAuthenticationProvider provider, IdentityService identities, Pbkdf2PasswordEncoder encoder) throws Exception {
        AuthenticationSuccessHandler success = (request, response, authentication) -> {
            response.setContentType("application/json;charset=UTF-8");
            json.writeValue(response.getOutputStream(), ((CampusPrincipal) authentication.getPrincipal()).identity());
        };
        AuthenticationFailureHandler failure = (request, response, exception) -> {
            SecurityContextHolder.clearContext();
            var session = request.getSession(false);
            if (session != null) session.invalidate();
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            json.writeValue(response.getOutputStream(), new ApiError("UNAUTHORIZED", "账号或密码错误"));
        };
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
                .addFilterAfter(new CurrentIdentityFilter(identities,json), SecurityContextHolderFilter.class)
                .addFilterBefore(new AuthenticationRateLimitFilter(json,java.time.Clock.systemUTC()), LogoutFilter.class)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .requestCache(cache -> cache.disable())
                .httpBasic(basic -> basic.disable())
                .authenticationProvider(provider)
                .authorizeHttpRequests(auth -> {
                    auth.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll();
                    auth.requestMatchers(HttpMethod.GET, "/api/v1/activity-categories", "/actuator/health", "/api/v1/auth/csrf").permitAll();
                    auth.requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/wechat-login").permitAll();
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
        http.formLogin(form -> form.loginPage("/api/v1/auth/login").loginProcessingUrl("/api/v1/auth/login")
                .successHandler(success).failureHandler(failure));
        if (policy.enabled()) {
            var demoProvider = new DaoAuthenticationProvider(identities::loadDemoAccount);
            demoProvider.setPasswordEncoder(encoder);
            var demo = new UsernamePasswordAuthenticationFilter(new ProviderManager(demoProvider));
            demo.setFilterProcessesUrl("/api/v1/auth/demo-login");
            demo.setAuthenticationSuccessHandler(success);
            demo.setAuthenticationFailureHandler(failure);
            demo.setSecurityContextRepository(new HttpSessionSecurityContextRepository());
            demo.setSessionAuthenticationStrategy(WechatLoginController.sessionStrategy());
            http.addFilterBefore(demo,UsernamePasswordAuthenticationFilter.class);
        }
        return http.build();
    }
}
