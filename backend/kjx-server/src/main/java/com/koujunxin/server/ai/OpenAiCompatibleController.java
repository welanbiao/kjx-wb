package com.koujunxin.server.ai;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.koujunxin.ai.api.AiChannelMode;
import com.koujunxin.ai.api.AiException;
import com.koujunxin.ai.api.AiTaskKind;
import com.koujunxin.ai.api.ByokSecret;
import com.koujunxin.ai.api.ChatTurnCommand;
import com.koujunxin.ai.api.ChatTurnResult;
import com.koujunxin.ai.api.port.CancellationToken;
import com.koujunxin.ai.chat.ChatCompletionService;
import com.koujunxin.ai.openai.OpenAiCodec;
import com.koujunxin.ai.openai.OpenAiCompletionRequest;
import com.koujunxin.ai.openai.OpenAiMessage;
import com.koujunxin.ai.sse.SseSink;
import com.koujunxin.runtime.api.RuntimeAccountPort;

@RestController
@ConditionalOnBean(ChatCompletionService.class)
public class OpenAiCompatibleController {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleController.class);
    private static final long SSE_TIMEOUT_MS = 120_000L;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ChatCompletionService chat;
    private final RuntimeAccountPort accounts;
    private final OpenAiCodec codec;

    public OpenAiCompatibleController(
            ChatCompletionService chat,
            RuntimeAccountPort accounts,
            OpenAiCodec codec) {
        this.chat = chat;
        this.accounts = accounts;
        this.codec = codec;
    }

    @GetMapping(value = "/v1/models", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> models(
            @RequestHeader("X-KJX-Session-Id") String sessionId,
            @RequestHeader("X-KJX-Work-Id") String workId) {
        accounts.requireAccountId();
        requireText(sessionId, "X-KJX-Session-Id");
        requireText(workId, "X-KJX-Work-Id");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(codec.write(chat.listModels()));
    }

    @PostMapping(
            value = "/v1/chat/completions",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_EVENT_STREAM_VALUE})
    public Object chatCompletions(
            @RequestHeader("X-KJX-Session-Id") String sessionId,
            @RequestHeader("X-KJX-Work-Id") String workId,
            @RequestHeader(value = "X-KJX-Channel", required = false) String channelHeader,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-KJX-BYOK-Key", required = false) String byokKey,
            @RequestHeader(value = "X-KJX-BYOK-Base-Url", required = false) String byokBaseUrl,
            @RequestHeader(value = "X-KJX-BYOK-Model", required = false) String byokModel,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestBody String body) {
        long accountId = accounts.requireAccountId();
        AiChannelMode channel = parseChannel(channelHeader);
        ChatTurnCommand command = buildCommand(
                accountId,
                sessionId,
                workId,
                idempotencyKey,
                channel,
                byokKey,
                byokBaseUrl,
                byokModel,
                body,
                requestId);
        if (command.stream()) {
            return stream(command);
        }
        CancellationToken cancel = new CancellationToken();
        try {
            ChatTurnResult result = chat.complete(command, cancel);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(codec.writeResponse(result.response()));
        } finally {
            command.wipeSecret();
        }
    }

    private SseEmitter stream(ChatTurnCommand command) {
        CancellationToken cancel = new CancellationToken();
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        emitter.onTimeout(cancel::cancel);
        emitter.onError(error -> cancel.cancel());
        emitter.onCompletion(cancel::cancel);
        Thread.startVirtualThread(() -> {
            try {
                chat.stream(command, new EmitterSseSink(emitter), cancel);
            } catch (RuntimeException ex) {
                log.warn("ai.http.stream.failed name={}", ex instanceof AiException ai ? ai.name() : ex.getClass().getSimpleName());
                try {
                    emitter.completeWithError(ex);
                } catch (Exception ignored) {
                    cancel.cancel();
                }
            } finally {
                command.wipeSecret();
            }
        });
        return emitter;
    }

    private ChatTurnCommand buildCommand(
            long accountId,
            String sessionId,
            String workId,
            String idempotencyKey,
            AiChannelMode channel,
            String byokKey,
            String byokBaseUrl,
            String byokModel,
            String body,
            String requestId) {
        ByokSecret secret = null;
        try {
            OpenAiCompletionRequest request = codec.readRequest(body == null ? "" : body);
            List<OpenAiMessage> clientMessages = lastUserMessage(request.messages());
            if (channel == AiChannelMode.BYOK) {
                if (isBlank(byokKey) || isBlank(byokBaseUrl) || isBlank(byokModel)) {
                    throw AiException.byokCredentialRequired();
                }
                secret = ByokSecret.of(byokKey);
            }
            boolean stream = Boolean.TRUE.equals(request.stream());
            ChatTurnCommand command = new ChatTurnCommand(
                    accountId,
                    sessionId,
                    workId,
                    idempotencyKey,
                    channel,
                    secret,
                    channel == AiChannelMode.BYOK ? byokBaseUrl : null,
                    channel == AiChannelMode.BYOK ? byokModel : null,
                    AiTaskKind.CHAT,
                    clientMessages,
                    request.tools(),
                    jsonSchema(request.responseFormat()),
                    stream,
                    request.maxTokens(),
                    "",
                    requestId == null ? "" : requestId);
            secret = null;
            return command;
        } finally {
            if (secret != null) {
                secret.wipe();
            }
        }
    }

    static AiChannelMode parseChannel(String header) {
        if (header == null || header.isBlank()) {
            return AiChannelMode.PLATFORM;
        }
        try {
            return AiChannelMode.valueOf(header.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw AiException.rejected("X-KJX-Channel must be PLATFORM or BYOK");
        }
    }

    static List<OpenAiMessage> lastUserMessage(List<OpenAiMessage> messages) {
        OpenAiMessage last = null;
        if (messages != null) {
            for (OpenAiMessage message : messages) {
                if (message != null && "user".equalsIgnoreCase(message.role())) {
                    last = message;
                }
            }
        }
        return last == null ? List.of() : List.of(last);
    }

    private String jsonSchema(Object responseFormat) {
        if (responseFormat == null) {
            return null;
        }
        try {
            JsonNode node = MAPPER.valueToTree(responseFormat);
            JsonNode schema = node.path("json_schema").path("schema");
            if (schema.isObject() || schema.isArray()) {
                return MAPPER.writeValueAsString(schema);
            }
            JsonNode nested = node.get("schema");
            if (nested != null && (nested.isObject() || nested.isArray())) {
                return MAPPER.writeValueAsString(nested);
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    static final class EmitterSseSink implements SseSink {

        private final SseEmitter emitter;

        EmitterSseSink(SseEmitter emitter) {
            this.emitter = emitter;
        }

        @Override
        public void comment(String comment) {
            send(SseEmitter.event().comment(comment));
        }

        @Override
        public void data(String payload) {
            send(SseEmitter.event().data(payload));
        }

        @Override
        public void complete() {
            emitter.complete();
        }

        private void send(SseEmitter.SseEventBuilder event) {
            try {
                emitter.send(event);
            } catch (IOException ex) {
                try {
                    emitter.complete();
                } catch (Exception ignored) {
                    // client already gone
                }
                throw AiException.cancelled();
            }
        }
    }
}
