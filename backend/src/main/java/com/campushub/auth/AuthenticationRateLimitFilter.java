package com.campushub.auth;

import com.campushub.error.ApiError;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/** Bounded single-process limits. The reverse proxy owns per-client limits. */
public class AuthenticationRateLimitFilter extends OncePerRequestFilter {
    private final ObjectMapper json;
    private final Clock clock;
    private final Map<String,Integer> usernames = new HashMap<>();
    private long window = -1;
    private int total;

    public AuthenticationRateLimitFilter(ObjectMapper json, Clock clock) { this.json = json; this.clock = clock; }

    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws ServletException,IOException {
        String path = request.getServletPath();
        if (!request.getMethod().equals("POST") || (!path.equals("/api/v1/auth/login") && !path.equals("/api/v1/auth/wechat-login"))) {
            chain.doFilter(request,response); return;
        }
        String username = path.equals("/api/v1/auth/login") ? request.getParameter("username") : null;
        if (!allow(username)) {
            response.setStatus(429);
            response.setHeader("Retry-After","60");
            response.setContentType("application/json;charset=UTF-8");
            json.writeValue(response.getOutputStream(),new ApiError("TOO_MANY_REQUESTS","登录请求过于频繁，请稍后再试"));
            return;
        }
        chain.doFilter(request,response);
    }

    private synchronized boolean allow(String username) {
        long minute = clock.millis() / 60000;
        if (window != minute) { window = minute; total = 0; usernames.clear(); }
        if (++total > 600) return false;
        if (username == null) return true;
        if (username.length() > 64) return false;
        if (!usernames.containsKey(username) && usernames.size() >= 4096) return false;
        return usernames.merge(username,1,Integer::sum) <= 10;
    }
}
