package com.facecook.common.time;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 행사 시각의 기준. DB에 저장하고 화면에 보여주는 시각은 전부 한국 시간(KST)으로 만든다.
 *
 * <p>서버·DB는 UTC로 돌아가고 주입받는 {@link Clock}도 {@code systemUTC()}다(세션 만료처럼
 * 절대 시각이 필요한 계산에 쓴다). 사람이 보는 시각을 만들 때는 {@code LocalDateTime.now()}나
 * {@code LocalDateTime.now(clock)}를 직접 쓰지 말고 이 클래스를 거친다 — 그러면 UTC 값이
 * 섞여 저장되지 않는다. 예전에는 서비스 여러 곳에 같은 변환이 복사돼 있었고, 이를 거치지 않은
 * 곳(미션·가입 시각)만 UTC로 저장돼 9시간 어긋났다.</p>
 */
public final class EventTime {

    /** 행사 시간대. 저장·표시 시각의 기준이다. */
    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private EventTime() {
    }

    /** clock의 현재 순간을 한국 시간 기준 날짜·시각으로 돌려준다. */
    public static LocalDateTime now(Clock clock) {
        return LocalDateTime.ofInstant(clock.instant(), ZONE);
    }

    /** clock의 현재 순간을 한국 시간 기준 날짜로 돌려준다(예: "오늘" 통계, 일일 한도). */
    public static LocalDate today(Clock clock) {
        return LocalDate.ofInstant(clock.instant(), ZONE);
    }
}
