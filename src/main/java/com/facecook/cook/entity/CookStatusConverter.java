package com.facecook.cook.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * {@link CookStatus} ↔ DB 문자열 변환. {@code Cook.status}의 {@code @Convert}가 지정한다.
 * DB에 모르는 값이 있으면 {@link CookStatus#from}이 예외를 던진다 — 알 수 없는 상태를
 * 조용히 다른 값으로 바꿔 읽지 않는다.
 */
@Converter(autoApply = false)
public class CookStatusConverter implements AttributeConverter<CookStatus, String> {

    @Override
    public String convertToDatabaseColumn(CookStatus attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public CookStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : CookStatus.from(dbData);
    }
}
