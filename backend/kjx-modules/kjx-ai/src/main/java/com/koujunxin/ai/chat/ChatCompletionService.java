package com.koujunxin.ai.chat;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.koujunxin.ai.api.AiChannelMode;
import com.koujunxin.ai.api.AiException;
import com.koujunxin.ai.api.ChatTurnCommand;
import com.koujunxin.ai.api.ChatTurnResult;
import com.koujunxin.ai.api.RoutedModel;
import com.koujunxin.ai.api.TokenUsage;
import com.koujunxin.ai.api.port.AiChargePort;
import com.koujunxin.ai.api.port.CancellationToken;
import com.koujunxin.ai.api.port.ConversationStore;
import com.koujunxin.ai.api.port.LangfusePort;
import com.koujunxin.ai.api.port.ModelRouter;
import com.koujunxin.ai.api.port.PromptCatalog;
import com.koujunxin.ai.api.port.UpstreamChatClient;
import com.koujunxin.ai.api.port.UpstreamChatRequest;
import com.koujunxin.ai.api.port.UpstreamChatResponse;
import com.koujunxin.ai.degrade.OutputDegrader;
import com.koujunxin.ai.observe.SafeLangfuseReporter;
import com.koujunxin.ai.openai.OpenAiChunk;
import com.koujunxin.ai.openai.OpenAiCodec;
import com.koujunxin.ai.openai.OpenAiCompletionResponse;
import com.koujunxin.ai.openai.OpenAiMessage;
import com.koujunxin.ai.openai.OpenAiModelList;
import com.koujunxin.ai.openai.OpenAiToolCall;
import com.koujunxin.ai.openai.OpenAiUsage;
import com.koujunxin.ai.prompt.AssembledPrompt;
import com.koujunxin.ai.prompt.PromptAssembler;
import com.koujunxin.ai.prompt.PromptRecord;
import com.koujunxin.ai.session.ChatMessage;
import com.koujunxin.ai.session.Conversation;
import com.koujunxin.ai.session.SummaryPolicy;
import com.koujunxin.ai.sse.SsePump;
import com.koujunxin.ai.sse.SseSink;
import com.koujunxin.ai.sse.StreamingTokenSource;
import com.koujunxin.runtime.api.dto.SessionRef;
import com.koujunxin.runtime.api.dto.SessionStateSnapshot;
import com.koujunxin.runtime.api.dto.ToolExecutionCommand;
import com.koujunxin.runtime.api.dto.ToolResult;
import com.koujunxin.runtime.api.port.EntitlementAccessPort;
import com.koujunxin.runtime.api.port.SessionAuthorizationPort;
import com.koujunxin.runtime.api.port.SessionStateQueryPort;
import com.koujunxin.runtime.api.port.ToolExecutionPort;

public final class ChatCompletionService {

    private static final Logger log = LoggerFactory.getLogger(ChatCompletionService.class);
    private static final int MAX_TOOL_ROUNDS = 4;

    private final PromptCatalog prompts;
    private final PromptAssembler assembler;
    private final ConversationStore conversations;
    private final SummaryPolicy summaries;
    private final ModelRouter router;
    private final UpstreamChatClient upstream;
    private final OutputDegrader degrader;
    private final AiChargePort charge;
    private final LangfusePort langfuse;
    private final OpenAiCodec codec;
    private final SsePump ssePump;
    private final Clock clock;
    private final SessionStateQueryPort sessionState;
    private final SessionAuthorizationPort sessionAuth;
    private final EntitlementAccessPort entitlements;
    private final ToolExecutionPort tools;
    private final String strongModel;
    private final String cheapModel;

    public ChatCompletionService(
            PromptCatalog prompts,
            ConversationStore conversations,
            ModelRouter router,
            UpstreamChatClient upstream,
            AiChargePort charge,
            LangfusePort langfuse,
            OpenAiCodec codec,
            SsePump ssePump,
            Clock clock,
            SessionStateQueryPort sessionState,
            SessionAuthorizationPort sessionAuth,
            EntitlementAccessPort entitlements,
            ToolExecutionPort tools,
            String strongModel,
            String cheapModel
    ) {
        this.prompts = prompts;
        this.assembler = new PromptAssembler();
        this.conversations = conversations;
        this.summaries = new SummaryPolicy(conversations);
        this.router = router;
        this.upstream = upstream;
        this.degrader = new OutputDegrader(upstream);
        this.charge = charge;
        this.langfuse = new SafeLangfuseReporter(langfuse);
        this.codec = codec;
        this.ssePump = ssePump;
        this.clock = clock;
        this.sessionState = sessionState;
        this.sessionAuth = sessionAuth;
        this.entitlements = entitlements;
        this.tools = tools;
        this.strongModel = strongModel;
        this.cheapModel = cheapModel;
    }

    public OpenAiModelList listModels() {
        long created = clock.instant().getEpochSecond();
        return new OpenAiModelList("list", List.of(
                new OpenAiModelList.OpenAiModel(strongModel, "model", created, "koujunxin"),
                new OpenAiModelList.OpenAiModel(cheapModel, "model", created, "koujunxin")));
    }

    public ChatTurnResult complete(ChatTurnCommand command, CancellationToken cancel) {
        TurnContext ctx = begin(command, cancel);
        try {
            UpstreamChatResponse response = runModel(ctx, command, cancel);
            persistAssistant(ctx, response);
            charge.settle(ctx.hold, response.usage());
            ChatTurnResult result = toResult(ctx, response, ctx.degradedToText);
            report(ctx, result.usage(), "ok");
            log.info("ai.turn.ok sessionId={} turnId={} channel={} model={} degraded={}",
                    command.sessionId(), ctx.turnId, ctx.route.channel(), ctx.route.modelId(), ctx.degradedToText);
            return result;
        } catch (RuntimeException ex) {
            charge.release(ctx.hold, ex.getClass().getSimpleName());
            report(ctx, TokenUsage.zero(), "error");
            throw ex;
        } finally {
            command.wipeSecret();
        }
    }

    public void stream(ChatTurnCommand command, SseSink sink, CancellationToken cancel) {
        TurnContext ctx = begin(command, cancel);
        try {
            if (!command.tools().isEmpty()) {
                UpstreamChatResponse response = runModel(ctx, command, cancel);
                persistAssistant(ctx, response);
                charge.settle(ctx.hold, response.usage());
                pumpText(ctx, response.content(), sink, cancel);
                report(ctx, response.usage(), "ok");
                return;
            }
            UpstreamChatRequest request = new UpstreamChatRequest(
                    ctx.route, ctx.assembled.toMessages(), List.of(), command.jsonSchema(), command.maxTokens(), true, command.byokSecret());
            StreamingTokenSource source = new StreamingTokenSource(upstream, request);
            String text = ssePump.run(ctx.completionId, ctx.route.modelId(), source, sink, cancel);
            TokenUsage usage = source.completed() == null ? estimate(text) : source.completed().usage();
            persistAssistant(ctx, new UpstreamChatResponse(
                    ctx.completionId, ctx.route.modelId(), OpenAiMessage.assistant(text), "stop", usage));
            charge.settle(ctx.hold, usage);
            report(ctx, usage, "ok");
        } catch (RuntimeException ex) {
            charge.release(ctx.hold, ex.getClass().getSimpleName());
            report(ctx, TokenUsage.zero(), "error");
            throw ex;
        } finally {
            command.wipeSecret();
        }
    }

    private TurnContext begin(ChatTurnCommand command, CancellationToken cancel) {
        cancel.throwIfCancelled();
        if (entitlements != null) {
            entitlements.requireEntitlement(command.accountId(), command.workCode());
        }
        Conversation conversation = conversations.open(command.accountId(), command.sessionId(), command.workCode());
        if (sessionAuth != null) {
            sessionAuth.requireOwner(command.accountId(), new SessionRef(command.sessionId(), command.workCode()));
        }
        PromptRecord workPrompt = prompts.findActiveWork(command.workCode())
                .orElseThrow(() -> AiException.promptMissing(command.workCode()));
        String turnId = UUID.randomUUID().toString();
        appendNewUserMessages(command, turnId);
        summaries.maybeSummarize(command.sessionId(), conversations.open(command.accountId(), command.sessionId(), command.workCode()).messageCount());
        String stateJson = loadState(command);
        AssembledPrompt assembled = assembler.assemble(
                workPrompt,
                stateJson,
                summaries.summary(command.sessionId()).orElse(null),
                summaries.recentWindow(command.sessionId()),
                null);
        RoutedModel route = router.route(new ModelRouter.RouteRequest(
                command.channel(), command.taskKind(), command.byokBaseUrl(), command.byokModel(), command.byokSecret()));
        log.info("ai.turn.start sessionId={} turnId={} channel={} model={} task={}",
                command.sessionId(), turnId, route.channel(), route.modelId(), command.taskKind());
        AiChargePort.Hold hold = AiChargePort.Hold.none();
        if (command.channel() == AiChannelMode.PLATFORM) {
            hold = charge.freezePlatform(new AiChargePort.ChargeContext(
                    command.accountId(), command.workCode(), command.sessionId(), turnId, command.idempotencyKey(), command.channel()));
        }
        conversations.touchTurn(command.sessionId(), turnId, workPrompt.version());
        return new TurnContext(turnId, "chatcmpl-" + turnId, conversation, workPrompt.version(), assembled, route, hold, false);
    }

    private UpstreamChatResponse runModel(TurnContext ctx, ChatTurnCommand command, CancellationToken cancel) {
        AssembledPrompt current = ctx.assembled;
        UpstreamChatRequest base = new UpstreamChatRequest(
                ctx.route, current.toMessages(), command.tools(), command.jsonSchema(), command.maxTokens(), false, command.byokSecret());
        for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
            cancel.throwIfCancelled();
            OutputDegrader.DegradedResult degraded = degrader.complete(current, command.tools(), command.jsonSchema(), base, cancel);
            ctx.degradedToText = degraded.degradedToText();
            UpstreamChatResponse response = degraded.response();
            if (response.toolCalls().isEmpty()) {
                return response;
            }
            List<OpenAiMessage> history = new ArrayList<>(current.history());
            history.add(response.message());
            conversations.append(command.sessionId(), ChatMessage.fromAssistant(
                    command.sessionId(), ctx.turnId, response.content(), response.toolCalls()));
            for (OpenAiToolCall call : response.toolCalls()) {
                String payload = executeTool(command, call);
                conversations.append(command.sessionId(), ChatMessage.fromTool(
                        command.sessionId(), ctx.turnId, call.id(), payload));
                history.add(OpenAiMessage.tool(call.id(), payload));
            }
            current = new AssembledPrompt(current.promptVersion(), current.stablePrefix(), current.variableSuffix(), history);
            base = new UpstreamChatRequest(
                    ctx.route, current.toMessages(), command.tools(), command.jsonSchema(), command.maxTokens(), false, command.byokSecret());
        }
        return new UpstreamChatResponse(
                ctx.completionId,
                ctx.route.modelId(),
                OpenAiMessage.assistant("这次没有生成完整结果，请再试一次。"),
                "stop",
                TokenUsage.zero());
    }

    private String executeTool(ChatTurnCommand command, OpenAiToolCall call) {
        if (tools == null) {
            return "工具暂不可用";
        }
        String version = "1";
        ToolResult result = tools.execute(new ToolExecutionCommand(
                call.function().name(),
                version,
                new SessionRef(command.sessionId(), command.workCode()),
                command.accountId(),
                command.workCode(),
                command.sessionId() + ":" + call.id(),
                call.function().arguments()));
        if (result.success()) {
            return result.payloadJson();
        }
        return result.message() == null || result.message().isBlank() ? "工具调用失败" : result.message();
    }

    private void persistAssistant(TurnContext ctx, UpstreamChatResponse response) {
        conversations.append(ctx.conversation.sessionId(), ChatMessage.fromAssistant(
                ctx.conversation.sessionId(), ctx.turnId, response.content(), response.toolCalls()));
    }

    private void appendNewUserMessages(ChatTurnCommand command, String turnId) {
        for (OpenAiMessage message : command.clientMessages()) {
            if (message.isSystem()) {
                continue;
            }
            if ("user".equalsIgnoreCase(message.role())) {
                conversations.append(command.sessionId(), ChatMessage.fromUser(command.sessionId(), turnId, message.content()));
            }
        }
    }

    private String loadState(ChatTurnCommand command) {
        if (sessionState == null) {
            return "{}";
        }
        SessionStateSnapshot snapshot = sessionState.load(new SessionRef(command.sessionId(), command.workCode()));
        return snapshot == null ? "{}" : snapshot.payloadJson();
    }

    private ChatTurnResult toResult(TurnContext ctx, UpstreamChatResponse response, boolean degraded) {
        Instant now = clock.instant();
        OpenAiUsage usage = OpenAiUsage.of(
                response.usage().promptTokens(),
                response.usage().completionTokens(),
                response.usage().cachedTokens());
        OpenAiCompletionResponse body = new OpenAiCompletionResponse(
                ctx.completionId,
                "chat.completion",
                now.getEpochSecond(),
                ctx.route.modelId(),
                List.of(new OpenAiCompletionResponse.Choice(0, response.message(), response.finishReason())),
                usage);
        return new ChatTurnResult(ctx.turnId, ctx.promptVersion, ctx.route, body, response.usage(), degraded);
    }

    private void pumpText(TurnContext ctx, String text, SseSink sink, CancellationToken cancel) {
        Instant now = clock.instant();
        if (text == null) {
            text = "";
        }
        for (int i = 0; i < text.length(); i++) {
            cancel.throwIfCancelled();
            sink.data(codec.writeChunk(OpenAiChunk.delta(ctx.completionId, ctx.route.modelId(), now.getEpochSecond(), text.substring(i, i + 1))));
        }
        sink.data(codec.writeChunk(OpenAiChunk.stop(ctx.completionId, ctx.route.modelId(), now.getEpochSecond())));
        sink.data("[DONE]");
        sink.complete();
    }

    private void report(TurnContext ctx, TokenUsage usage, String outcome) {
        langfuse.report(new LangfusePort.AiTrace(
                "",
                "",
                ctx.conversation.sessionId(),
                ctx.turnId,
                ctx.conversation.workCode(),
                ctx.promptVersion,
                ctx.route,
                usage,
                outcome,
                true));
    }

    private static TokenUsage estimate(String text) {
        int completion = text == null ? 0 : Math.max(1, text.length() / 4);
        return new TokenUsage(0, completion, 0);
    }

    private static final class TurnContext {
        private final String turnId;
        private final String completionId;
        private final Conversation conversation;
        private final String promptVersion;
        private AssembledPrompt assembled;
        private final RoutedModel route;
        private final AiChargePort.Hold hold;
        private boolean degradedToText;

        private TurnContext(
                String turnId,
                String completionId,
                Conversation conversation,
                String promptVersion,
                AssembledPrompt assembled,
                RoutedModel route,
                AiChargePort.Hold hold,
                boolean degradedToText
        ) {
            this.turnId = turnId;
            this.completionId = completionId;
            this.conversation = conversation;
            this.promptVersion = promptVersion;
            this.assembled = assembled;
            this.route = route;
            this.hold = hold;
            this.degradedToText = degradedToText;
        }
    }
}
