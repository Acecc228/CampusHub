package com.campushub.activity;

import com.campushub.auth.CampusPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/manage/activities")
public class ActivityManagementController {
    private final ActivityService service;
    public ActivityManagementController(ActivityService service) { this.service = service; }

    @GetMapping
    public ActivityPage list(@RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size, @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword, @RequestParam(required = false) ActivityStatus status,
            @AuthenticationPrincipal CampusPrincipal actor) {
        return service.list(page, size, categoryId, keyword, status, true, actor);
    }

    @GetMapping("/{id}")
    public ActivityView detail(@PathVariable long id, @AuthenticationPrincipal CampusPrincipal actor) {
        return service.managed(id, actor);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ActivityView create(@RequestBody ActivityInput input, @AuthenticationPrincipal CampusPrincipal actor) {
        return service.create(input, actor);
    }

    @PutMapping("/{id}")
    public ActivityView edit(@PathVariable long id, @RequestBody ActivityInput input,
            @AuthenticationPrincipal CampusPrincipal actor) { return service.edit(id, input, actor); }

    @PostMapping("/{id}/publish")
    public ActivityView publish(@PathVariable long id, @RequestBody VersionInput input,
            @AuthenticationPrincipal CampusPrincipal actor) { return service.transition(id, input.version(), true, actor); }

    @PostMapping("/{id}/unpublish")
    public ActivityView unpublish(@PathVariable long id, @RequestBody VersionInput input,
            @AuthenticationPrincipal CampusPrincipal actor) { return service.transition(id, input.version(), false, actor); }
}
