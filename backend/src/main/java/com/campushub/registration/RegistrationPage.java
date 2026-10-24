package com.campushub.registration;

import java.util.List;

public record RegistrationPage(List<RegistrationView> items, int page, int size, long total) { }
