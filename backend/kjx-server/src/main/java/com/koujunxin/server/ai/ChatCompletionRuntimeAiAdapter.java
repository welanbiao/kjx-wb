package com.koujunxin.server.ai;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;

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
import com.koujunxin.ai.openai.OpenAiMessage;
import com.koujunxin.runtime.api.RuntimeAiPort;

@ConditionalOnBean(ChatCompletionService.class)
public final class ChatCompletionRuntimeAiAdapter implements RuntimeAiPort {

    private static final Logger log = LoggerFactory.getLogger(ChatCompletionRuntimeAiAdapter.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ChatCompletionService chat;
    private final OpenAiCodec codec;

    public ChatCompletionRuntimeAiAdapter(ChatCompletionService chat, OpenAiCodec codec) {
        this.chat = chat;
        this.codec = codec;
    }

    @Override
    public String complete(long accountId, String sessionId, String workCode, String payloadJson) {
        ChatTurnCommand command = null;
        ByokSecret secret = null;
        try {
            JsonNode payload = readPayload(payloadJson);
            String text = userText(payload);
            AiChannelMode channel = channelOf(payload);
            String byokBaseUrl = null;
            String byokModel = null;
            if (channel == AiChannelMode.BYOK) {
                String apiKey = textField(payload, "apiKey");
                String endpoint = firstNonBlank(textField(payload, "endpoint"), textField(payload, "baseUrl"));
                if (isBlank(apiKey) || isBlank(endpoint)) {
                    throw AiException.byokCredentialRequired();
                }
                secret = ByokSecret.of(apiKey);
                byokBaseUrl = endpoint;
                byokModel = blankToNull(textField(payload, "model"));
            }
            command = new ChatTurnCommand(
                    accountId,
                    sessionId,
                    workCode,
                    null,
                    channel,
                    secret,
                    byokBaseUrl,
                    byokModel,
                    AiTaskKind.CHAT,
                    List.of(OpenAiMessage.user(text)),
                    List.of(),
                    null,
                    false,
                    null,
                    "",
                    "");
            secret = null;
            ChatTurnResult result = chat.complete(command, new CancellationToken());
            Map<String, String> body = new LinkedHashMap<>();
            body.put("text", assistantText(result));
            return codec.write(body);
        } catch (AiException ex) {
            log.warn("ai.runtime.failed name={}", ex.name());
            return errorJson(ex.name(), OpenAiExceptionAdvice.safeMessage(ex.getMessage()));
        } catch (RuntimeException ex) {
            log.warn("ai.runtime.failed name={}", ex.getClass().getSimpleName());
            return errorJson("AI_FAILED", "AI completion failed");
        } finally {
            if (secret != null) {
                secret.wipe();
            }
            if (command != null) {
                command.wipeSecret();
            }
        }
    }

    private JsonNode readPayload(String payloadJson) {
        try {
            String raw = payloadJson == null || payloadJson.isBlank() ? "{}" : payloadJson;
            return MAPPER.readTree(raw);
        } catch (Exception ex) {
            throw AiException.rejected("invalid payload");
        }
    }

    private static String userText(JsonNode payload) {
        if (payload == null || payload.isNull() || payload.isMissingNode()) {
            return "";
        }
        if (payload.isTextual()) {
            return payload.asText("");
        }
        if (payload.isObject()) {
            if (payload.hasNonNull("text")) {
                return payload.get("text").asText("");
            }
            if (payload.hasNonNull("content")) {
                return payload.get("content").asText("");
            }
        }
        return "";
    }

    private static String textField(JsonNode payload, String name) {
        if (payload == null || !payload.hasNonNull(name)) {
            return "";
        }
        return payload.get(name).asText("").trim();
    }

    private static String firstNonBlank(String first, String second) {
        if (!isBlank(first)) {
            return first;
        }
        return isBlank(second) ? "" : second;
    }

    private static String blankToNull(String value) {
        return isBlank(value) ? null : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static AiChannelMode channelOf(JsonNode payload) {
        if (payload == null || !payload.hasNonNull("channel")) {
            return AiChannelMode.PLATFORM;
        }
        String raw = payload.get("channel").asText("");
        if (raw.isBlank()) {
            return AiChannelMode.PLATFORM;
        }
        try {
            return AiChannelMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw AiException.rejected("channel must be PLATFORM or BYOK");
        }
    }

    private static String assistantText(ChatTurnResult result) {
        if (result == null || result.response() == null || result.response().choices().isEmpty()) {
            return "";
        }
        OpenAiMessage message = result.response().choices().getFirst().message();
        return message == null || message.content() == null ? "" : message.content();
    }

    private String errorJson(String name, String message) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("code", name);
        body.put("message", message == null ? "" : message);
        return codec.write(body);
    }
}
