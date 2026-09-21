package com.koujunxin.member.normalize;

import com.koujunxin.member.api.MemberFailures;

/**
 * 抖音号规范化：trim、去前导 @、小写。用于三要素精确匹配。
 */
public final class DouyinIds {

    private DouyinIds() {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            throw MemberFailures.badRequest("douyinId 必填");
        }
        String value = raw.trim();
        while (value.startsWith("@")) {
            value = value.substring(1).trim();
        }
        value = value.toLowerCase();
        if (value.isBlank() || value.length() > 128) {
            throw MemberFailures.badRequest("douyinId 不合法");
        }
        return value;
    }
}
