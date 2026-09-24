package com.facecook.report.dto;

import com.facecook.report.entity.Report;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class ReportResponseTest {

    @Test
    void statusIsLowercasedIndependentOfDefaultLocale() {
        Locale original = Locale.getDefault();
        // 터키어 로케일에서는 "I".toLowerCase()가 점 없는 "ı"가 된다.
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
        try {
            Report report = Report.create(1L, 2L, "SPAM", null, LocalDateTime.of(2026, 9, 30, 12, 0));

            assertThat(ReportResponse.from(report).status()).isEqualTo("pending");
        } finally {
            Locale.setDefault(original);
        }
    }
}
