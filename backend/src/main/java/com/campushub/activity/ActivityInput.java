package com.campushub.activity;

import java.time.Instant;
import tools.jackson.databind.annotation.JsonDeserialize;

public record ActivityInput(
        @JsonDeserialize(using = StrictStringDeserializer.class) String title,
        @JsonDeserialize(using = StrictStringDeserializer.class) String summary,
        @JsonDeserialize(using = StrictStringDeserializer.class) String content,
        @JsonDeserialize(using = StrictLongDeserializer.class) Long categoryId,
        @JsonDeserialize(using = StrictStringDeserializer.class) String location,
        @JsonDeserialize(using = StrictInstantDeserializer.class) Instant startTime,
        @JsonDeserialize(using = StrictInstantDeserializer.class) Instant endTime,
        @JsonDeserialize(using = StrictInstantDeserializer.class) Instant registrationStart,
        @JsonDeserialize(using = StrictInstantDeserializer.class) Instant registrationEnd,
        @JsonDeserialize(using = StrictLongDeserializer.class) Long capacity,
        @JsonDeserialize(using = StrictLongDeserializer.class) Long version) {
}
