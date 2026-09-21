package com.koujunxin.tool.execute;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.koujunxin.runtime.api.dto.SessionStateSnapshot;
import com.koujunxin.runtime.api.dto.ToolExecutionCommand;
import com.koujunxin.runtime.api.dto.ToolResult;
import com.koujunxin.runtime.api.error.EntitlementRequiredException;
import com.koujunxin.runtime.api.error.SessionNotOwnedException;
import com.koujunxin.runtime.api.port.EntitlementAccessPort;
import com.koujunxin.runtime.api.port.SessionAuthorizationPort;
import com.koujunxin.runtime.api.port.SessionStateCommandPort;
import com.koujunxin.runtime.api.port.SessionStateQueryPort;
import com.koujunxin.runtime.api.port.ToolExecutionPort;
import com.koujunxin.tool.api.ToolFailure;
import com.koujunxin.tool.api.ToolSpec;
import com.koujunxin.tool.builtin.BuiltinCatalog;
import com.koujunxin.tool.compose.ComposeEngine;
import com.koujunxin.tool.json.JsonSupport;
import com.koujunxin.tool.registry.ToolCatalog;
import com.koujunxin.tool.schema.ToolJsonSchema;
import com.koujunxin.tool.state.WorkingState;
import com.koujunxin.tool.ui.UiNodeWhitelist;

/**
 * ToolExecutionPort 实现。校验 entitlement / 会话归属 / 频控，不判断会员 VALID。
 */
public final class ToolExecutor implements ToolExecutionPort {

    private final ToolCatalog catalog;
    private final ToolCallStore calls;
    private final RateLimitStore rates;
    private final ToolUnitOfWork unitOfWork;
    private final EntitlementAccessPort entitlements;
    private final SessionAuthorizationPort sessionAuth;
    private final SessionStateQueryPort stateQuery;
    private final SessionStateCommandPort stateCommand;
    private final BuiltinCatalog builtins;
    private final ComposeEngine compose;
    private final UiNodeWhitelist ui;
    private final ToolJsonSchema schemas;
    private final PreconditionChecker preconditions;
    private final ObjectMapper mapper;
    private final Clock clock;

    public ToolExecutor(
            ToolCatalog catalog,
            ToolCallStore calls,
            RateLimitStore rates,
            ToolUnitOfWork unitOfWork,
            EntitlementAccessPort entitlements,
            SessionAuthorizationPort sessionAuth,
            SessionStateQueryPort stateQuery,
            SessionStateCommandPort stateCommand,
            BuiltinCatalog builtins,
            ComposeEngine compose,
            UiNodeWhitelist ui,
            ToolJsonSchema schemas,
            ObjectMapper mapper,
            Clock clock) {
        this.catalog = catalog;
        this.calls = calls;
        this.rates = rates;
        this.unitOfWork = unitOfWork;
        this.entitlements = entitlements;
        this.sessionAuth = sessionAuth;
        this.stateQuery = stateQuery;
        this.stateCommand = stateCommand;
        this.builtins = builtins;
        this.compose = compose;
        this.ui = ui;
        this.schemas = schemas;
        this.preconditions = new PreconditionChecker(mapper);
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public ToolResult execute(ToolExecutionCommand command) {
        Instant started = clock.instant();
        try {
            return doExecute(command, started);
        } catch (EntitlementRequiredException | SessionNotOwnedException ex) {
            throw ex;
        } catch (ToolFailure ex) {
            return ex.toResult();
        }
    }

    private ToolResult doExecute(ToolExecutionCommand command, Instant started) {
        if (!command.workCode().equals(command.session().workCode())) {
            throw ToolFailure.of("SESSION_WORK_MISMATCH", "workCode does not match session");
        }
        ToolSpec spec = catalog.find(command.workCode(), command.toolCode(), command.toolVersion())
                .orElseThrow(() -> ToolFailure.of("TOOL_NOT_FOUND", "tool not found"));
        if (!spec.enabled()) {
            throw ToolFailure.of("TOOL_DISABLED", "tool is disabled");
        }
        PreconditionChecker.rejectScript(spec);
        JsonNode args = JsonSupport.read(mapper, command.argumentsJson());
        if (!args.isObject()) {
            throw ToolFailure.of("INVALID_ARGUMENTS", "arguments must be an object");
        }
        PreconditionChecker.validateArguments(schemas, spec, args);

        entitlements.requireEntitlement(command.accountId(), command.workCode());
        sessionAuth.requireOwner(command.accountId(), command.session());

        SessionStateSnapshot snapshot = stateQuery.load(command.session());
        preconditions.check(spec, snapshot, args);

        String hash = JsonSupport.argumentsHash(spec.code(), spec.version(), args);
        CallClaim claim = unitOfWork.inTransaction(() -> {
            rates.checkAndIncrement(command.session().sessionId(), spec.code(), spec.rateLimit(), started);
            return calls.claim(
                    command.accountId(),
                    command.workCode(),
                    command.session().sessionId(),
                    spec.code(),
                    spec.version(),
                    command.idempotencyKey(),
                    hash,
                    started,
                    spec.timeoutMs());
        });
        if (claim.replay()) {
            return claim.replayed();
        }

        ToolResult result;
        try {
            WorkingState working = runTimed(spec, () -> runWithRetry(spec, () -> runImplementation(spec, args, snapshot)));
            ui.validateInstructions(working.ui());
            if (working.dirty()) {
                stateCommand.apply(command.session(), working.mutation());
            }
            result = ToolResult.ok(working.toResultJson());
        } catch (EntitlementRequiredException | SessionNotOwnedException ex) {
            complete(claim, ToolResult.fail(ex.code(), ex.getMessage()), started);
            throw ex;
        } catch (ToolFailure ex) {
            complete(claim, ex.toResult(), started);
            return ex.toResult();
        } catch (RuntimeException ex) {
            ToolResult failed = ToolResult.fail("TOOL_FAILED", "tool execution failed");
            complete(claim, failed, started);
            return failed;
        }
        complete(claim, result, started);
        return result;
    }

    private void complete(CallClaim claim, ToolResult result, Instant started) {
        Instant finished = clock.instant();
        int duration = (int) Math.max(0, finished.toEpochMilli() - started.toEpochMilli());
        unitOfWork.inTransaction(() -> {
            calls.complete(claim.id(), result, finished, duration);
            return null;
        });
    }

    private WorkingState runImplementation(ToolSpec spec, JsonNode args, SessionStateSnapshot snapshot) {
        WorkingState working = new WorkingState(mapper, snapshot);
        if (spec.composeKind()) {
            compose.run(spec.implementation(), args, working, 0);
        } else {
            String handler = spec.implementation().path("handler").asText(spec.code());
            builtins.execute(handler, args, working);
        }
        return working;
    }

    private WorkingState runWithRetry(ToolSpec spec, Callable<WorkingState> once) throws Exception {
        int max = spec.retryMax();
        ToolFailure last = null;
        for (int attempt = 0; attempt <= max; attempt++) {
            try {
                return once.call();
            } catch (ToolFailure ex) {
                if (!ex.retryable() || attempt == max) {
                    throw ex;
                }
                last = ex;
            }
        }
        throw last == null ? ToolFailure.of("TOOL_FAILED", "retry exhausted") : last;
    }

    private WorkingState runTimed(ToolSpec spec, Callable<WorkingState> task) {
        int timeout = spec.timeoutMs();
        ExecutorService pool = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "kjx-tool-" + spec.code());
            thread.setDaemon(true);
            return thread;
        });
        try {
            return pool.submit(task).get(timeout, TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            throw ToolFailure.of("TOOL_TIMEOUT", "信息不可用");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw ToolFailure.of("TOOL_TIMEOUT", "信息不可用");
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof ToolFailure failure) {
                throw failure;
            }
            if (cause instanceof EntitlementRequiredException entitlement) {
                throw entitlement;
            }
            if (cause instanceof SessionNotOwnedException owned) {
                throw owned;
            }
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw ToolFailure.of("TOOL_FAILED", cause == null ? "tool execution failed" : cause.getMessage());
        } finally {
            List<Runnable> leftover = pool.shutdownNow();
            leftover.clear();
        }
    }
}
