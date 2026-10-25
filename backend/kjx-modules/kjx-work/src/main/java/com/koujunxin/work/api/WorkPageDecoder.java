package com.koujunxin.work.api;

import java.nio.charset.StandardCharsets;

/**
 * C 端作品页标题解码。直播库若把中文落成问号或 latin1 误读，仍返回计划中的中文名。
 */
public final class WorkPageDecoder {

    public static final String BREAKUP_CODE = "breakup-editor";
    public static final String BREAKUP_NAME = "分手结局修改器";
    public static final String BREAKUP_SUMMARY = "如果那句话没说出口，他们还会分手吗？今天，帮 TA 改写曾经的选择。";

    private WorkPageDecoder() {
    }

    public static String name(String workCode, String raw) {
        if (!BREAKUP_CODE.equals(workCode)) {
            return raw;
        }
        String recovered = recover(raw);
        return recovered != null && recovered.contains("分手") ? recovered : BREAKUP_NAME;
    }

    public static String summary(String workCode, String raw) {
        if (!BREAKUP_CODE.equals(workCode)) {
            return raw;
        }
        String recovered = recover(raw);
        return recovered != null && recovered.contains("那句话") ? recovered : BREAKUP_SUMMARY;
    }

    static String recover(String raw) {
        if (raw == null || raw.isBlank()) {
            return raw;
        }
        if (raw.contains("分手") || raw.contains("那句话")) {
            return raw;
        }
        try {
            return new String(raw.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
        } catch (RuntimeException ignored) {
            return raw;
        }
    }
}
