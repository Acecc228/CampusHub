package com.campushub.activity;

import tools.jackson.databind.annotation.JsonDeserialize;

public record VersionInput(@JsonDeserialize(using = StrictLongDeserializer.class) Long version) { }
