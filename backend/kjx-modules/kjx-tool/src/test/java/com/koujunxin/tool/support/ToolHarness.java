package com.koujunxin.tool.support;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.koujunxin.runtime.api.dto.SessionRef;
import com.koujunxin.runtime.api.dto.SessionStateSnapshot;
import com.koujunxin.runtime.api.dto.StateMutation;
import com.koujunxin.runtime.api.dto.ToolResult;
import com.koujunxin.runtime.api.error.EntitlementRequiredException;
import com.koujunxin.runtime.api.error.SessionNotOwnedException;
import com.koujunxin.runtime.api.port.EntitlementAccessPort;
import com.koujunxin.runtime.api.port.SessionAuthorizationPort;
import com.koujunxin.runtime.api.port.SessionStateCommandPort;
import com.koujunxin.runtime.api.port.SessionStateQueryPort;
import com.koujunxin.tool.api.RateLimitSpec;
import com.koujunxin.tool.api.ToolFailure;
import com.koujunxin.tool.api.ToolSpec;
import com.koujunxin.tool.builtin.BuiltinCatalog;
import com.koujunxin.tool.builtin.BuiltinHandler;
import com.koujunxin.tool.compose.ComposeEngine;
import com.koujunxin.tool.execute.CallClaim;
import com.koujunxin.tool.execute.RateLimitStore;
import com.koujunxin.tool.execute.ToolCallStore;
import com.koujunxin.tool.execute.ToolExecutor;
import com.koujunxin.tool.execute.ToolUnitOfWork;
import com.koujunxin.tool.json.JsonSupport;
import com.koujunxin.tool.registry.ToolCatalog;
import com.koujunxin.tool.registry.ToolRegistration;
import com.koujunxin.tool.schema.ToolJsonSchema;
import com.koujunxin.tool.ui.UiNodeWhitelist;

public final class ToolHarness {

    public final ObjectMapper mapper = new ObjectMapper();
    public final Clock clock = Clock.systemUTC();
    public final MemoryCatalog catalog = new MemoryCatalog();
    public final MemoryCalls calls = new MemoryCalls();
    public final MemoryRates rates = new MemoryRates();
    public final CountingUnitOfWork unitOfWork = new CountingUnitOfWork();
    public final GrantingEntitlement entitlements = new GrantingEntitlement();
    public final OwnerAuth sessions = new OwnerAuth();
    public final MemorySessionState state = new MemorySessionState(mapper);
    public final Map<String, BuiltinHandler> extraBuiltins = new HashMap<>();

    public ToolExecutor executor() {
        BuiltinCatalog builtins = new BuiltinCatalog(mapper, clock, extraBuiltins);
        UiNodeWhitelist ui = platformUi();
        return new ToolExecutor(
                catalog,
                calls,
                rates,
                unitOfWork,
                entitlements,
                sessions,
                state,
                state,
                builtins,
                new ComposeEngine(mapper, builtins, ui, clock),
                ui,
                new ToolJsonSchema(),
                mapper,
                clock);
    }

    public UiNodeWhitelist platformUi() {
        Map<String, JsonNode> schemas = new HashMap<>();
        schemas.put("toast", JsonSupport.read(mapper,
                "{\"type\":\"object\",\"properties\":{\"text\":{\"type\":\"string\"}},\"required\":[\"text\"],\"additionalProperties\":false}"));
        schemas.put("chat.bubble", JsonSupport.read(mapper,
                "{\"type\":\"object\",\"properties\":{\"text\":{\"type\":\"string\"},\"role\":{\"type\":\"string\"},\"speaker\":{\"type\":\"string\",\"maxLength\":64}},\"required\":[\"text\"],\"additionalProperties\":false}"));
        schemas.put("wallet-balance", JsonSupport.read(mapper,
                "{\"type\":\"object\",\"properties\":{\"amount\":{\"type\":\"number\"}},\"required\":[\"amount\"],\"additionalProperties\":false}"));
        schemas.put("moment-card", JsonSupport.read(mapper,
                "{\"type\":\"object\",\"properties\":{\"content\":{\"type\":\"string\"},\"visibility\":{\"type\":\"string\"}},\"required\":[\"content\"],\"additionalProperties\":false}"));
        schemas.put("redpacket-card", JsonSupport.read(mapper,
                "{\"type\":\"object\",\"properties\":{\"amount\":{\"type\":\"number\"},\"note\":{\"type\":\"string\"},\"opened\":{\"type\":\"boolean\"}},\"required\":[\"amount\"],\"additionalProperties\":false}"));
        schemas.put("memo-item", JsonSupport.read(mapper,
                "{\"type\":\"object\",\"properties\":{\"title\":{\"type\":\"string\"},\"body\":{\"type\":\"string\"}},\"required\":[\"title\"],\"additionalProperties\":false}"));
        schemas.put("music-player", JsonSupport.read(mapper,
                "{\"type\":\"object\",\"properties\":{\"trackId\":{\"type\":\"string\"},\"fadeIn\":{\"type\":\"boolean\"},\"loop\":{\"type\":\"boolean\"}},\"required\":[\"trackId\"],\"additionalProperties\":false}"));
        schemas.put("dialog", JsonSupport.read(mapper,
                "{\"type\":\"object\",\"properties\":{\"title\":{\"type\":\"string\"},\"text\":{\"type\":\"string\"}},\"required\":[\"text\"],\"additionalProperties\":false}"));
        return UiNodeWhitelist.of(schemas, mapper);
    }

    public void grant(long accountId, String workCode) {
        entitlements.grant(accountId, workCode);
    }

    public void own(String sessionId, long accountId) {
        sessions.own(sessionId, accountId);
    }

    public void snapshot(String sessionId, String workCode, String stage, String payloadJson) {
        state.put(new SessionStateSnapshot(new SessionRef(sessionId, workCode), stage, "1.0", payloadJson));
    }

    public void registerBuiltin(String code, String schemaJson, RateLimitSpec rate, int timeoutMs, int retryMax) {
        ObjectNode impl = mapper.createObjectNode();
        impl.put("kind", "builtin");
        impl.put("handler", code);
        catalog.register(new ToolRegistration(
                code,
                "1.0.0",
                "builtin",
                code,
                code,
                JsonSupport.read(mapper, schemaJson),
                mapper.createObjectNode(),
                impl,
                rate,
                timeoutMs,
                retryMax));
    }

    public static final class MemoryCatalog implements ToolCatalog {
        private final Map<String, ToolSpec> tools = new ConcurrentHashMap<>();
        private final Map<String, String> bindings = new ConcurrentHashMap<>();
        private final AtomicLong ids = new AtomicLong();

        @Override
        public Optional<ToolSpec> find(String workCode, String toolCode, String version) {
            ToolSpec spec = tools.get(toolCode + "@" + version);
            if (spec == null) {
                return Optional.empty();
            }
            String binding = bindings.get(workCode + ":" + toolCode);
            boolean enabled = spec.enabled();
            if ("custom".equalsIgnoreCase(spec.type()) && binding == null) {
                enabled = false;
            }
            if ("0".equals(binding)) {
                enabled = false;
            }
            if (binding != null && binding.contains("@") && !binding.equals("1") && !binding.equals("0")
                    && !binding.endsWith("@" + version)) {
                enabled = false;
            }
            return Optional.of(new ToolSpec(
                    spec.id(), spec.code(), spec.version(), spec.type(), spec.displayName(), spec.description(),
                    spec.parametersSchema(), spec.preconditions(), spec.implementation(), spec.rateLimit(),
                    spec.timeoutMs(), spec.retryMax(), enabled));
        }

        @Override
        public void register(ToolRegistration registration) {
            long id = ids.incrementAndGet();
            tools.put(registration.code() + "@" + registration.version(), new ToolSpec(
                    id,
                    registration.code(),
                    registration.version(),
                    registration.type(),
                    registration.displayName(),
                    registration.description(),
                    registration.parametersSchema(),
                    registration.preconditions() == null
                            ? com.fasterxml.jackson.databind.node.MissingNode.getInstance()
                            : registration.preconditions(),
                    registration.implementation(),
                    registration.rateLimit(),
                    registration.timeoutMs(),
                    registration.retryMax(),
                    true));
        }

        @Override
        public void bindWork(String workCode, String toolCode, String version, boolean enabled) {
            bindings.put(workCode + ":" + toolCode, enabled ? "1@" + version : "0");
        }
    }

    public static final class MemoryCalls implements ToolCallStore {
        private final Map<String, CallClaim> claims = new ConcurrentHashMap<>();
        private final Map<Long, String> hashes = new ConcurrentHashMap<>();
        private final Map<Long, ToolResult> results = new ConcurrentHashMap<>();
        private final AtomicLong ids = new AtomicLong();

        @Override
        public CallClaim claim(
                long accountId,
                String workCode,
                String sessionId,
                String toolCode,
                String toolVersion,
                String idempotencyKey,
                String argumentsHash,
                Instant startedAt,
                int pendingTtlMs) {
            String key = sessionId + "\0" + idempotencyKey;
            CallClaim existing = claims.get(key);
            if (existing != null) {
                if (!argumentsHash.equals(hashes.get(existing.id()))) {
                    throw ToolFailure.of("IDEMPOTENCY_CONFLICT", "idempotency key reused with different arguments");
                }
                ToolResult replayed = results.get(existing.id());
                if (replayed != null) {
                    return CallClaim.replay(existing.id(), replayed);
                }
                throw ToolFailure.of("TOOL_IN_PROGRESS", "same call is still running");
            }
            CallClaim fresh = CallClaim.fresh(ids.incrementAndGet());
            claims.put(key, fresh);
            hashes.put(fresh.id(), argumentsHash);
            return fresh;
        }

        @Override
        public void complete(long id, ToolResult result, Instant finishedAt, int durationMs) {
            results.put(id, result);
        }
    }

    public static final class MemoryRates implements RateLimitStore {
        private final Map<String, Integer> sessionCounts = new ConcurrentHashMap<>();
        private final Map<String, Instant> lastCalled = new ConcurrentHashMap<>();

        @Override
        public void checkAndIncrement(String sessionId, String toolCode, RateLimitSpec spec, Instant now) {
            if (spec == null || !spec.limited()) {
                return;
            }
            String key = sessionId + ":" + toolCode;
            Instant previous = lastCalled.get(key);
            if (spec.cooldownSec() != null && spec.cooldownSec() > 0 && previous != null
                    && previous.plusSeconds(spec.cooldownSec()).isAfter(now)) {
                throw ToolFailure.of("RATE_LIMITED", "tool cooldown active");
            }
            int count = sessionCounts.getOrDefault(key, 0);
            if (spec.perSession() != null && count >= spec.perSession()) {
                throw ToolFailure.of("RATE_LIMITED", "per-session tool limit reached");
            }
            sessionCounts.put(key, count + 1);
            lastCalled.put(key, now);
        }
    }

    public static final class CountingUnitOfWork implements ToolUnitOfWork {
        public final AtomicInteger count = new AtomicInteger();

        @Override
        public <T> T inTransaction(Supplier<T> work) {
            count.incrementAndGet();
            return work.get();
        }
    }

    public static final class GrantingEntitlement implements EntitlementAccessPort {
        private final Set<String> grants = new HashSet<>();

        public void grant(long accountId, String workCode) {
            grants.add(accountId + ":" + workCode);
        }

        @Override
        public void requireEntitlement(long accountId, String workCode) {
            if (!grants.contains(accountId + ":" + workCode)) {
                throw new EntitlementRequiredException(accountId, workCode);
            }
        }
    }

    public static final class OwnerAuth implements SessionAuthorizationPort {
        private final Map<String, Long> owners = new HashMap<>();

        public void own(String sessionId, long accountId) {
            owners.put(sessionId, accountId);
        }

        @Override
        public void requireOwner(long accountId, SessionRef session) {
            Long owner = owners.get(session.sessionId());
            if (owner == null || owner != accountId) {
                throw new SessionNotOwnedException(accountId, session.sessionId());
            }
        }
    }

    public static final class MemorySessionState implements SessionStateQueryPort, SessionStateCommandPort {
        private final ObjectMapper mapper;
        private final Map<String, SessionStateSnapshot> snapshots = new ConcurrentHashMap<>();
        public final AtomicInteger applies = new AtomicInteger();

        public MemorySessionState(ObjectMapper mapper) {
            this.mapper = mapper;
        }

        public void put(SessionStateSnapshot snapshot) {
            snapshots.put(snapshot.session().sessionId(), snapshot);
        }

        @Override
        public SessionStateSnapshot load(SessionRef session) {
            SessionStateSnapshot snapshot = snapshots.get(session.sessionId());
            if (snapshot == null) {
                snapshot = new SessionStateSnapshot(session, "INTRO", "1.0", "{}");
                snapshots.put(session.sessionId(), snapshot);
            }
            return snapshot;
        }

        @Override
        public SessionStateSnapshot apply(SessionRef session, StateMutation mutation) {
            applies.incrementAndGet();
            SessionStateSnapshot current = load(session);
            JsonNode payload = JsonSupport.read(mapper, mutation.payloadJson());
            SessionStateSnapshot next = new SessionStateSnapshot(
                    session, current.stage(), mutation.schemaVersion(), JsonSupport.write(mapper, payload));
            snapshots.put(session.sessionId(), next);
            return next;
        }
    }
}
