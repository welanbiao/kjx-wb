package com.koujunxin.server.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import com.koujunxin.ai.api.AiException;
import com.koujunxin.ai.api.ByokSecret;
import com.koujunxin.ai.api.ModelTier;
import com.koujunxin.ai.api.RoutedModel;
import com.koujunxin.ai.api.port.CancellationToken;
import com.koujunxin.ai.api.port.UpstreamChatRequest;
import com.koujunxin.ai.openai.OpenAiCodec;
import com.koujunxin.ai.openai.OpenAiMessage;
import com.koujunxin.channel.api.DnsLookup;
import com.koujunxin.channel.api.SsrfGuard;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class PlatformUpstreamChatClientTest {

    private static final String PLATFORM_KEY = "platform-key-do-not-leak";
    private static final String BYOK_KEY = "byok-secret-key";

    @Test
    void privateAddressIsRejectedBySsrfGuard() {
        RecordingTransport transport = new RecordingTransport();
        PlatformAiProperties properties = properties("http://10.0.0.1");
        PlatformUpstreamChatClient client = client(properties, transport);
        AiException error = assertThrows(
                AiException.class,
                () -> client.complete(platformRequest(), new CancellationToken()));
        assertEquals("endpoint rejected", error.getMessage());
        assertTrue(transport.requests.isEmpty());
    }

    @Test
    void byokRequestDoesNotSendPlatformKey() throws Exception {
        RecordingTransport transport = new RecordingTransport();
        transport.body = """
                {"id":"c1","model":"user-model","choices":[{"index":0,"message":{"role":"assistant","content":"ok"},"finish_reason":"stop"}]}
                """;
        PlatformUpstreamChatClient client = client(properties("https://gw.example"), transport);
        client.complete(byokRequest("https://byok.example/v1"), new CancellationToken());
        assertEquals(1, transport.requests.size());
        UpstreamHttpRequest sent = transport.requests.getFirst();
        assertEquals("Bearer " + BYOK_KEY, sent.headers().get("Authorization"));
        sent.headers().values().forEach(value -> assertTrue(
                value == null || !value.contains(PLATFORM_KEY),
                "BYOK request must not carry the platform key"));
        assertTrue(!sent.jsonBody().contains(PLATFORM_KEY));
        assertTrue(sent.url().startsWith("https://byok.example/"));
    }

    @Test
    void redirectToLoopbackIsRejected() {
        RecordingTransport transport = new RecordingTransport();
        transport.handler = request -> UpstreamHttpResponse.text(302, "http://127.0.0.1/v1/chat/completions", "");
        PlatformUpstreamChatClient client = client(properties("https://gw.example"), transport);
        AiException error = assertThrows(
                AiException.class,
                () -> client.complete(platformRequest(), new CancellationToken()));
        assertEquals("endpoint rejected", error.getMessage());
        assertEquals(1, transport.requests.size());
    }

    @Test
    void blankPlatformKeyStillSendsByokToUserUrlWithoutLoggingSecret() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(PlatformUpstreamChatClient.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            RecordingTransport transport = new RecordingTransport();
            transport.body = """
                    {"id":"c1","model":"user-model","choices":[{"index":0,"message":{"role":"assistant","content":"ok"},"finish_reason":"stop"}]}
                    """;
            PlatformUpstreamChatClient client = client(blankProperties(), transport);
            client.complete(byokRequest("https://byok.example/v1"), new CancellationToken());
            assertEquals(1, transport.requests.size());
            UpstreamHttpRequest sent = transport.requests.getFirst();
            assertEquals("https://byok.example/v1/chat/completions", sent.url());
            assertEquals("Bearer " + BYOK_KEY, sent.headers().get("Authorization"));
            sent.headers().values().forEach(value -> assertTrue(
                    value == null || !value.contains(PLATFORM_KEY),
                    "BYOK must not fall back to a platform key"));
            assertFalse(sent.jsonBody().contains(BYOK_KEY));
            String logs = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .collect(Collectors.joining("\n"));
            assertFalse(logs.contains(BYOK_KEY));
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void blankPlatformKeyRejectsPlatformChannelWithoutCallingTransport() {
        RecordingTransport transport = new RecordingTransport();
        PlatformUpstreamChatClient client = client(blankProperties(), transport);
        AiException error = assertThrows(
                AiException.class,
                () -> client.complete(platformRequest(), new CancellationToken()));
        assertEquals("AI_REJECTED", error.name());
        assertEquals("平台模型通道未配置", error.getMessage());
        assertTrue(transport.requests.isEmpty());
    }

    private static PlatformUpstreamChatClient client(PlatformAiProperties properties, UpstreamHttpTransport transport) {
        return new PlatformUpstreamChatClient(
                properties, new SsrfGuard(lookup()), new OpenAiCodec(), transport);
    }

    private static PlatformAiProperties properties(String baseUrl) {
        PlatformAiProperties properties = new PlatformAiProperties();
        properties.setBaseUrl(baseUrl);
        properties.setApiKey(PLATFORM_KEY);
        return properties;
    }

    private static PlatformAiProperties blankProperties() {
        return new PlatformAiProperties();
    }

    private static UpstreamChatRequest platformRequest() {
        return new UpstreamChatRequest(
                RoutedModel.platform(ModelTier.STRONG, "kjx-strong"),
                List.of(OpenAiMessage.user("hi")),
                List.of(),
                null,
                16,
                false,
                null);
    }

    private static UpstreamChatRequest byokRequest(String baseUrl) {
        return new UpstreamChatRequest(
                RoutedModel.byok("user-model", baseUrl),
                List.of(OpenAiMessage.user("hi")),
                List.of(),
                null,
                16,
                false,
                ByokSecret.of(BYOK_KEY));
    }

    private static DnsLookup lookup() {
        return host -> {
            String name = host == null ? "" : host.toLowerCase(Locale.ROOT);
            if ("127.0.0.1".equals(name) || "localhost".equals(name)) {
                return List.of(InetAddress.getByAddress(name, new byte[] {127, 0, 0, 1}));
            }
            if ("10.0.0.1".equals(name)) {
                return List.of(InetAddress.getByAddress(name, new byte[] {10, 0, 0, 1}));
            }
            return List.of(InetAddress.getByAddress(name, new byte[] {8, 8, 8, 8}));
        };
    }

    private static final class RecordingTransport implements UpstreamHttpTransport {
        private final List<UpstreamHttpRequest> requests = new ArrayList<>();
        private String body = "{}";
        private ResponseHandler handler;

        @Override
        public UpstreamHttpResponse send(UpstreamHttpRequest request) throws IOException {
            requests.add(request);
            if (handler != null) {
                return handler.handle(request);
            }
            return UpstreamHttpResponse.text(200, null, body);
        }
    }

    @FunctionalInterface
    private interface ResponseHandler {
        UpstreamHttpResponse handle(UpstreamHttpRequest request) throws IOException;
    }
}
