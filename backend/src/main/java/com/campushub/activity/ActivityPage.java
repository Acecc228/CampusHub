package com.campushub.activity;

import java.util.List;

public record ActivityPage(List<ActivityView> items, int page, int size, long total) { }
