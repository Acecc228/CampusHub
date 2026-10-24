package com.campushub.activity;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

public class StrictInstantDeserializer extends ValueDeserializer<Instant> {
    @Override
    public Instant deserialize(JsonParser parser, DeserializationContext context) {
        if (!parser.hasToken(JsonToken.VALUE_STRING)) {
            return (Instant) context.handleUnexpectedToken(Instant.class, parser);
        }
        try {
            return OffsetDateTime.parse(parser.getString()).toInstant();
        } catch (DateTimeParseException exception) {
            return (Instant) context.handleWeirdStringValue(Instant.class, parser.getString(), "Expected ISO-8601 time with offset");
        }
    }
}
