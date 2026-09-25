package com.facecook.report.entity;

/** 신고 상태. DB에는 {@link ReportStatusConverter}가 소문자({@code pending}/{@code reviewed})로 저장한다. */
public enum ReportStatus {
    PENDING,
    REVIEWED
}
