package com.facecook.cook.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

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
