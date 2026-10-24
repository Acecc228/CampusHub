package com.campushub.auth;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {
    public record CsrfResponse(String headerName, String token) {}

    @GetMapping("/api/v1/auth/csrf")
    public CsrfResponse csrf(CsrfToken csrf) { return new CsrfResponse(csrf.getHeaderName(), csrf.getToken()); }

    @GetMapping("/api/v1/auth/me")
    public CampusPrincipal.Identity me(@AuthenticationPrincipal CampusPrincipal principal) { return principal.identity(); }
}
