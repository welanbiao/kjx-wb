package com.koujunxin.runtime.support;

import java.time.Clock;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.koujunxin.runtime.api.error.EntitlementRequiredException;
import com.koujunxin.runtime.api.port.EntitlementAccessPort;
import com.koujunxin.runtime.api.port.ToolExecutionPort;
import com.koujunxin.runtime.api.dto.ToolResult;
import com.koujunxin.runtime.breakup.BreakupAssetService;
import com.koujunxin.runtime.breakup.BreakupCatalog;
import com.koujunxin.runtime.breakup.BreakupRewriteService;
import com.koujunxin.runtime.breakup.BreakupRuntimeService;
import com.koujunxin.runtime.breakup.BreakupSessionFactory;
import com.koujunxin.runtime.dreamphone.DreamPhoneRuntimeService;
import com.koujunxin.runtime.dreamphone.DreamPhoneSessionFactory;
import com.koujunxin.runtime.ledger.SaveSigner;
import com.koujunxin.runtime.sandbox.SandboxGuard;
import com.koujunxin.runtime.session.InMemoryRuntimeStore;
import com.koujunxin.runtime.session.RuntimeApplicationService;
import com.koujunxin.runtime.session.SessionStateService;

public final class RuntimeHarness {

    public final InMemoryRuntimeStore store = new InMemoryRuntimeStore();
    public final FakeWorkRuntimePort works = new FakeWorkRuntimePort()
            .registerFormA("phone-demo")
            .registerFormA("breakup-editor")
            .registerFormA("dream-phone")
            .registerFormB("sandbox-demo");
    public final Set<String> entitled = ConcurrentHashMap.newKeySet();
    public final EntitlementAccessPort entitlements = (accountId, workCode) -> {
        if (!entitled.contains(accountId + ":" + workCode)) {
            throw new EntitlementRequiredException(accountId, workCode);
        }
    };
    public final SessionStateService sessions = new SessionStateService(store, works);
    public final SaveSigner signer = new SaveSigner("unit-test-runtime-secret");
    public final SandboxGuard sandbox = new SandboxGuard(store, Clock.systemUTC(), Duration.ofHours(2));
    public ToolExecutionPort tools = command -> ToolResult.ok("{\"echo\":true}");
    public final BreakupCatalog breakupCatalog = new BreakupCatalog();
    public final BreakupSessionFactory breakupSessions = new BreakupSessionFactory(breakupCatalog);
    public BreakupRewriteService breakupRewrites = new BreakupRewriteService(
            breakupCatalog, command -> tools.execute(command), null);
    public final BreakupRuntimeService breakup = new BreakupRuntimeService(
            breakupCatalog,
            breakupSessions,
            breakupRewrites,
            new BreakupAssetService("unit-test-runtime-secret"),
            sessions,
            store,
            entitlements);
    public final DreamPhoneSessionFactory dreamSessions = new DreamPhoneSessionFactory();
    public final DreamPhoneRuntimeService dream = new DreamPhoneRuntimeService(
            dreamSessions, sessions, store, entitlements, command -> tools.execute(command), null, null);
    public final RuntimeApplicationService app = new RuntimeApplicationService(
            store, sessions, entitlements, works, signer, sandbox, command -> tools.execute(command), null, null,
            breakupSessions, dreamSessions);

    public RuntimeHarness entitle(long accountId, String workCode) {
        entitled.add(accountId + ":" + workCode);
        return this;
    }
}
