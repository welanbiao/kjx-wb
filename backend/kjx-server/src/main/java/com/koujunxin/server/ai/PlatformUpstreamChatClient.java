package com.koujunxin.server.ai;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.koujunxin.ai.api.AiChannelMode;
import com.koujunxin.ai.api.AiException;
import com.koujunxin.ai.api.TokenUsage;
import com.koujunxin.ai.api.port.CancellationToken;
import com.koujunxin.ai.api.port.UpstreamChatClient;
import com.koujunxin.ai.api.port.UpstreamChatRequest;
import com.koujunxin.ai.api.port.UpstreamChatResponse;
import com.koujunxin.ai.api.port.UpstreamStreamListener;
import com.koujunxin.ai.openai.OpenAiChunk;
import com.koujunxin.ai.openai.OpenAiCodec;
import com.koujunxin.ai.openai.OpenAiCompletionRequest;
import com.koujunxin.ai.openai.OpenAiCompletionResponse;
import com.koujunxin.ai.openai.OpenAiMessage;
import com.koujunxin.ai.openai.OpenAiUsage;
import com.koujunxin.channel.api.SsrfGuard;

public final class PlatformUpstreamChatClient implements UpstreamChatClient {

    private static final Logger log = LoggerFactory.getLogger(PlatformUpstreamChatClient.class);
    private static final int MAX_REDIRECTS = 2;

    private final PlatformAiProperties properties;
    private final SsrfGuard ssrf;
    private final OpenAiCodec codec;
    private final UpstreamHttpTransport transport;
    private final ObjectMapper mapper;

    public PlatformUpstreamChatClient(
            PlatformAiProperties properties,
            SsrfGuard ssrf,
            OpenAiCodec codec,
            UpstreamHttpTransport transport) {
        if (ssrf == null || codec == null || transport == null) {
            throw new IllegalArgumentException("upstream client dependencies are required");
        }
        this.properties = properties == null ? new PlatformAiProperties() : properties;
        this.ssrf = ssrf;
        this.codec = codec;
        this.transport = transport;
        this.mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public UpstreamChatResponse complete(UpstreamChatRequest request, CancellationToken cancel) {
        cancel.throwIfCancelled();
        assertChannelReady(request);
        try (UpstreamHttpResponse response = exchange(request, false, cancel)) {
            cancel.throwIfCancelled();
            if (response.status() < 200 || response.status() >= 300) {
                log.warn("ai.upstream.http status={}", response.status());
                throw upstreamFailed(request);
            }
            String body = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
            cancel.throwIfCancelled();
            return parseCompletion(request, body);
        } catch (AiException ex) {
            throw ex;
        } catch (IllegalArgumentException ex) {
            throw AiException.rejected("endpoint rejected");
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("ai.upstream.transport_failed");
            throw upstreamFailed(request);
        }
    }

    @Override
    public void stream(UpstreamChatRequest request, UpstreamStreamListener listener, CancellationToken cancel) {
        cancel.throwIfCancelled();
        assertChannelReady(request);
        try (UpstreamHttpResponse response = exchange(request, true, cancel)) {
            cancel.throwIfCancelled();
            if (response.status() < 200 || response.status() >= 300) {
                log.warn("ai.upstream.http status={}", response.status());
                listener.onComplete(errorResponse(request));
                return;
            }
            parseSse(request, response, listener, cancel);
        } catch (AiException ex) {
            throw ex;
        } catch (IllegalArgumentException ex) {
            throw AiException.rejected("endpoint rejected");
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("ai.upstream.transport_failed");
            listener.onComplete(errorResponse(request));
        }
    }

    private UpstreamHttpResponse exchange(UpstreamChatRequest request, boolean stream, CancellationToken cancel)
            throws IOException, InterruptedException {
        String url = targetUrl(request);
        String json = writeRequest(request, stream);
        String authorization = authorization(request);
        int followed = 0;
        while (true) {
            cancel.throwIfCancelled();
            ssrf.check(url);
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("Content-Type", "application/json");
            headers.put("Accept", stream ? "text/event-stream" : "application/json");
            headers.put("Authorization", authorization);
            UpstreamHttpRequest httpRequest = new UpstreamHttpRequest("POST", url, headers, json, stream);
            UpstreamHttpResponse response = transport.send(httpRequest);
            if (!response.redirect()) {
                return response;
            }
            try (response) {
                if (followed >= MAX_REDIRECTS) {
                    throw AiException.rejected("too many redirects");
                }
                ssrf.checkRedirect(url, response.location());
                url = URI.create(url).resolve(response.location()).toString();
                followed++;
            }
        }
    }

    private String writeRequest(UpstreamChatRequest request, boolean stream) {
        Object responseFormat = null;
        if (request.jsonSchema() != null && !request.jsonSchema().isBlank()) {
            try {
                JsonNode schema = mapper.readTree(request.jsonSchema());
                responseFormat = Map.of(
                        "type", "json_schema",
                        "json_schema", Map.of("name", "response", "schema", schema, "strict", true));
            } catch (IOException ignored) {
                responseFormat = null;
            }
        }
        OpenAiCompletionRequest body = new OpenAiCompletionRequest(
                request.route().modelId(),
                request.messages(),
                stream,
                request.tools().isEmpty() ? List.of() : request.tools(),
                request.tools().isEmpty() ? null : "auto",
                responseFormat,
                request.maxTokens());
        return codec.write(body);
    }

    private void assertChannelReady(UpstreamChatRequest request) {
        if (request.route().channel() == AiChannelMode.BYOK) {
            if (request.secret() == null) {
                throw AiException.byokCredentialRequired();
            }
            return;
        }
        if (!properties.configured()) {
            throw AiException.rejected("平台模型通道未配置");
        }
    }

    private String targetUrl(UpstreamChatRequest request) {
        if (request.route().channel() == AiChannelMode.BYOK) {
            return completionsUrl(request.route().baseUrl());
        }
        return trimSlash(properties.getBaseUrl()) + "/v1/chat/completions";
    }

    private String authorization(UpstreamChatRequest request) {
        if (request.route().channel() == AiChannelMode.BYOK) {
            if (request.secret() == null) {
                throw AiException.byokCredentialRequired();
            }
            return "Bearer " + request.secret().reveal();
        }
        if (!properties.configured()) {
            throw AiException.rejected("平台模型通道未配置");
        }
        return "Bearer " + properties.getApiKey();
    }

    private UpstreamChatResponse parseCompletion(UpstreamChatRequest request, String body) {
        try {
            OpenAiCompletionResponse parsed = mapper.readValue(body, OpenAiCompletionResponse.class);
            if (parsed == null || parsed.choices() == null || parsed.choices().isEmpty()
                    || parsed.choices().getFirst().message() == null) {
                return errorResponse(request);
            }
            OpenAiCompletionResponse.Choice choice = parsed.choices().getFirst();
            return new UpstreamChatResponse(
                    parsed.id(),
                    parsed.model() == null || parsed.model().isBlank() ? request.route().modelId() : parsed.model(),
                    choice.message(),
                    choice.finishReason(),
                    toUsage(parsed.usage()));
        } catch (RuntimeException | IOException ex) {
            log.warn("ai.upstream.parse_failed");
            return errorResponse(request);
        }
    }

    private void parseSse(
            UpstreamChatRequest request,
            UpstreamHttpResponse response,
            UpstreamStreamListener listener,
            CancellationToken cancel) throws IOException {
        StringBuilder content = new StringBuilder();
        String id = "chatcmpl-local";
        String model = request.route().modelId();
        String finishReason = "stop";
        TokenUsage usage = TokenUsage.zero();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (cancel.isCancelled()) {
                    cancel.throwIfCancelled();
                }
                if (line.isEmpty() || line.startsWith(":")) {
                    continue;
                }
                if (!line.startsWith("data:")) {
                    continue;
                }
                String payload = line.substring("data:".length()).trim();
                if (payload.isEmpty()) {
                    continue;
                }
                if ("[DONE]".equals(payload)) {
                    break;
                }
                try {
                    OpenAiChunk chunk = mapper.readValue(payload, OpenAiChunk.class);
                    if (chunk.id() != null && !chunk.id().isBlank()) {
                        id = chunk.id();
                    }
                    if (chunk.model() != null && !chunk.model().isBlank()) {
                        model = chunk.model();
                    }
                    if (chunk.choices() == null || chunk.choices().isEmpty()) {
                        continue;
                    }
                    OpenAiChunk.Choice choice = chunk.choices().getFirst();
                    if (choice.finishReason() != null && !choice.finishReason().isBlank()) {
                        finishReason = choice.finishReason();
                    }
                    if (choice.delta() != null && choice.delta().content() != null && !choice.delta().content().isEmpty()) {
                        content.append(choice.delta().content());
                        listener.onDelta(choice.delta().content());
                    }
                } catch (RuntimeException | IOException ex) {
                    log.warn("ai.upstream.parse_failed");
                    listener.onComplete(errorResponse(request));
                    return;
                }
            }
        }
        if (cancel.isCancelled()) {
            cancel.throwIfCancelled();
        }
        listener.onComplete(new UpstreamChatResponse(
                id, model, OpenAiMessage.assistant(content.toString()), finishReason, usage));
    }

    static String completionsUrl(String baseUrl) {
        String base = trimSlash(baseUrl);
        if (base.endsWith("/chat/completions")) {
            return base;
        }
        if (base.endsWith("/v1")) {
            return base + "/chat/completions";
        }
        return base + "/v1/chat/completions";
    }

    static String trimSlash(String url) {
        if (url == null) {
            return "";
        }
        String value = url.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static TokenUsage toUsage(OpenAiUsage usage) {
        if (usage == null) {
            return TokenUsage.zero();
        }
        int cached = usage.promptTokensDetails() == null ? 0 : usage.promptTokensDetails().cachedTokens();
        return new TokenUsage(usage.promptTokens(), usage.completionTokens(), cached);
    }

    private static UpstreamChatResponse errorResponse(UpstreamChatRequest request) {
        return new UpstreamChatResponse(
                "chatcmpl-error",
                request.route().modelId(),
                OpenAiMessage.assistant(""),
                "error",
                TokenUsage.zero());
    }

    private static AiException upstreamFailed(UpstreamChatRequest request) {
        if (request.route().channel() == AiChannelMode.BYOK) {
            return AiException.byokFailed();
        }
        return AiException.rejected("上游调用失败");
    }
}
