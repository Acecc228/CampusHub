package com.campushub.registration;

import com.campushub.activity.VersionInput;
import com.campushub.auth.CampusPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class RegistrationController {
    private final RegistrationService service;
    public RegistrationController(RegistrationService service) { this.service = service; }

    @PostMapping("/activities/{id}/registrations")
    public RegistrationView register(@PathVariable long id, @RequestBody VersionInput input,
            @AuthenticationPrincipal CampusPrincipal actor) {
        return service.change(id, input.version(), false, actor);
    }
    @PostMapping("/activities/{id}/registrations/cancel")
    public RegistrationView cancel(@PathVariable long id, @RequestBody VersionInput input,
            @AuthenticationPrincipal CampusPrincipal actor) {
        return service.change(id, input.version(), true, actor);
    }
    @GetMapping("/activities/{id}/registration")
    public RegistrationView status(@PathVariable long id, @AuthenticationPrincipal CampusPrincipal actor) {
        return service.mine(id, actor);
    }
    @GetMapping("/student/registrations")
    public RegistrationPage mine(@RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size, @AuthenticationPrincipal CampusPrincipal actor) {
        return service.mine(page, size, actor);
    }
    @GetMapping("/manage/activities/{id}/registrations")
    public RegistrationPage managed(@PathVariable long id, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size, @AuthenticationPrincipal CampusPrincipal actor) {
        return service.managed(id, page, size, actor);
    }
}
