package com.facecook.report.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Locale;

/**
 * {@link ReportStatus} ↔ DB 문자열 변환. 테이블의 값과 기본값(V1의 {@code DEFAULT 'pending'})이 소문자다.
 * {@code @Enumerated(EnumType.STRING)}은 enum 이름({@code PENDING})을 그대로 쓰므로 이 값과 맞지 않는다.
 * {@code Report.status} 필드에 {@code @Convert}로 붙어 있다. 로케일 문제는 {@code ReportResponse} 설명과 같다.
 */
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
