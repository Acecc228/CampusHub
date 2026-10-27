package com.campushub.auth;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthenticationFiltersTest {
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void disabledOrChangedIdentityInvalidatesSessionBeforeNextProtectedRequest() throws Exception {
        var identities = mock(IdentityService.class);
        var principal = new CampusPrincipal(new IdentityMapper.Account(1,"manager","Manager","",true,false), List.of("ADMIN"));
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal,"",principal.getAuthorities()));
        when(identities.isCurrent(principal)).thenReturn(false);
        var request = new MockHttpServletRequest("GET","/api/v1/auth/me");
        var session = (MockHttpSession) request.getSession();
        var response = new MockHttpServletResponse();
        new CurrentIdentityFilter(identities,new ObjectMapper()).doFilter(request,response,new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(session.isInvalid()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test void limiterBoundsSubjectsAndDoesNotTrustForwardedHeadersOrThrottleDemo() throws Exception {
        var clock = Clock.fixed(Instant.EPOCH,ZoneOffset.UTC);
        var filter = new AuthenticationRateLimitFilter(new ObjectMapper(), clock);
        for (int i=0;i<10;i++) assertThat(attempt(filter,"manager")).isEqualTo(200);
        assertThat(attempt(filter,"manager")).isEqualTo(429);
        assertThat(attempt(filter,"other-manager")).isEqualTo(200);
        var demo = new MockHttpServletRequest("POST","/api/v1/auth/demo-login");
        var response = new MockHttpServletResponse();
        filter.doFilter(demo,response,new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(200);
    }

    private int attempt(AuthenticationRateLimitFilter filter,String username) throws Exception {
        var request=new MockHttpServletRequest("POST","/api/v1/auth/login");
        request.setServletPath("/api/v1/auth/login");
        request.setParameter("username",username);
        request.addHeader("X-Forwarded-For",java.util.UUID.randomUUID().toString());
        var response=new MockHttpServletResponse();
        filter.doFilter(request,response,new MockFilterChain());
        return response.getStatus();
    }
}
