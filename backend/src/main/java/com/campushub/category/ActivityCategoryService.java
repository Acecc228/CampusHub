package com.campushub.category;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActivityCategoryService {
    private final ActivityCategoryMapper mapper;

    public ActivityCategoryService(ActivityCategoryMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<ActivityCategory> findAll() {
        return mapper.findAll();
    }
}
