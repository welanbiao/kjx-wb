package com.koujunxin.runtime.support;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.koujunxin.runtime.api.SignedAsset;
import com.koujunxin.runtime.api.StageContent;
import com.koujunxin.runtime.api.WorkRuntimeManifest;
import com.koujunxin.runtime.api.WorkRuntimePort;
import com.koujunxin.runtime.api.RuntimeFailure;
import com.koujunxin.runtime.ledger.CanonicalJson;

public final class FakeWorkRuntimePort implements WorkRuntimePort {

    private final Map<String, WorkRuntimeManifest> works = new ConcurrentHashMap<>();
    private final Map<String, String> layout = new ConcurrentHashMap<>();
    private final Map<String, String> tokens = new ConcurrentHashMap<>();
    private final Map<String, String> nodes = new ConcurrentHashMap<>();

    public FakeWorkRuntimePort registerFormA(String workCode) {
        works.put(workCode, new WorkRuntimeManifest(
                42L, workCode, "A", "1.0.0", "c-1.0.0", "1", "", "null"));
        layout.put(workCode, """
                {"type":"phone","screens":[
                  {"id":"home","stage":"1","nodes":[{"type":"chat.bubble","props":{"role":"npc","text":"你好，这里是第一幕。"}}]},
                  {"id":"later","stage":"2","nodes":[{"type":"chat.system","props":{"text":"第二幕尚未到达时不应下发。"}}]}
                ]}
                """);
        tokens.put(workCode, "{\"color\":{\"bg\":\"#061923\",\"ink\":\"#faedd1\",\"accent\":\"#f4520d\"}}");
        nodes.put(workCode, """
                [{"id":"n1","text":"hello","stage":1},{"id":"n2","text":"secret-ending","stage":2}]
                """);
        return this;
    }

    public FakeWorkRuntimePort registerFormB(String workCode) {
        works.put(workCode, new WorkRuntimeManifest(
                43L, workCode, "B", "1.0.0", "c-1.0.0", "1", "dist/index.html", "https://demo.game.example"));
        layout.put(workCode, "{}");
        tokens.put(workCode, "{}");
        nodes.put(workCode, "[]");
        return this;
    }

    @Override
    public WorkRuntimeManifest require(String workCode) {
        WorkRuntimeManifest manifest = works.get(workCode);
        if (manifest == null) {
            throw RuntimeFailure.notFound("WORK_NOT_FOUND", "work not found");
        }
        return manifest;
    }

    @Override
    public StageContent loadStage(String workCode, String packageVersion, String stage, List<String> reached) {
        require(workCode);
        Map<String, String> assets = new LinkedHashMap<>();
        assets.put("icon", "https://cdn.example/icon.svg?sig=short");
        return new StageContent(
                stage,
                layout.getOrDefault(workCode, "{}"),
                tokens.getOrDefault(workCode, "{}"),
                nodes.getOrDefault(workCode, "[]"),
                List.of("icon"),
                assets,
                false);
    }

    @Override
    public SignedAsset signAsset(String workCode, String packageVersion, String assetKey, String stage) {
        if (assetKey != null && assetKey.toLowerCase().contains("server")) {
            throw RuntimeFailure.forbidden("SERVER_NOT_ON_CDN", "server/ cannot be signed");
        }
        return new SignedAsset(assetKey, "https://cdn.example/" + assetKey + "?sig=short", Instant.now().plusSeconds(600));
    }

    @Override
    public String sandboxOrigin(String workCode) {
        return require(workCode).sandboxOrigin();
    }

    public Set<String> codes() {
        return works.keySet();
    }
}
