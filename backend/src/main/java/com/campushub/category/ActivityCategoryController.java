package com.campushub.category;

import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/activity-categories")
public class ActivityCategoryController {
    private final ActivityCategoryService service;

    public ActivityCategoryController(ActivityCategoryService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "查询活动分类", description = "按展示顺序返回数据库中的活动分类")
    public List<ActivityCategory> list() {
        return service.findAll();
    }
}
