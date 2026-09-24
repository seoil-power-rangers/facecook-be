package com.facecook.auth.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Locale;

/**
 * {@link UserRole} ↔ DB 문자열({@code "participant"} 등) 변환. {@code User.role}
 * 필드의 {@code @Convert}가 이 클래스를 지정한다.
 *
 * <p>JPA 기본값({@code @Enumerated(STRING)})은 enum 이름({@code "PARTICIPANT"})을 그대로
 * 저장하는데, 스키마(V1)의 기본값은 {@code 'participant'}·{@code 'active'}처럼 소문자라 거기에 맞춘다. {@code Locale.ROOT}로 대소문자를 바꾸는 이유는 서버 기본 언어가 터키어
 * 같은 경우에도 {@code I}↔{@code i} 변환이 달라지지 않게 하기 위해서다.</p>
 */
@Converter
public class UserRoleConverter implements AttributeConverter<UserRole, String> {

    @Override
    public String convertToDatabaseColumn(UserRole attribute) {
        return attribute == null ? null : attribute.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public UserRole convertToEntityAttribute(String dbData) {
        return dbData == null ? null : UserRole.valueOf(dbData.toUpperCase(Locale.ROOT));
    }
}
