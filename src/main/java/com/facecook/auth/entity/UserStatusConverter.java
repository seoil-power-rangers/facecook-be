package com.facecook.auth.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Locale;

/** {@link UserStatus} ↔ DB 소문자 문자열 변환. 만든 이유는 {@link UserRoleConverter}와 같다. */
@Converter
public class UserStatusConverter implements AttributeConverter<UserStatus, String> {

    @Override
    public String convertToDatabaseColumn(UserStatus attribute) {
        return attribute == null ? null : attribute.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public UserStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : UserStatus.valueOf(dbData.toUpperCase(Locale.ROOT));
    }
}
