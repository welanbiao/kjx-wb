package com.koujunxin.runtime.session;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import java.util.function.Supplier;

import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.koujunxin.runtime.api.RuntimeAiPort;
import com.koujunxin.runtime.api.RuntimeFailure;
import com.koujunxin.runtime.api.SignedAsset;
import com.koujunxin.runtime.api.StageContent;
import com.koujunxin.runtime.api.WorkRuntimeManifest;
import com.koujunxin.runtime.api.WorkRuntimePort;
import com.koujunxin.runtime.api.dto.SessionRef;
import com.koujunxin.runtime.api.dto.SessionStateSnapshot;
import com.koujunxin.runtime.api.dto.StateMutation;
import com.koujunxin.runtime.api.dto.ToolExecutionCommand;
import com.koujunxin.runtime.api.dto.ToolResult;
import com.koujunxin.runtime.api.error.EntitlementRequiredException;
import com.koujunxin.runtime.api.port.EntitlementAccessPort;
import com.koujunxin.runtime.api.port.ToolExecutionPort;
import com.koujunxin.runtime.disclose.StageDisclosure;
import com.koujunxin.runtime.ledger.CanonicalJson;
import com.koujunxin.runtime.ledger.SaveSigner;
import com.koujunxin.runtime.sandbox.HostSanitizer;
import com.koujunxin.runtime.sandbox.SandboxGuard;
import com.koujunxin.runtime.breakup.BreakupSessionFactory;
import com.koujunxin.runtime.breakup.BreakupWorks;
import com.koujunxin.runtime.dreamphone.DreamPhoneSessionFactory;
import com.koujunxin.runtime.dreamphone.DreamPhoneWorks;
import com.koujunxin.runtime.sandbox.SandboxMethods;

/**
 * 会话、存档、分段披露与宿主代理。EXPIRED 不在此检查；只认 entitlement 事实。
 */
public final class RuntimeApplicationService {

    private final RuntimeStore store;
    private final SessionStateService sessions;
    private final EntitlementAccessPort entitlements;
    private final WorkRuntimePort works;
    private final StageDisclosure disclosure;
    private final SaveSigner signer;
    private final SandboxGuard sandbox;
    private final ToolExecutionPort tools;
    private final RuntimeAiPort ai;
    private final TransactionTemplate tx;
    private final BreakupSessionFactory breakupSessions;
    private final DreamPhoneSessionFactory dreamSessions;

    public RuntimeApplicationService(
            RuntimeStore store,
            SessionStateService sessions,
            EntitlementAccessPort entitlements,
            WorkRuntimePort works,
            SaveSigner signer,
            SandboxGuard sandbox,
            ToolExecutionPort tools,
            RuntimeAiPort ai,
            TransactionTemplate tx) {
        this(store, sessions, entitlements, works, signer, sandbox, tools, ai, tx, null, null);
    }

    public RuntimeApplicationService(
            RuntimeStore store,
            SessionStateService sessions,
            EntitlementAccessPort entitlements,
            WorkRuntimePort works,
            SaveSigner signer,
            SandboxGuard sandbox,
            ToolExecutionPort tools,
            RuntimeAiPort ai,
            TransactionTemplate tx,
            BreakupSessionFactory breakupSessions,
            DreamPhoneSessionFactory dreamSessions) {
        this.store = store;
        this.sessions = sessions;
        this.entitlements = entitlements;
        this.works = works;
        this.disclosure = new StageDisclosure(works);
        this.signer = signer;
        this.sandbox = sandbox;
        this.tools = tools;
        this.ai = ai;
        this.tx = tx;
        this.breakupSessions = breakupSessions;
        this.dreamSessions = dreamSessions;
    }

    private <T> T inTx(Supplier<T> action) {
        if (tx == null) {
            return action.get();
        }
        return tx.execute(status -> action.get());
    }

    public Map<String, Object> createSession(long accountId, String workCode) {
        return createSession(accountId, workCode, false);
    }

    public Map<String, Object> createSession(long accountId, String workCode, boolean trial) {
        if (trial && !BreakupWorks.is(workCode) && !DreamPhoneWorks.is(workCode)) {
            throw RuntimeFailure.badRequest("WORK_NOT_SUPPORTED", "trial is not open for this work");
        } else if (!trial) {
            requireEntitlement(accountId, workCode);
        }
        return inTx(() -> {
            if (BreakupWorks.is(workCode) || DreamPhoneWorks.is(workCode)) {
                for (SessionRecord existing : store.listSessions(accountId, workCode)) {
                    if (SessionStateService.ACTIVE.equals(existing.status())
                            && BreakupWorks.trialPayload(existing.payloadJson()) == trial) {
                        return sessionView(existing, true);
                    }
                }
                String payload;
                if (BreakupWorks.is(workCode)) {
                    if (breakupSessions == null) {
                        throw RuntimeFailure.badRequest("WORK_NOT_SUPPORTED", "breakup session factory missing");
                    }
                    payload = breakupSessions.initialPayload(trial);
                } else {
                    if (dreamSessions == null) {
                        throw RuntimeFailure.badRequest("WORK_NOT_SUPPORTED", "dream-phone session factory missing");
                    }
                    payload = dreamSessions.initialPayload(trial);
                }
                SessionRecord row = sessions.create(accountId, workCode, payload, "1");
                return sessionView(row, true);
            }
            SessionRecord row = sessions.create(accountId, workCode);
            return sessionView(row, true);
        });
    }

    public List<Map<String, Object>> listSessions(long accountId, String workCode) {
        boolean entitled = hasEntitlement(accountId, workCode);
        List<Map<String, Object>> out = new ArrayList<>();
        for (SessionRecord row : store.listSessions(accountId, workCode)) {
            if (entitled || BreakupWorks.trialPayload(row.payloadJson())) {
                out.add(sessionView(row, false));
            }
        }
        if (!entitled && out.isEmpty() && !BreakupWorks.is(workCode) && !DreamPhoneWorks.is(workCode)) {
            requireEntitlement(accountId, workCode);
        }
        return out;
    }

    public Map<String, Object> getSession(long accountId, String sessionId, String workCode) {
        SessionRef ref = new SessionRef(sessionId, workCode);
        sessions.requireOwner(accountId, ref);
        SessionStateSnapshot snapshot = sessions.load(ref);
        SessionRecord row = store.findSession(sessionId).orElseThrow(RuntimeFailure::crossSession);
        requirePlayAccess(accountId, workCode, row);
        Map<String, Object> view = sessionView(row, true);
        view.put("stage", snapshot.stage());
        view.put("schemaVersion", snapshot.schemaVersion());
        view.put("payloadJson", snapshot.payloadJson());
        return view;
    }

    public Map<String, Object> apply(long accountId, String sessionId, String workCode, StateMutation mutation) {
        SessionRef ref = new SessionRef(sessionId, workCode);
        sessions.requireOwner(accountId, ref);
        SessionRecord current = store.findSession(sessionId).orElseThrow(RuntimeFailure::crossSession);
        requirePlayAccess(accountId, workCode, current);
        rejectTrialMix(current, mutation);
        return inTx(() -> {
            SessionStateSnapshot snapshot = sessions.apply(ref, mutation);
            SessionRecord row = store.findSession(sessionId).orElseThrow(RuntimeFailure::crossSession);
            Map<String, Object> view = sessionView(row, true);
            view.put("stage", snapshot.stage());
            view.put("payloadJson", snapshot.payloadJson());
            return view;
        });
    }

    public Map<String, Object> endSession(long accountId, String sessionId, String workCode) {
        SessionRef ref = new SessionRef(sessionId, workCode);
        sessions.requireOwner(accountId, ref);
        SessionRecord owned = store.findSession(sessionId).orElseThrow(RuntimeFailure::crossSession);
        requirePlayAccess(accountId, workCode, owned);
        SessionRecord row = inTx(() -> sessions.end(accountId, ref));
        return sessionView(row, false);
    }

    public Map<String, Object> stage(long accountId, String sessionId, String workCode) {
        SessionRef ref = new SessionRef(sessionId, workCode);
        sessions.requireOwner(accountId, ref);
        SessionRecord row = store.findSession(sessionId).orElseThrow(RuntimeFailure::crossSession);
        boolean trial = BreakupWorks.trialPayload(row.payloadJson());
        boolean entitled = hasEntitlement(accountId, workCode);
        if (!trial && !entitled && BreakupWorks.is(workCode)) {
            Map<String, Object> gatedBody = new LinkedHashMap<>();
            gatedBody.put("sessionId", sessionId);
            gatedBody.put("workCode", workCode);
            gatedBody.put("form", row.form());
            gatedBody.put("stage", row.stage());
            gatedBody.put("gated", true);
            gatedBody.put("layoutJson", "{}");
            gatedBody.put("tokensJson", "{}");
            gatedBody.put("nodesJson", "[]");
            gatedBody.put("assets", Map.of());
            return gatedBody;
        }
        boolean disclose = entitled || trial;
        List<String> reached = StageDisclosure.reachedFrom(row.payloadJson(), row.stage());
        StageContent content = disclosure.disclose(disclose, workCode, contentVersion(row), row.stage(), reached);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sessionId", sessionId);
        body.put("workCode", workCode);
        body.put("form", row.form());
        body.put("stage", content.stage());
        body.put("gated", content.gated());
        if (content.gated()) {
            body.put("layoutJson", "{}");
            body.put("tokensJson", "{}");
            body.put("nodesJson", "[]");
            body.put("assets", Map.of());
            return body;
        }
        body.put("layoutJson", content.layoutJson());
        body.put("tokensJson", content.tokensJson());
        body.put("nodesJson", content.nodesJson());
        body.put("assets", content.signedAssetUrls());
        return body;
    }

    public Map<String, Object> sandboxBootstrap(long accountId, String sessionId, String workCode) {
        SessionRef ref = new SessionRef(sessionId, workCode);
        sessions.requireOwner(accountId, ref);
        requireEntitlement(accountId, workCode);
        SessionRecord row = store.findSession(sessionId).orElseThrow(RuntimeFailure::crossSession);
        WorkRuntimeManifest manifest = works.require(workCode);
        String origin = manifest.sandboxOrigin();
        SandboxGuard.IssuedTicket ticket = sandbox.issue(sessionId, accountId, workCode, origin);
        String entry = manifest.entry() == null || manifest.entry().isBlank() ? "index.html" : manifest.entry();
        String iframeSrc = originForSrc(origin) + "/" + trimSlash(entry);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sessionId", sessionId);
        body.put("workCode", workCode);
        body.put("form", row.form());
        body.put("iframeSrc", iframeSrc);
        body.put("nonce", ticket.nonce());
        body.put("origin", ticket.origin());
        body.put("sandbox", SandboxMethods.IFRAME_SANDBOX);
        body.put("methods", List.copyOf(SandboxMethods.HOST_METHODS));
        body.put("expiresAt", ticket.expiresAt().toString());
        return body;
    }

    public Map<String, Object> save(
            long accountId,
            String sessionId,
            String workCode,
            String slot,
            Integer expectedVersion) {
        SessionRef ref = new SessionRef(sessionId, workCode);
        sessions.requireOwner(accountId, ref);
        SessionRecord playable = store.findSession(sessionId).orElseThrow(RuntimeFailure::crossSession);
        requirePlayAccess(accountId, workCode, playable);
        String resolvedSlot = slot == null || slot.isBlank() ? "auto" : slot.trim();
        return inTx(() -> {
            SessionRecord session = store.findSession(sessionId).orElseThrow(RuntimeFailure::crossSession);
            CanonicalJson.requireSchema(session.schemaVersion());
            CanonicalJson.verifyChecksum(
                    session.schemaVersion(),
                    session.contentVersion(),
                    session.stage(),
                    session.payloadJson(),
                    session.checksum());
            String signature = signer.sign(
                    accountId,
                    sessionId,
                    workCode,
                    resolvedSlot,
                    session.schemaVersion(),
                    session.contentVersion(),
                    session.checksum());
            Instant now = Instant.now();
            SaveRecord existing = store.findSave(sessionId, resolvedSlot).orElse(null);
            if (existing == null) {
                SaveRecord created = new SaveRecord(
                        0L,
                        sessionId,
                        accountId,
                        workCode,
                        resolvedSlot,
                        session.schemaVersion(),
                        session.contentVersion(),
                        session.stage(),
                        session.payloadJson(),
                        session.checksum(),
                        signature,
                        0,
                        now,
                        now);
                store.insertSave(created);
                return saveView(store.findSave(sessionId, resolvedSlot).orElse(created));
            }
            if (expectedVersion != null && existing.version() != expectedVersion) {
                throw RuntimeFailure.conflict("存档已在其他设备更新");
            }
            SaveRecord next = new SaveRecord(
                    existing.id(),
                    existing.sessionId(),
                    existing.accountId(),
                    existing.workCode(),
                    existing.slot(),
                    session.schemaVersion(),
                    session.contentVersion(),
                    session.stage(),
                    session.payloadJson(),
                    session.checksum(),
                    signature,
                    existing.version(),
                    existing.createdAt(),
                    now);
            if (!store.updateSave(next, existing.version())) {
                throw RuntimeFailure.conflict("存档已在其他设备更新");
            }
            return saveView(store.findSave(sessionId, resolvedSlot).orElse(next));
        });
    }

    public List<Map<String, Object>> listSaves(long accountId, String sessionId, String workCode) {
        SessionRef ref = new SessionRef(sessionId, workCode);
        sessions.requireOwner(accountId, ref);
        SessionRecord listed = store.findSession(sessionId).orElseThrow(RuntimeFailure::crossSession);
        requirePlayAccess(accountId, workCode, listed);
        List<Map<String, Object>> out = new ArrayList<>();
        for (SaveRecord row : store.listSaves(accountId, workCode, sessionId)) {
            signer.verify(
                    row.accountId(),
                    row.sessionId(),
                    row.workCode(),
                    row.slot(),
                    row.schemaVersion(),
                    row.contentVersion(),
                    row.checksum(),
                    row.signature());
            CanonicalJson.verifyChecksum(row.schemaVersion(), row.contentVersion(), row.stage(), row.stateJson(), row.checksum());
            out.add(saveView(row));
        }
        return out;
    }

    public Map<String, Object> hostInvoke(
            long accountId,
            String sessionId,
            String workCode,
            String nonce,
            String iframeOrigin,
            String method,
            String payloadJson) {
        SessionRef ref = new SessionRef(sessionId, workCode);
        sessions.requireOwner(accountId, ref);
        SessionRecord hosted = store.findSession(sessionId).orElseThrow(RuntimeFailure::crossSession);
        requirePlayAccess(accountId, workCode, hosted);
        sandbox.require(nonce, iframeOrigin, method, accountId, sessionId, workCode);
        String safePayload = HostSanitizer.sanitizeJson(payloadJson);
        String result = switch (method) {
            case "state.save" -> CanonicalJson.write(
                    CanonicalJson.parse(CanonicalJson.write(CanonicalJson.MAPPER.valueToTree(
                            save(accountId, sessionId, workCode, slotOf(safePayload), versionOf(safePayload))))));
            case "state.load" -> CanonicalJson.write(
                    CanonicalJson.parse(CanonicalJson.write(CanonicalJson.MAPPER.valueToTree(
                            listSaves(accountId, sessionId, workCode)))));
            case "assets.sign" -> signAssets(accountId, sessionId, workCode, safePayload);
            case "tools.invoke" -> invokeTool(accountId, sessionId, workCode, safePayload);
            case "ai.complete" -> completeAi(accountId, sessionId, workCode, safePayload);
            case "events.emit", "events.schedule", "metrics.report" -> "{\"accepted\":true}";
            case "user.profile" -> "{\"displayName\":\"member\"}";
            default -> throw RuntimeFailure.forbidden("METHOD_NOT_ALLOWED", "sandbox method rejected");
        };
        return Map.of(
                "ok", true,
                "method", method,
                "payloadJson", HostSanitizer.sanitizeJson(result));
    }

    public Map<String, Object> invokeToolHttp(long accountId, String sessionId, String workCode, String nonce, String iframeOrigin, String payloadJson) {
        if (nonce != null && !nonce.isBlank()) {
            return hostInvoke(accountId, sessionId, workCode, nonce, iframeOrigin, "tools.invoke", payloadJson);
        }
        SessionRef toolRef = new SessionRef(sessionId, workCode);
        sessions.requireOwner(accountId, toolRef);
        SessionRecord toolRow = store.findSession(sessionId).orElseThrow(RuntimeFailure::crossSession);
        requirePlayAccess(accountId, workCode, toolRow);
        if (BreakupWorks.trialPayload(toolRow.payloadJson())) {
            throw RuntimeFailure.forbidden("TRIAL_TOOL_BLOCKED", "trial sessions do not call live tools");
        }
        String result = invokeTool(accountId, sessionId, workCode, HostSanitizer.sanitizeJson(payloadJson));
        return Map.of("ok", true, "method", "tools.invoke", "payloadJson", HostSanitizer.sanitizeJson(result));
    }

    private String invokeTool(long accountId, String sessionId, String workCode, String payloadJson) {
        JsonNode body = CanonicalJson.parse(payloadJson);
        String toolCode = text(body, "toolCode");
        String toolVersion = body.path("toolVersion").asText("1");
        String idempotencyKey = body.path("idempotencyKey").asText("");
        if (idempotencyKey.isBlank()) {
            idempotencyKey = sessionId + ":" + toolCode + ":" + CanonicalJson.sha256(payloadJson).substring(0, 16);
        }
        String argumentsJson = body.has("argumentsJson")
                ? (body.get("argumentsJson").isTextual() ? body.get("argumentsJson").asText() : CanonicalJson.write(body.get("argumentsJson")))
                : "{}";
        if (StageDisclosure.looksLikeServerPath(toolCode)) {
            throw RuntimeFailure.forbidden("METHOD_NOT_ALLOWED", "toolCode rejected");
        }
        ToolResult result = tools.execute(new ToolExecutionCommand(
                toolCode,
                toolVersion,
                new SessionRef(sessionId, workCode),
                accountId,
                workCode,
                idempotencyKey,
                argumentsJson));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", result.success());
        out.put("code", result.code());
        out.put("message", result.message());
        out.put("payloadJson", HostSanitizer.sanitizeJson(result.payloadJson()));
        try {
            return CanonicalJson.MAPPER.writeValueAsString(out);
        } catch (Exception ex) {
            return "{\"success\":false,\"code\":\"TOOL_RESULT\",\"message\":\"encode failed\",\"payloadJson\":\"{}\"}";
        }
    }

    private String completeAi(long accountId, String sessionId, String workCode, String payloadJson) {
        if (ai == null) {
            return "{\"code\":\"AI_UNAVAILABLE\",\"message\":\"AI port not wired\"}";
        }
        String text = ai.complete(accountId, sessionId, workCode, payloadJson);
        return HostSanitizer.sanitizeJson(text == null || text.isBlank() ? "{\"text\":\"\"}" : text);
    }

    private String signAssets(long accountId, String sessionId, String workCode, String payloadJson) {
        SessionRecord row = store.findSession(sessionId).orElseThrow(RuntimeFailure::crossSession);
        JsonNode body = CanonicalJson.parse(payloadJson);
        List<String> keys = new ArrayList<>();
        JsonNode ids = body.get("assetIds");
        if (ids != null && ids.isArray()) {
            ids.forEach(item -> keys.add(item.asText()));
        }
        Map<String, String> urls = new LinkedHashMap<>();
        for (String key : keys) {
            if (StageDisclosure.looksLikeServerPath(key)) {
                continue;
            }
            SignedAsset signed = works.signAsset(workCode, contentVersion(row), key, row.stage());
            urls.put(signed.assetKey(), signed.url());
        }
        try {
            return CanonicalJson.MAPPER.writeValueAsString(Map.of("assets", urls));
        } catch (Exception ex) {
            return "{\"assets\":{}}";
        }
    }

    private void requireEntitlement(long accountId, String workCode) {
        entitlements.requireEntitlement(accountId, workCode);
    }

    private void requirePlayAccess(long accountId, String workCode, SessionRecord row) {
        if ((BreakupWorks.is(workCode) || DreamPhoneWorks.is(workCode)) && BreakupWorks.trialPayload(row.payloadJson())) {
            return;
        }
        requireEntitlement(accountId, workCode);
    }

    private static void rejectTrialMix(SessionRecord row, StateMutation mutation) {
        boolean currentTrial = BreakupWorks.trialPayload(row.payloadJson());
        if (!"REPLACE".equals(mutation.type()) && !"PATCH".equals(mutation.type()) && !"TOOL_PATCH".equals(mutation.type())) {
            return;
        }
        boolean nextTrial = BreakupWorks.trialPayload(mutation.payloadJson());
        if ("PATCH".equals(mutation.type())) {
            JsonNode patch = CanonicalJson.parse(mutation.payloadJson());
            if (patch.has("trial")) {
                nextTrial = patch.path("trial").asBoolean(currentTrial);
            } else {
                nextTrial = currentTrial;
            }
        }
        if (currentTrial != nextTrial) {
            throw RuntimeFailure.forbidden("TRIAL_ISOLATION", "trial saves cannot enter the full session");
        }
    }

    private boolean hasEntitlement(long accountId, String workCode) {
        try {
            entitlements.requireEntitlement(accountId, workCode);
            return true;
        } catch (EntitlementRequiredException ex) {
            return false;
        }
    }

    private static String contentVersion(SessionRecord row) {
        return row.contentVersion();
    }

    private static String slotOf(String payloadJson) {
        return CanonicalJson.parse(payloadJson).path("slot").asText("auto");
    }

    private static Integer versionOf(String payloadJson) {
        JsonNode version = CanonicalJson.parse(payloadJson).get("version");
        return version == null || version.isNull() ? null : version.asInt();
    }

    private static String text(JsonNode body, String field) {
        String value = body.path(field).asText("");
        if (value.isBlank()) {
            throw RuntimeFailure.badRequest("INVALID_JSON", field + " is required");
        }
        return value;
    }

    private static String originForSrc(String origin) {
        if (origin == null || origin.isBlank() || "null".equals(origin)) {
            return "https://sandbox.invalid";
        }
        return origin.endsWith("/") ? origin.substring(0, origin.length() - 1) : origin;
    }

    private static String trimSlash(String path) {
        if (path.startsWith("/")) {
            return path.substring(1);
        }
        return path;
    }

    static Map<String, Object> sessionView(SessionRecord row, boolean includePayload) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("sessionId", row.sessionId());
        view.put("workCode", row.workCode());
        view.put("form", row.form());
        view.put("stage", row.stage());
        view.put("schemaVersion", row.schemaVersion());
        view.put("contentVersion", row.contentVersion());
        view.put("checksum", row.checksum());
        view.put("status", row.status());
        view.put("version", row.version());
        view.put("trial", BreakupWorks.trialPayload(row.payloadJson()));
        if (includePayload) {
            view.put("payloadJson", row.payloadJson());
        }
        return view;
    }

    static Map<String, Object> saveView(SaveRecord row) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("sessionId", row.sessionId());
        view.put("workCode", row.workCode());
        view.put("slot", row.slot());
        view.put("schemaVersion", row.schemaVersion());
        view.put("contentVersion", row.contentVersion());
        view.put("stage", row.stage());
        view.put("checksum", row.checksum());
        view.put("version", row.version());
        view.put("stateJson", row.stateJson());
        return view;
    }
}
