package kr.ac.hanseo.lostfound.common.time;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 시간 규칙 (명세 공통 규칙 "날짜·시간")
 * - DB와 엔티티는 한국 시간 LocalDateTime
 * - 응답은 항상 "+09:00"을 붙인 ISO 8601
 * - 요청은 "2026-11-21T14:30:00", "...+09:00", "...Z" 모두 받는다. 표기가 없으면 한국 시간으로 본다.
 */
public final class KstTime {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    public static final ZoneOffset OFFSET = ZoneOffset.ofHours(9);
    private static final DateTimeFormatter OUTPUT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private KstTime() {
    }

    public static String format(LocalDateTime kst) {
        return kst.withNano(0).atOffset(OFFSET).format(OUTPUT);
    }

    public static LocalDateTime parse(String text) {
        String value = text.trim();
        try {
            if (hasOffset(value)) {
                return OffsetDateTime.parse(value).atZoneSameInstant(ZONE).toLocalDateTime();
            }
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("날짜 형식이 올바르지 않습니다: " + text, e);
        }
    }

    private static boolean hasOffset(String value) {
        if (value.endsWith("Z") || value.endsWith("z")) {
            return true;
        }
        int t = value.indexOf('T');
        return t > 0 && (value.indexOf('+', t) > 0 || value.indexOf('-', t) > 0);
    }
}
