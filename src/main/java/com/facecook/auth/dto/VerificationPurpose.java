package com.facecook.auth.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum VerificationPurpose {
    SIGNUP,
    LOGIN;

    @JsonCreator
    public static VerificationPurpose from(String value) {
        if (value == null) {
            return null;
        }
        return VerificationPurpose.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    @JsonValue
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }
}
