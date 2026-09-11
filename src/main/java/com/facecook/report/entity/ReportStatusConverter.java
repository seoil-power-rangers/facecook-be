package com.facecook.report.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Locale;

@Converter
public class ReportStatusConverter implements AttributeConverter<ReportStatus, String> {

    @Override
    public String convertToDatabaseColumn(ReportStatus attribute) {
        return attribute == null ? null : attribute.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public ReportStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : ReportStatus.valueOf(dbData.toUpperCase(Locale.ROOT));
    }
}
