package com.campushub.auth;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RoleAccessController {
    public record Access(String capability, long userId) {}

    @GetMapping("/api/v1/student/access")
    public Access student(@AuthenticationPrincipal CampusPrincipal user) { return new Access("STUDENT", user.identity().id()); }

    @GetMapping("/api/v1/organizer/access")
    public Access organizer(@AuthenticationPrincipal CampusPrincipal user) { return new Access("ORGANIZER", user.identity().id()); }

    @GetMapping("/api/v1/admin/access")
    public Access admin(@AuthenticationPrincipal CampusPrincipal user) { return new Access("ADMIN", user.identity().id()); }
}
