package com.koujunxin.server.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import com.koujunxin.ai.api.AiChannelMode;
import com.koujunxin.ai.api.AiException;
import com.koujunxin.ai.api.ChatTurnCommand;
import com.koujunxin.ai.chat.ChatCompletionService;
import com.koujunxin.ai.openai.OpenAiCodec;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

@ExtendWith(MockitoExtension.class)
class ChatCompletionRuntimeAiAdapterTest {

    private static final String TEST_KEY = "sk-test-only";

    @Mock
    private ChatCompletionService chat;

    @Test
    void completeReturnsTextWithoutPromptMasterFields() {
        ArgumentCaptor<ChatTurnCommand> captor = ArgumentCaptor.forClass(ChatTurnCommand.class);
        when(chat.complete(captor.capture(), any())).thenReturn(OpenAiCompatibleControllerTest.okResult("sandbox-reply"));
        ChatCompletionRuntimeAiAdapter adapter = new ChatCompletionRuntimeAiAdapter(chat, new OpenAiCodec());
        String json = adapter.complete(7L, "sess-1", "breakup-editor", """
                {"text":"本轮用户","channel":"PLATFORM"}
                """);
        assertTrue(json.contains("\"text\""));
        assertTrue(json.contains("sandbox-reply"));
        assertFalse(json.contains("stablePrefix"));
        assertFalse(json.contains("promptVersion"));
        assertFalse(json.contains("METHOD"));
        ChatTurnCommand command = captor.getValue();
        assertEquals(AiChannelMode.PLATFORM, command.channel());
        assertNull(command.byokSecret());
        assertNull(command.byokBaseUrl());
        assertEquals(1, command.clientMessages().size());
        assertEquals("本轮用户", command.clientMessages().getFirst().content());
    }

    @Test
    void contentFieldIsAcceptedAsUserMessage() {
        ArgumentCaptor<ChatTurnCommand> captor = ArgumentCaptor.forClass(ChatTurnCommand.class);
        when(chat.complete(captor.capture(), any())).thenReturn(OpenAiCompatibleControllerTest.okResult("ok"));
        ChatCompletionRuntimeAiAdapter adapter = new ChatCompletionRuntimeAiAdapter(chat, new OpenAiCodec());
        adapter.complete(7L, "sess-1", "breakup-editor", "{\"content\":\"from-content\"}");
        assertEquals("from-content", captor.getValue().clientMessages().getFirst().content());
    }

    @Test
    void aiExceptionReturnsNameWithoutStack() {
        when(chat.complete(any(), any())).thenThrow(AiException.promptMissing("breakup-editor"));
        ChatCompletionRuntimeAiAdapter adapter = new ChatCompletionRuntimeAiAdapter(chat, new OpenAiCodec());
        String json = adapter.complete(7L, "sess-1", "breakup-editor", "{\"text\":\"hi\"}");
        assertTrue(json.contains("\"code\":\"PROMPT_NOT_FOUND\""));
        assertFalse(json.contains("Exception"));
        assertFalse(json.contains("stack"));
        assertFalse(json.contains("stablePrefix"));
    }

    @Test
    void byokForwardsRequestKeyAndBaseUrlWithoutLoggingSecret() {
        Logger logger = (Logger) LoggerFactory.getLogger(ChatCompletionRuntimeAiAdapter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            ArgumentCaptor<ChatTurnCommand> captor = ArgumentCaptor.forClass(ChatTurnCommand.class);
            when(chat.complete(captor.capture(), any())).thenAnswer(invocation -> {
                ChatTurnCommand command = invocation.getArgument(0);
                assertEquals(AiChannelMode.BYOK, command.channel());
                assertEquals("https://gw.example/v1", command.byokBaseUrl());
                assertEquals("user-model", command.byokModel());
                assertEquals(TEST_KEY, command.byokSecret().reveal());
                assertFalse(command.toString().contains(TEST_KEY));
                return OpenAiCompatibleControllerTest.okResult("byok-reply");
            });
            ChatCompletionRuntimeAiAdapter adapter = new ChatCompletionRuntimeAiAdapter(chat, new OpenAiCodec());
            String json = adapter.complete(7L, "sess-1", "breakup-editor", """
                    {"text":"本轮用户","channel":"BYOK","apiKey":"sk-test-only","baseUrl":"https://gw.example/v1","model":"user-model"}
                    """);
            assertTrue(json.contains("byok-reply"));
            assertFalse(json.contains(TEST_KEY));
            String logs = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .collect(Collectors.joining("\n"));
            assertFalse(logs.contains(TEST_KEY));
            ChatTurnCommand after = captor.getValue();
            assertFalse(after.byokSecret().reveal().contains(TEST_KEY));
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void byokMissingApiKeyOrEndpointDoesNotCallChat() {
        ChatCompletionRuntimeAiAdapter adapter = new ChatCompletionRuntimeAiAdapter(chat, new OpenAiCodec());
        String missingKey = adapter.complete(7L, "sess-1", "breakup-editor", """
                {"text":"本轮用户","channel":"BYOK","baseUrl":"https://gw.example/v1","model":"user-model"}
                """);
        assertTrue(missingKey.contains("\"code\":\"CREDENTIAL_INVALID\""));
        assertFalse(missingKey.contains(TEST_KEY));
        String missingEndpoint = adapter.complete(7L, "sess-1", "breakup-editor", """
                {"text":"本轮用户","channel":"BYOK","apiKey":"sk-test-only","model":"user-model"}
                """);
        assertTrue(missingEndpoint.contains("\"code\":\"CREDENTIAL_INVALID\""));
        assertFalse(missingEndpoint.contains(TEST_KEY));
        verify(chat, never()).complete(any(), any());
    }
}
