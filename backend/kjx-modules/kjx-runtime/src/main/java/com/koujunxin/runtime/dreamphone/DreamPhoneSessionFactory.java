package com.koujunxin.runtime.dreamphone;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.koujunxin.runtime.ledger.CanonicalJson;

public final class DreamPhoneSessionFactory {

    public String initialPayload(boolean trial) {
        ObjectNode payload = CanonicalJson.MAPPER.createObjectNode();
        payload.put("trial", trial);
        payload.put("work", DreamPhoneWorks.CODE);
        payload.put("screen", "pageLauncher");
        payload.put("playerName", DreamPhoneWorks.PLAYER);
        payload.put("characterName", DreamPhoneWorks.CHARACTER);
        payload.put("currentCharId", DreamPhoneWorks.CHARACTER_ID);
        payload.put("wallet", DreamPhoneWorks.WALLET);
        payload.put("trialStep", 0);
        payload.put("fontScale", "md");
        payload.put("chatBg", "default");
        payload.put("setupDone", false);
        payload.put("momentCount", 0);
        payload.set("characters", characters());
        payload.set("chatHistory", chatHistory(trial));
        payload.set("messages", CanonicalJson.MAPPER.createArrayNode());
        payload.set("moments", moments(trial));
        payload.put("momentCount", payload.path("moments").size());
        payload.set("memos", memos(trial));
        payload.set("photos", photos(trial));
        payload.set("walletLedger", CanonicalJson.MAPPER.createArrayNode());
        payload.set("charWallets", charWallets());
        payload.set("persona", persona());
        payload.set("gameRecords", CanonicalJson.MAPPER.createObjectNode());
        payload.set("orders", CanonicalJson.MAPPER.createArrayNode());
        payload.set("musicTracks", CanonicalJson.MAPPER.createArrayNode());
        payload.set("calls", CanonicalJson.MAPPER.createArrayNode());
        payload.put("unread", 0);
        return CanonicalJson.write(payload);
    }

    private static ArrayNode characters() {
        ArrayNode list = CanonicalJson.MAPPER.createArrayNode();
        ObjectNode guxi = CanonicalJson.MAPPER.createObjectNode();
        guxi.put("id", DreamPhoneWorks.CHARACTER_ID);
        guxi.put("name", DreamPhoneWorks.CHARACTER);
        guxi.put("emoji", "\uD83C\uDFAE");
        guxi.put("color", "#6D81A1");
        guxi.put("avatar", "");
        list.add(guxi);
        return list;
    }

    private static ObjectNode chatHistory(boolean trial) {
        ObjectNode history = CanonicalJson.MAPPER.createObjectNode();
        ArrayNode thread = CanonicalJson.MAPPER.createArrayNode();
        thread.add(msg("assistant", "text", "\u5728\u3002\u521A\u60F3\u5230\u4F60\uFF0C\u4ECA\u5929\u987A\u5229\u5417\uFF1F", System.currentTimeMillis()));
        if (trial) {
            // trial starts with greeting only; scripted user line is first covered send
        }
        history.set(DreamPhoneWorks.CHARACTER_ID, thread);
        return history;
    }

    private static ArrayNode moments(boolean trial) {
        ArrayNode moments = CanonicalJson.MAPPER.createArrayNode();
        long now = System.currentTimeMillis();
        moments.add(moment("m1", DreamPhoneWorks.CHARACTER_ID, DreamPhoneWorks.CHARACTER, "\uD83C\uDFAE",
                "\u665A\u98CE\u5F88\u597D\uFF0C\u5E0C\u671B\u4F60\u4E5F\u65E9\u70B9\u6536\u5DE5\u3002", now - 3600_000L));
        if (!trial) {
            moments.add(moment("m2", DreamPhoneWorks.CHARACTER_ID, DreamPhoneWorks.CHARACTER, "\uD83C\uDFAE",
                    "\u4E2D\u5348\u5403\u4E86\u9762\uFF0C\u4E0B\u5348\u7EE7\u7EED\u6253\u6E38\u620F\u3002", now - 7200_000L));
            moments.add(moment("m3", DreamPhoneWorks.CHARACTER_ID, DreamPhoneWorks.CHARACTER, "\uD83C\uDFAE",
                    "\u770B\u5230\u4E00\u53EA\u732B\uFF0C\u60F3\u8D77\u4F60\u8BF4\u8FC7\u559C\u6B22\u6BDB\u8338\u8361\u8361\u7684\u3002", now - 10800_000L));
            moments.add(moment("m4", DreamPhoneWorks.PLAYER, DreamPhoneWorks.PLAYER, "\uD83D\uDC64",
                    "\u4ECA\u5929\u5929\u6C14\u4E0D\u9519\u3002", now - 14400_000L));
            moments.add(moment("m5", DreamPhoneWorks.CHARACTER_ID, DreamPhoneWorks.CHARACTER, "\uD83C\uDFAE",
                    "\u665A\u4E0A\u8981\u4E0D\u8981\u4E00\u8D77\u542C\u6B4C\uFF1F", now - 18000_000L));
        }
        return moments;
    }

    private static ObjectNode moment(String id, String charId, String author, String emoji, String content, long time) {
        ObjectNode row = CanonicalJson.MAPPER.createObjectNode();
        row.put("id", id);
        row.put("charId", charId);
        row.put("author", author);
        row.put("charEmoji", emoji);
        row.put("content", content);
        row.put("time", time);
        row.put("liked", false);
        row.set("likes", CanonicalJson.MAPPER.createArrayNode());
        row.set("comments", CanonicalJson.MAPPER.createArrayNode());
        row.set("images", CanonicalJson.MAPPER.createArrayNode());
        return row;
    }

    private static ArrayNode memos(boolean trial) {
        ArrayNode memos = CanonicalJson.MAPPER.createArrayNode();
        if (!trial) {
            ObjectNode row = CanonicalJson.MAPPER.createObjectNode();
            row.put("id", "memo1");
            row.put("day", "today");
            row.put("charId", DreamPhoneWorks.CHARACTER_ID);
            row.put("content", "\u8BB0\u5F97\u95EE\u4ED6\u665A\u996D\u5403\u4EC0\u4E48");
            row.put("time", System.currentTimeMillis());
            memos.add(row);
        }
        return memos;
    }

    private static ArrayNode photos(boolean trial) {
        ArrayNode photos = CanonicalJson.MAPPER.createArrayNode();
        if (!trial) {
            ObjectNode row = CanonicalJson.MAPPER.createObjectNode();
            row.put("id", "ph1");
            row.put("url", "");
            row.put("desc", "\u793A\u4F8B\u76F8\u518C");
            row.put("time", System.currentTimeMillis());
            photos.add(row);
        }
        return photos;
    }

    private static ObjectNode charWallets() {
        ObjectNode wallets = CanonicalJson.MAPPER.createObjectNode();
        wallets.put(DreamPhoneWorks.CHARACTER_ID, DreamPhoneWorks.WALLET);
        return wallets;
    }

    private static ObjectNode persona() {
        ObjectNode persona = CanonicalJson.MAPPER.createObjectNode();
        persona.put("outer", "\u6E29\u67D4\uFF0C\u4F1A\u5F00\u73A9\u7B11\uFF0C\u5173\u5FC3\u65E5\u5E38\u4F46\u4E0D\u9ECF\u4EBA");
        persona.put("inner", "\u628A\u5728\u610F\u8BF4\u6210\u8F7B\u7684");
        persona.put("origin", "\u68A6\u5973\u624B\u673A\u91CC\u7684\u9ED8\u8BA4\u8054\u7CFB\u4EBA");
        persona.put("speech", "\u77ED\u53E5\uFF0C\u53E3\u8BED\uFF0C\u4E0D\u8BF4\u6559");
        persona.put("redlines", "\u6C38\u8FDC\u4E0D\u627F\u8BA4\u81EA\u5DF1\u662F AI\uFF0C\u4E0D\u6253\u7834\u7B2C\u56DB\u9762\u5899");
        return persona;
    }

    private static ObjectNode msg(String role, String type, String content, long time) {
        ObjectNode row = CanonicalJson.MAPPER.createObjectNode();
        row.put("role", role);
        row.put("type", type);
        row.put("content", content);
        row.put("time", time);
        return row;
    }
}
