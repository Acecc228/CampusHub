package com.campushub.activity;

import com.campushub.auth.CampusPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/activities")
public class ActivityController {
    private final ActivityService service;
    public ActivityController(ActivityService service) { this.service = service; }

    @GetMapping
    public ActivityPage list(@RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size, @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword, @RequestParam(required = false) ActivityStatus status,
            @AuthenticationPrincipal CampusPrincipal actor) {
        return service.list(page, size, categoryId, keyword, status, false, actor);
    }

    @GetMapping("/{id}")
    public ActivityDetail detail(@PathVariable long id, @AuthenticationPrincipal CampusPrincipal actor) {
        return service.detail(id, actor);
    }
}
