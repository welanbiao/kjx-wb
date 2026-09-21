package com.koujunxin.member.unlock;

import java.util.LinkedHashMap;
import java.util.Map;

public record UnlockOutcome(int code, String message, Map<String, Object> data) {

    public static UnlockOutcome created(Map<String, Object> data) {
        return new UnlockOutcome(0, "", data);
    }

    public static UnlockOutcome already(Map<String, Object> data) {
        Map<String, Object> copy = new LinkedHashMap<>(data);
        copy.put("alreadyUnlocked", true);
        return new UnlockOutcome(1010, "已解锁，正在进入", copy);
    }

    public Map<String, Object> replayBody() {
        return data;
    }
}
