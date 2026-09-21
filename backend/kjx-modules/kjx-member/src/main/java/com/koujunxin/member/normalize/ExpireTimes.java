package com.koujunxin.member.normalize;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import com.koujunxin.member.api.MemberFailures;

/**
 * 到期时间：只输入日期时展开为当天 23:59:59.999+08:00。不得从月数/天数推导。
 */
public final class ExpireTimes {

    public static final ZoneOffset PLUS_8 = ZoneOffset.ofHours(8);
    public static final LocalTime END_OF_DAY = LocalTime.of(23, 59, 59, 999_000_000);
    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
    private static final DateTimeFormatter ISO_NO_MILLIS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    private ExpireTimes() {
    }

    public static LocalDateTime parseRequired(String raw) {
        if (raw == null || raw.isBlank()) {
            throw MemberFailures.badRequest("expireAt 必填");
        }
        String value = raw.trim();
        if (value.length() == 10) {
            LocalDate date = LocalDate.parse(value);
            return LocalDateTime.of(date, END_OF_DAY);
        }
        try {
            OffsetDateTime odt = parseOffset(value);
            LocalDateTime local = odt.withOffsetSameInstant(PLUS_8).toLocalDateTime();
            if (local.toLocalTime().equals(LocalTime.MIDNIGHT)) {
                return LocalDateTime.of(local.toLocalDate(), END_OF_DAY);
            }
            return local;
        } catch (DateTimeParseException ex) {
            throw MemberFailures.badRequest("expireAt 格式必须为 ISO-8601 +08:00");
        }
    }

    public static LocalDateTime startAt(LocalDateTime expireAt, int subscribedDays) {
        if (expireAt == null) {
            throw MemberFailures.badRequest("expireAt 必填");
        }
        if (subscribedDays < 0) {
            throw MemberFailures.badRequest("subscribedDays 不合法");
        }
        return expireAt.minusDays(subscribedDays);
    }

    public static String format(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return OffsetDateTime.of(value, PLUS_8).format(ISO);
    }

    private static OffsetDateTime parseOffset(String value) {
        try {
            return OffsetDateTime.parse(value, ISO);
        } catch (DateTimeParseException ignored) {
            return OffsetDateTime.parse(value, ISO_NO_MILLIS);
        }
    }
}
