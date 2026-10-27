package com.campushub.auth;

import com.campushub.error.ApiError;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

public class CurrentIdentityFilter extends OncePerRequestFilter {
    private final IdentityService identities;
    private final ObjectMapper json;
    public CurrentIdentityFilter(IdentityService identities, ObjectMapper json) { this.identities = identities; this.json = json; }

    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws ServletException,IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CampusPrincipal principal) {
            boolean current;
            try { current = identities.isCurrent(principal); }
            catch (DataAccessException exception) {
                response.setStatus(503);
                response.setContentType("application/json;charset=UTF-8");
                json.writeValue(response.getOutputStream(),new ApiError("SERVICE_UNAVAILABLE","服务暂时不可用"));
                return;
            }
            if (!current) {
                SecurityContextHolder.clearContext();
                var session = request.getSession(false);
                if (session != null) session.invalidate();
                response.setStatus(401);
                response.setContentType("application/json;charset=UTF-8");
                json.writeValue(response.getOutputStream(),new ApiError("UNAUTHORIZED","请先认证"));
                return;
            }
        }
        chain.doFilter(request,response);
    }
}
