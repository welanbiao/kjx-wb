package com.koujunxin.runtime.breakup;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.koujunxin.runtime.api.RuntimeFailure;
import com.koujunxin.runtime.ledger.CanonicalJson;

/**
 * 消息级重演边界与 PACK 落库。标题和过场旁白冻结。
 */
public final class BreakupPackEngine {

    private static final Pattern PACK_BLOCK = Pattern.compile("«PACK»(.*?)«/PACK»", Pattern.DOTALL);
    private static final Pattern STATE_BLOCK = Pattern.compile("«STATE»(.*?)«/STATE»", Pattern.DOTALL);
    private static final Pattern REPLY = Pattern.compile("«REPLY:([^»]*)»");
    private static final Pattern NAR = Pattern.compile("«NAR:([^»]*)»");
    private static final int MAX_METRIC_DELTA = 15;

    private BreakupPackEngine() {
    }

    public static String normalizeMessageId(String msgId) {
        return String.valueOf(msgId == null ? "" : msgId).replaceFirst("_p\\d+$", "");
    }

    public static ObjectNode findNode(JsonNode bundle, String nodeId) {
        JsonNode nodes = bundle.path("nodes");
        if (!nodes.isArray()) {
            return null;
        }
        for (JsonNode node : nodes) {
            if (nodeId.equals(node.path("id").asText()) && node instanceof ObjectNode objectNode) {
                return objectNode;
            }
        }
        return null;
    }

    public static Boundary computeReplayBoundary(JsonNode bundle, String nodeId, String msgId) {
        msgId = normalizeMessageId(msgId);
        JsonNode nodes = bundle.path("nodes");
        if (!nodes.isArray()) {
            return null;
        }
        int nodeIdx = -1;
        int msgIdx = -1;
        for (int i = 0; i < nodes.size(); i++) {
            if (!nodeId.equals(nodes.get(i).path("id").asText())) {
                continue;
            }
            nodeIdx = i;
            JsonNode chat = nodes.get(i).path("chat");
            if (chat.isArray()) {
                for (int j = 0; j < chat.size(); j++) {
                    if (msgId.equals(chat.get(j).path("id").asText())) {
                        msgIdx = j;
                        break;
                    }
                }
            }
            break;
        }
        if (nodeIdx < 0 || msgIdx < 0) {
            return null;
        }
        String nextId = nextInterveneId(bundle, nodeId);
        if (nextId == null || nextId.isBlank()) {
            nextId = nodeId;
        }
        int nextIdx = nodeIdx;
        for (int i = 0; i < nodes.size(); i++) {
            if (nextId.equals(nodes.get(i).path("id").asText())) {
                nextIdx = i;
                break;
            }
        }
        int startNodeIdx = -1;
        int startMsgIdx = -1;
        JsonNode fromChat = nodes.get(nodeIdx).path("chat");
        if (msgIdx + 1 < fromChat.size()) {
            startNodeIdx = nodeIdx;
            startMsgIdx = msgIdx + 1;
        } else if (nodeIdx < nextIdx) {
            startNodeIdx = nodeIdx + 1;
            startMsgIdx = 0;
        }
        return new Boundary(
                nodeId,
                msgId,
                nodeIdx,
                msgIdx,
                nextId,
                nextIdx,
                startNodeIdx,
                startMsgIdx,
                nodes.get(nodeIdx).path("date").asText(""),
                nodes.get(nextIdx).path("date").asText(nodes.get(nodeIdx).path("date").asText("")),
                startNodeIdx >= 0);
    }

    public static String nextInterveneId(JsonNode bundle, String fromId) {
        JsonNode nodes = bundle.path("nodes");
        boolean passed = fromId == null || fromId.isBlank();
        if (!nodes.isArray()) {
            return null;
        }
        for (JsonNode node : nodes) {
            if (!passed) {
                if (fromId.equals(node.path("id").asText())) {
                    passed = true;
                }
                continue;
            }
            if ("intervene".equals(node.path("type").asText())) {
                return node.path("id").asText();
            }
        }
        return null;
    }

    public static List<String> nodeIdsInReplaySpan(JsonNode bundle, Boundary boundary) {
        List<String> ids = new ArrayList<>();
        if (boundary == null || !boundary.hasReplay()) {
            return ids;
        }
        JsonNode nodes = bundle.path("nodes");
        for (int i = boundary.startNodeIdx(); i <= boundary.nextIdx(); i++) {
            String id = nodes.get(i).path("id").asText();
            if (!ids.contains(id)) {
                ids.add(id);
            }
        }
        return ids;
    }

    public static void validateEditPack(JsonNode bundle, ObjectNode pack, String nodeId, String msgId) {
        if (pack == null) {
            throw RuntimeFailure.badRequest("PACK_INVALID", "模型未返回 PACK");
        }
        msgId = normalizeMessageId(msgId);
        Boundary boundary = computeReplayBoundary(bundle, nodeId, msgId);
        if (boundary == null) {
            throw RuntimeFailure.badRequest("PACK_INVALID", "找不到改写边界");
        }
        if (!nodeId.equals(pack.path("from").asText(nodeId))) {
            throw RuntimeFailure.badRequest("PACK_INVALID", "PACK.FROM 与改写节点不一致");
        }
        if (!boundary.nextId().equals(pack.path("next").asText(boundary.nextId()))) {
            throw RuntimeFailure.badRequest("PACK_INVALID", "PACK.NEXT 与下一可介入节点不一致");
        }
        String chatMode = pack.path("chat").path("mode").asText();
        if (boundary.hasReplay() && !"REPLACE".equals(chatMode)) {
            throw RuntimeFailure.badRequest("PACK_INVALID", "有重演段时必须 CHAT|REPLACE");
        }
        Set<String> covered = new LinkedHashSet<>();
        for (JsonNode line : pack.path("chat").path("lines")) {
            if (line.path("empty").asBoolean(false) || line.has("nodeId")) {
                covered.add(line.path("nodeId").asText());
            }
            if (!line.path("empty").asBoolean(false) && msgId.equals(line.path("id").asText())) {
                throw RuntimeFailure.badRequest("PACK_INVALID", "PACK 不应包含被改消息本身");
            }
        }
        if (boundary.hasReplay()) {
            for (String nid : nodeIdsInReplaySpan(bundle, boundary)) {
                if (!covered.contains(nid)) {
                    throw RuntimeFailure.badRequest("PACK_INVALID", "重演段未覆盖节点：" + nid);
                }
            }
        }
        pack.put("from", nodeId);
        pack.put("next", boundary.nextId());
    }

    public static void applyPack(ObjectNode session, ObjectNode pack, String nodeId, String msgId) {
        ObjectNode bundle = (ObjectNode) session.get("rewritten");
        msgId = normalizeMessageId(msgId);
        Boundary boundary = computeReplayBoundary(bundle, nodeId, msgId);
        if (boundary == null) {
            throw RuntimeFailure.badRequest("PACK_INVALID", "CHAT|REPLACE 需要改写消息上下文");
        }
        String nextId = boundary.nextId();
        if (nextId == null || nextId.isBlank()) {
            throw RuntimeFailure.badRequest("PACK_INVALID", "缺少 NEXT，拒绝落库");
        }
        if ("REPLACE".equals(pack.path("chat").path("mode").asText())) {
            Map<String, ArrayNode> byNode = new LinkedHashMap<>();
            Set<String> emptyNodes = new LinkedHashSet<>();
            for (JsonNode ln : pack.path("chat").path("lines")) {
                String nid = ln.path("nodeId").asText();
                if (ln.path("empty").asBoolean(false)) {
                    emptyNodes.add(nid);
                    byNode.put(nid, CanonicalJson.MAPPER.createArrayNode());
                    continue;
                }
                if (emptyNodes.contains(nid)) {
                    continue;
                }
                byNode.computeIfAbsent(nid, key -> CanonicalJson.MAPPER.createArrayNode()).add(applyFlags(ln));
            }
            ObjectNode fromNode = findNode(bundle, boundary.nodeId());
            if (fromNode != null) {
                ArrayNode head = CanonicalJson.MAPPER.createArrayNode();
                JsonNode chat = fromNode.path("chat");
                for (int i = 0; i <= boundary.msgIdx() && i < chat.size(); i++) {
                    head.add(chat.get(i).deepCopy());
                }
                ArrayNode tail = byNode.getOrDefault(boundary.nodeId(), CanonicalJson.MAPPER.createArrayNode());
                head.addAll(tail);
                fromNode.set("chat", head);
                byNode.remove(boundary.nodeId());
            }
            for (String nid : nodeIdsInReplaySpan(bundle, boundary)) {
                if (nid.equals(boundary.nodeId())) {
                    continue;
                }
                ObjectNode target = findNode(bundle, nid);
                if (target == null) {
                    continue;
                }
                if (emptyNodes.contains(nid)) {
                    target.set("chat", CanonicalJson.MAPPER.createArrayNode());
                } else if (byNode.containsKey(nid)) {
                    target.set("chat", byNode.get(nid));
                }
            }
        }
        if ("REPLACE".equals(pack.path("narr").path("mode").asText())) {
            for (JsonNode item : pack.path("narr").path("items")) {
                ObjectNode node = findNode(bundle, item.path("nodeId").asText());
                if (node != null) {
                    node.put("narr", item.path("narr").asText());
                }
            }
        }
        replaceRange(bundle, "moments", pack.path("moment"), boundary.fromDate(), boundary.nextDate(), false);
        replaceRange(bundle, "notes", pack.path("note"), boundary.fromDate(), boundary.nextDate(), true);
        replaceRange(bundle, "bills", pack.path("bill"), boundary.fromDate(), boundary.nextDate(), true);
        replaceRange(bundle, "photos", pack.path("photo"), boundary.fromDate(), boundary.nextDate(), true);
        lockNodesAfter(session, nextId);
    }

    public static void rewriteMessageText(ObjectNode session, String nodeId, String msgId, String newText) {
        ObjectNode node = findNode(session.path("rewritten"), nodeId);
        if (node == null) {
            throw RuntimeFailure.badRequest("PACK_INVALID", "找不到改写节点");
        }
        ArrayNode chat = node.withArray("chat");
        boolean found = false;
        for (int i = 0; i < chat.size(); i++) {
            if (msgId.equals(chat.get(i).path("id").asText()) && chat.get(i) instanceof ObjectNode objectNode) {
                objectNode.put("text", newText);
                objectNode.put("rewritten", true);
                found = true;
                break;
            }
        }
        if (!found) {
            throw RuntimeFailure.badRequest("PACK_INVALID", "找不到改写消息");
        }
    }

    public static ObjectNode packFromBranch(JsonNode branch) {
        ObjectNode pack = emptyPack(branch.path("from").asText(), branch.path("next").asText());
        pack.with("chat").put("mode", "REPLACE");
        ArrayNode lines = pack.with("chat").withArray("lines");
        for (JsonNode ln : branch.path("chat")) {
            if (ln.path("empty").asBoolean(false)) {
                ObjectNode empty = CanonicalJson.MAPPER.createObjectNode();
                empty.put("nodeId", ln.path("nodeId").asText());
                empty.put("empty", true);
                lines.add(empty);
            } else {
                ObjectNode row = CanonicalJson.MAPPER.createObjectNode();
                row.put("nodeId", ln.path("nodeId").asText());
                row.put("id", ln.path("id").asText());
                row.put("side", ln.path("side").asText());
                row.put("time", ln.path("time").asText(""));
                row.put("text", ln.path("text").asText(""));
                row.set("flags", CanonicalJson.MAPPER.createArrayNode().add("branch"));
                lines.add(row);
            }
        }
        if (branch.path("narrs").isArray() && !branch.path("narrs").isEmpty()) {
            pack.with("narr").put("mode", "REPLACE");
            pack.with("narr").set("items", branch.get("narrs").deepCopy());
        }
        return pack;
    }

    public static ObjectNode packFromOriginal(JsonNode bundle, String nodeId, String msgId, JsonNode meta) {
        Boundary boundary = computeReplayBoundary(bundle, nodeId, msgId);
        if (boundary == null) {
            return null;
        }
        ObjectNode pack = emptyPack(nodeId, boundary.nextId());
        pack.with("chat").put("mode", boundary.hasReplay() ? "REPLACE" : "KEEP");
        if (meta != null && meta.path("narrs").isArray() && !meta.path("narrs").isEmpty()) {
            pack.with("narr").put("mode", "REPLACE");
            pack.with("narr").set("items", meta.get("narrs").deepCopy());
        }
        if (!boundary.hasReplay()) {
            return pack;
        }
        JsonNode nodes = bundle.path("nodes");
        ArrayNode lines = pack.with("chat").withArray("lines");
        for (int i = boundary.startNodeIdx(); i <= boundary.nextIdx(); i++) {
            JsonNode node = nodes.get(i);
            JsonNode chat = node.path("chat");
            if (!chat.isArray() || chat.isEmpty()) {
                ObjectNode empty = CanonicalJson.MAPPER.createObjectNode();
                empty.put("nodeId", node.path("id").asText());
                empty.put("empty", true);
                lines.add(empty);
                continue;
            }
            int startJ = i == boundary.startNodeIdx() ? boundary.startMsgIdx() : 0;
            for (int j = startJ; j < chat.size(); j++) {
                JsonNode m = chat.get(j);
                ObjectNode row = CanonicalJson.MAPPER.createObjectNode();
                row.put("nodeId", node.path("id").asText());
                row.put("id", m.path("id").asText());
                row.put("side", m.path("side").asText());
                row.put("time", m.path("time").asText(""));
                row.put("text", m.path("text").asText(""));
                row.set("flags", CanonicalJson.MAPPER.createArrayNode());
                lines.add(row);
            }
        }
        return pack;
    }

    public static void rejectAiFailure(String raw) {
        if (raw == null || raw.isBlank()) {
            throw RuntimeFailure.badRequest("AI_FAILED", "模型没有返回内容");
        }
        String trimmed = raw.trim();
        if (!trimmed.startsWith("{")) {
            return;
        }
        try {
            JsonNode node = CanonicalJson.parse(trimmed);
            if (node.hasNonNull("code") && !node.hasNonNull("text") && !node.hasNonNull("content")
                    && !node.has("from")) {
                String message = node.path("message").asText("模型调用失败");
                throw RuntimeFailure.badRequest(node.path("code").asText("AI_FAILED"), message);
            }
        } catch (RuntimeFailure ex) {
            throw ex;
        } catch (RuntimeException ignored) {
            // not a structured error payload
        }
    }

    public static List<String> repliesOrPlain(String raw, List<String> parsed) {
        if (parsed != null && !parsed.isEmpty()) {
            return parsed;
        }
        String text = unwrapAssistantText(raw);
        if (text == null || text.isBlank() || text.trim().startsWith("{")) {
            return List.of();
        }
        return List.of(text.trim());
    }

    public static void appendSendTurn(ObjectNode session, String userText, List<String> replies) {
        ObjectNode node = lastChatNode(session);
        ArrayNode chat = node.withArray("chat");
        String side = session.path("side").asText("a");
        String other = "b".equals(side) ? "a" : "b";
        if (userText != null && !userText.isBlank()) {
            chat.add(chatLine("send-u-" + nextId(), side, userText, false));
        }
        appendReplies(chat, other, replies);
    }

    public static void appendModelReplies(ObjectNode session, List<String> replies) {
        if (replies == null || replies.isEmpty()) {
            return;
        }
        ObjectNode node = lastChatNode(session);
        String side = session.path("side").asText("a");
        String other = "b".equals(side) ? "a" : "b";
        appendReplies(node.withArray("chat"), other, replies);
    }

    private static void appendReplies(ArrayNode chat, String side, List<String> replies) {
        if (replies == null) {
            return;
        }
        for (String reply : replies) {
            if (reply == null || reply.isBlank()) {
                continue;
            }
            chat.add(chatLine("send-r-" + nextId(), side, reply, true));
        }
    }

    private static ObjectNode lastChatNode(ObjectNode session) {
        JsonNode rewritten = session.path("rewritten");
        JsonNode nodes = rewritten.path("nodes");
        if (nodes.isArray() && !nodes.isEmpty() && nodes.get(nodes.size() - 1) instanceof ObjectNode last) {
            return last;
        }
        ObjectNode bundle = session.with("rewritten");
        ArrayNode created = bundle.withArray("nodes");
        ObjectNode node = CanonicalJson.MAPPER.createObjectNode();
        node.put("id", "send");
        node.set("chat", CanonicalJson.MAPPER.createArrayNode());
        created.add(node);
        return node;
    }

    private static ObjectNode chatLine(String id, String side, String text, boolean editable) {
        ObjectNode row = CanonicalJson.MAPPER.createObjectNode();
        row.put("id", id);
        row.put("side", side);
        row.put("text", text);
        row.put("time", "");
        row.put("edit", editable);
        return row;
    }

    private static String nextId() {
        return Integer.toHexString((int) (System.nanoTime() & 0xfffffff));
    }

    public static ParsedModel parseModelOutput(String raw) {
        raw = unwrapAssistantText(raw);
        List<String> replies = new ArrayList<>();
        Matcher reply = REPLY.matcher(raw == null ? "" : raw);
        while (reply.find() && replies.size() < 3) {
            String text = reply.group(1).trim();
            if (!text.isBlank()) {
                replies.add(text);
            }
        }
        Matcher nar = NAR.matcher(raw == null ? "" : raw);
        String ripple = nar.find() ? nar.group(1).trim() : "";
        ObjectNode pack = parsePackText(raw);
        ObjectNode state = parseStateText(raw);
        return new ParsedModel(replies, ripple, pack, state);
    }

    public static void applyState(ObjectNode session, ObjectNode state) {
        if (state == null || state.isEmpty()) {
            return;
        }
        applyMetric(session, "bp", state.path("BP").asInt(session.path("bp").asInt()));
        applyMetric(session, "lm", state.path("LM").asInt(session.path("lm").asInt()));
        applyMetric(session, "lf", state.path("LF").asInt(session.path("lf").asInt()));
        if (state.has("TIME")) {
            session.put("timeDays", Math.max(0, state.path("TIME").asInt(0)));
        }
        if (state.has("FLAG")) {
            ArrayNode flags = session.withArray("flags");
            String flag = state.path("FLAG").asText("");
            if (!flag.isBlank()) {
                flags.removeAll();
                flags.add(flag);
            }
        }
    }

    public static void applyMetric(ObjectNode session, String key, int next) {
        int cur = session.path(key).asInt(0);
        int delta = next - cur;
        if (delta > MAX_METRIC_DELTA) {
            next = cur + MAX_METRIC_DELTA;
        }
        if (delta < -MAX_METRIC_DELTA) {
            next = cur - MAX_METRIC_DELTA;
        }
        if (next == 0 && cur > 8) {
            next = Math.max(1, cur - MAX_METRIC_DELTA);
        }
        if (next == 100 && cur < 92) {
            next = Math.min(99, cur + MAX_METRIC_DELTA);
        }
        session.put(key, Math.max(0, Math.min(100, next)));
    }

    public static boolean titlesIntact(ObjectNode session) {
        JsonNode frozen = session.path("titles");
        JsonNode nodes = session.path("rewritten").path("nodes");
        if (!frozen.isArray() || !nodes.isArray() || frozen.size() != nodes.size()) {
            return false;
        }
        for (int i = 0; i < frozen.size(); i++) {
            if (!frozen.get(i).path("title").asText().equals(nodes.get(i).path("title").asText())) {
                return false;
            }
            if (!CanonicalJson.write(frozen.get(i).path("bridgeNarr"))
                    .equals(CanonicalJson.write(nodes.get(i).path("bridgeNarr")))) {
                return false;
            }
        }
        return true;
    }

    static void restoreFrozenTitles(ObjectNode session) {
        JsonNode frozen = session.path("titles");
        JsonNode nodes = session.path("rewritten").path("nodes");
        if (!frozen.isArray() || !nodes.isArray()) {
            return;
        }
        for (int i = 0; i < frozen.size() && i < nodes.size(); i++) {
            if (nodes.get(i) instanceof ObjectNode node) {
                node.put("title", frozen.get(i).path("title").asText());
                node.set("bridgeNarr", frozen.get(i).path("bridgeNarr").deepCopy());
            }
        }
    }

    static void lockNodesAfter(ObjectNode session, String nextId) {
        if (nextId == null || nextId.isBlank()) {
            return;
        }
        JsonNode nodes = session.path("rewritten").path("nodes");
        boolean passed = false;
        if (!nodes.isArray()) {
            return;
        }
        for (JsonNode item : nodes) {
            if (!(item instanceof ObjectNode node)) {
                continue;
            }
            if (!passed) {
                node.put("pending", false);
                if (nextId.equals(node.path("id").asText())) {
                    passed = true;
                }
                continue;
            }
            node.put("pending", true);
        }
        session.put("frontier", nextId);
    }

    private static ObjectNode emptyPack(String from, String next) {
        ObjectNode pack = CanonicalJson.MAPPER.createObjectNode();
        pack.put("from", from);
        pack.put("next", next);
        for (String key : List.of("chat", "narr", "moment", "note", "bill", "photo")) {
            ObjectNode section = pack.with(key);
            section.put("mode", "KEEP");
            section.set("lines", CanonicalJson.MAPPER.createArrayNode());
            section.set("items", CanonicalJson.MAPPER.createArrayNode());
        }
        return pack;
    }

    private static ObjectNode applyFlags(JsonNode ln) {
        ObjectNode msg = CanonicalJson.MAPPER.createObjectNode();
        msg.put("id", ln.path("id").asText());
        msg.put("side", ln.path("side").asText());
        msg.put("text", ln.path("text").asText());
        msg.put("time", ln.path("time").asText(""));
        List<String> flags = new ArrayList<>();
        for (JsonNode flag : ln.path("flags")) {
            flags.add(flag.asText());
        }
        msg.put("rewritten", flags.contains("rewritten"));
        msg.put("seen", flags.contains("seen"));
        msg.put("recall", flags.contains("recall"));
        msg.put("silence", flags.contains("silence"));
        msg.put("branch", flags.contains("branch"));
        if (flags.contains("recall") || flags.contains("silence")) {
            msg.put("text", "");
        }
        return msg;
    }

    private static void replaceRange(
            ObjectNode bundle, String field, JsonNode section, String start, String end, boolean sided) {
        if (!"REPLACE".equals(section.path("mode").asText())) {
            return;
        }
        if (!sided) {
            ArrayNode kept = CanonicalJson.MAPPER.createArrayNode();
            for (JsonNode item : bundle.path(field)) {
                if (!inRange(item.path("date").asText(""), start, end)) {
                    kept.add(item.deepCopy());
                }
            }
            for (JsonNode item : section.path("items")) {
                kept.add(item.deepCopy());
            }
            bundle.set(field, kept);
            return;
        }
        ObjectNode bucket = bundle.with(field);
        Map<String, ArrayNode> incoming = Map.of(
                "a", CanonicalJson.MAPPER.createArrayNode(),
                "b", CanonicalJson.MAPPER.createArrayNode());
        for (JsonNode item : section.path("items")) {
            String side = item.path("side").asText("a");
            incoming.getOrDefault(side, incoming.get("a")).add(item.deepCopy());
        }
        for (String side : List.of("a", "b")) {
            ArrayNode kept = CanonicalJson.MAPPER.createArrayNode();
            for (JsonNode item : bucket.path(side)) {
                if (!inRange(item.path("date").asText(""), start, end)) {
                    kept.add(item.deepCopy());
                }
            }
            kept.addAll(incoming.get(side));
            bucket.set(side, kept);
        }
    }

    private static boolean inRange(String date, String start, String end) {
        if (date == null || date.isBlank()) {
            return false;
        }
        if (start != null && !start.isBlank() && date.compareTo(start) < 0) {
            return false;
        }
        return end == null || end.isBlank() || date.compareTo(end) <= 0;
    }

    static String unwrapAssistantText(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String trimmed = raw.trim();
        if (!trimmed.startsWith("{")) {
            return trimmed;
        }
        try {
            JsonNode node = CanonicalJson.parse(trimmed);
            if (node.hasNonNull("text")) {
                return node.path("text").asText();
            }
            if (node.hasNonNull("content")) {
                return node.path("content").asText();
            }
        } catch (RuntimeException ignored) {
            return trimmed;
        }
        return trimmed;
    }

    private static ObjectNode parsePackText(String raw) {
        if (raw == null) {
            return null;
        }
        Matcher pack = PACK_BLOCK.matcher(raw);
        if (pack.find()) {
            return parsePackBody(pack.group(1));
        }
        try {
            JsonNode json = CanonicalJson.parse(raw);
            if (json.has("from") || json.has("chat")) {
                return json instanceof ObjectNode objectNode ? objectNode : null;
            }
        } catch (RuntimeException ignored) {
            return null;
        }
        return null;
    }

    private static ObjectNode parsePackBody(String body) {
        ObjectNode pack = emptyPack("", "");
        for (String rawLine : body.split("\n")) {
            String line = rawLine.trim();
            if (line.isBlank()) {
                continue;
            }
            if (line.regionMatches(true, 0, "FROM:", 0, 5)) {
                pack.put("from", line.substring(5).trim());
            } else if (line.regionMatches(true, 0, "NEXT:", 0, 5)) {
                pack.put("next", line.substring(5).trim());
            } else if (line.equalsIgnoreCase("«CHAT|REPLACE»")) {
                pack.with("chat").put("mode", "REPLACE");
            } else if (line.equalsIgnoreCase("«CHAT|KEEP»")) {
                pack.with("chat").put("mode", "KEEP");
            } else if (line.equalsIgnoreCase("«NARR|REPLACE»")) {
                pack.with("narr").put("mode", "REPLACE");
            } else if (line.startsWith("«L:")) {
                String[] p = line.replace("«L:", "").replace("»", "").split("\\|", -1);
                if (p.length >= 2 && "EMPTY".equals(p[1])) {
                    ObjectNode empty = CanonicalJson.MAPPER.createObjectNode();
                    empty.put("nodeId", p[0]);
                    empty.put("empty", true);
                    pack.with("chat").withArray("lines").add(empty);
                } else if (p.length >= 5) {
                    ObjectNode row = CanonicalJson.MAPPER.createObjectNode();
                    row.put("nodeId", p[0]);
                    row.put("id", p[1]);
                    row.put("side", p[2]);
                    row.put("time", p[3]);
                    row.put("text", p[4]);
                    ArrayNode flags = CanonicalJson.MAPPER.createArrayNode();
                    if (p.length > 5) {
                        for (String flag : p[5].split(",")) {
                            if (!flag.isBlank()) {
                                flags.add(flag);
                            }
                        }
                    }
                    row.set("flags", flags);
                    pack.with("chat").withArray("lines").add(row);
                }
            } else if (line.startsWith("«R:")) {
                String[] r = line.replace("«R:", "").replace("»", "").split("\\|", 2);
                ObjectNode item = CanonicalJson.MAPPER.createObjectNode();
                item.put("nodeId", r[0]);
                item.put("narr", r.length > 1 ? r[1] : "");
                pack.with("narr").withArray("items").add(item);
            }
        }
        return pack;
    }

    private static ObjectNode parseStateText(String raw) {
        ObjectNode state = CanonicalJson.MAPPER.createObjectNode();
        if (raw == null) {
            return state;
        }
        Matcher block = STATE_BLOCK.matcher(raw);
        String body = block.find() ? block.group(1) : "";
        for (String line : body.split("\n")) {
            int idx = line.indexOf(':');
            if (idx <= 0) {
                continue;
            }
            state.put(line.substring(0, idx).trim().toUpperCase(), line.substring(idx + 1).trim());
        }
        return state;
    }

    public record Boundary(
            String nodeId,
            String msgId,
            int nodeIdx,
            int msgIdx,
            String nextId,
            int nextIdx,
            int startNodeIdx,
            int startMsgIdx,
            String fromDate,
            String nextDate,
            boolean hasReplay
    ) {
    }

    public record ParsedModel(List<String> replies, String ripple, ObjectNode pack, ObjectNode state) {
    }
}
