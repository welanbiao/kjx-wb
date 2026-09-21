package com.koujunxin.member.normalize;

import java.util.Locale;
import java.util.regex.Pattern;

import com.koujunxin.member.api.MemberFailures;

/**
 * 会员编码规范化：trim、去空格、大写。比对时去连字符。
 * 存储格式 {HOST_CODE}-{8 位随机}，随机码排除 O/0/I/1/L。
 */
public final class MemberCodes {

    public static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    public static final Pattern STORED = Pattern.compile("^[A-Z]{2,8}-[A-HJ-NP-Z2-9]{8}$");
    private static final Pattern HOST = Pattern.compile("^[A-Z]{2,8}$");

    private MemberCodes() {
    }

    public static String compact(String raw) {
        if (raw == null) {
            throw MemberFailures.mismatch();
        }
        String compact = raw.trim().replace(" ", "").replace("-", "").toUpperCase(Locale.ROOT);
        if (compact.isBlank()) {
            throw MemberFailures.mismatch();
        }
        return compact;
    }

    public static String storedForm(String hostCode, String random8) {
        String host = requireHostCode(hostCode);
        if (random8 == null || random8.length() != 8) {
            throw MemberFailures.badRequest("会员编码随机段必须为 8 位");
        }
        String upper = random8.toUpperCase(Locale.ROOT);
        for (int i = 0; i < upper.length(); i++) {
            if (ALPHABET.indexOf(upper.charAt(i)) < 0) {
                throw MemberFailures.badRequest("会员编码字符集非法");
            }
        }
        return host + "-" + upper;
    }

    public static String requireHostCode(String hostCode) {
        if (hostCode == null) {
            throw MemberFailures.badRequest("hostCode 必填");
        }
        String value = hostCode.trim().toUpperCase(Locale.ROOT);
        if (!HOST.matcher(value).matches()) {
            throw MemberFailures.badRequest("hostCode 不合法");
        }
        return value;
    }

    public static String optionalHostCode(String hostCode) {
        if (hostCode == null || hostCode.isBlank()) {
            return null;
        }
        return requireHostCode(hostCode);
    }

    public static boolean forbiddenChar(char ch) {
        return ch == 'O' || ch == '0' || ch == 'I' || ch == '1' || ch == 'L';
    }
}
