package com.campushub.activity;

import com.campushub.registration.RegistrationView;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

public record ActivityDetail(@JsonUnwrapped ActivityView activity, RegistrationView myRegistration) { }
