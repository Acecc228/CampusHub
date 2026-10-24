package com.campushub.registration;

import java.time.Instant;

public record RegistrationView(Long id, long activityId, long userId, String status,
        long version, Instant createdAt, Instant updatedAt) {
    public static RegistrationView none(long activityId, long userId) {
        return new RegistrationView(null, activityId, userId, "NONE", 0, null, null);
    }
}
