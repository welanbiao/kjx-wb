package com.koujunxin.runtime.breakup;

import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.koujunxin.runtime.ledger.CanonicalJson;

/**
 * 软话/硬话世界联动：只改侧线预览、未读和便签说明，不再请求模型。
 */
public final class BreakupWorldRules {

    static final Pattern SOFT = Pattern.compile("对不起|抱歉|想你|爱你|回来|别走|在吗|认真|不是气话|道歉");
    static final Pattern HARSH = Pattern.compile("滚|随便|无所谓|别联系|滚吧|(?<!出)分了");

    private BreakupWorldRules() {
    }

    public static String tone(String text) {
        String value = text == null ? "" : text;
        if (SOFT.matcher(value).find()) {
            return "soft";
        }
        if (HARSH.matcher(value).find()) {
            return "harsh";
        }
        return "none";
    }

    public static boolean apply(ObjectNode session, String newText) {
        String kind = tone(newText);
        if ("none".equals(kind)) {
            return false;
        }
        ObjectNode world = session.has("world") && session.get("world").isObject()
                ? (ObjectNode) session.get("world")
                : BreakupSessionFactory.emptyWorld();
        ObjectNode patches = world.with("threadPatches");
        String side = session.path("side").asText("a");
        ObjectNode juan = CanonicalJson.MAPPER.createObjectNode();
        if ("soft".equals(kind)) {
            if ("a".equals(side)) {
                juan.put("preview", "你们……和好了？");
                juan.put("unread", 1);
            } else {
                juan.put("preview", "等等，他刚才那条什么意思？");
                juan.put("unread", 2);
            }
            ArrayNode hints = world.withArray("noteHints");
            hints.add("未发送的草稿里，有一行字被删了又打回来。");
        } else {
            juan.put("preview", "对。就该这样。别回头。");
            juan.put("unread", 1);
        }
        juan.put("previewTime", "刚刚");
        patches.set("juan", juan);
        session.set("world", world);
        return true;
    }
}
