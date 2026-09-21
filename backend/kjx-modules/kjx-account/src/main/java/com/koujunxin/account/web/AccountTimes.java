package com.koujunxin.account.web;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public final class AccountTimes {

    private static final ZoneOffset PLUS_8 = ZoneOffset.ofHours(8);
    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

    private AccountTimes() {
    }

    public static String format(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return OffsetDateTime.of(value, PLUS_8).format(ISO);
    }
}
