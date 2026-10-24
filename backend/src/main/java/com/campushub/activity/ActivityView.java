package com.campushub.activity;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ActivityView(long id, String title, String summary, String content, long categoryId,
        String categoryName, String location, Instant startTime, Instant endTime,
        Instant registrationStart, Instant registrationEnd, int capacity, long creatorId,
        ActivityStatus status, long version, Instant createdAt, Instant updatedAt,
        int registeredCount, int remainingCapacity) { }
