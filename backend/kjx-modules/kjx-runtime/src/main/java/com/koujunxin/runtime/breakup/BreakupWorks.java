package com.koujunxin.runtime.breakup;

import com.koujunxin.runtime.api.RuntimeFailure;
import com.koujunxin.runtime.ledger.CanonicalJson;

/**
 * 分手结局修改器作品编码。可玩对姓名从计划「可玩示例是」句抄入，男名第二字 U+65F8。
 */
public final class BreakupWorks {

    public static final String CODE = "breakup-editor";
    public static final String COUPLE_ID = "asphyxia";
    /** 江雪 × 张旸奕 — 旸 = U+65F8 */
    public static final String COUPLE_LABEL = "\u6C5F\u96EA \u00D7 \u5F20\u65F8\u5955";
    public static final String DEMO_UNCOVERED = "演示模式暂未覆盖这句改写";
    public static final String ORIGINAL_SEND = "在历史里无法发送新消息。请切换到改写版。";
    public static final String ORIGINAL_EDIT = "这是已经发生的历史，无法改写。请切换到改写版。";
    public static final String LOCKED = "敬请期待";
    public static final String CONTENT_VERSION = "1.0.0";

    private BreakupWorks() {
    }

    public static boolean is(String workCode) {
        return CODE.equals(workCode);
    }

    public static void require(String workCode) {
        if (!is(workCode)) {
            throw RuntimeFailure.badRequest("WORK_NOT_SUPPORTED", "not breakup-editor");
        }
    }

    public static boolean trialPayload(String payloadJson) {
        return CanonicalJson.parse(payloadJson).path("trial").asBoolean(false);
    }
}
