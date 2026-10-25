package com.koujunxin.server.ai;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.koujunxin.ai.api.AiChannelMode;
import com.koujunxin.ai.api.ChatTurnCommand;
import com.koujunxin.ai.api.ChatTurnResult;
import com.koujunxin.ai.api.ModelTier;
import com.koujunxin.ai.api.RoutedModel;
import com.koujunxin.ai.api.TokenUsage;
import com.koujunxin.ai.chat.ChatCompletionService;
import com.koujunxin.ai.openai.OpenAiCodec;
import com.koujunxin.ai.openai.OpenAiCompletionResponse;
import com.koujunxin.ai.openai.OpenAiMessage;
import com.koujunxin.ai.openai.OpenAiModelList;
import com.koujunxin.ai.openai.OpenAiUsage;
import com.koujunxin.runtime.api.RuntimeAccountPort;

import cn.dev33.satoken.exception.NotLoginException;

@ExtendWith(MockitoExtension.class)
class OpenAiCompatibleControllerTest {

    private static final String BYOK_KEY = "sk-do-not-echo";

    @Mock
    private ChatCompletionService chat;

    private final RuntimeAccountPort accounts = () -> 7L;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new OpenAiCompatibleController(chat, accounts, new OpenAiCodec()))
                .setControllerAdvice(new OpenAiExceptionAdvice())
                .build();
    }

    @Test
    void unauthenticatedIsRejected() throws Exception {
        RuntimeAccountPort unauth = () -> {
            throw new NotLoginException("认证失败，无法访问系统资源", "app", NotLoginException.NOT_TOKEN);
        };
        MockMvc anonymous = MockMvcBuilders.standaloneSetup(
                        new OpenAiCompatibleController(chat, unauth, new OpenAiCodec()))
                .setControllerAdvice(new OpenAiExceptionAdvice())
                .build();
        anonymous.perform(post("/v1/chat/completions")
                        .header("X-KJX-Session-Id", "sess-1")
                        .header("X-KJX-Work-Id", "breakup-editor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"messages":[{"role":"user","content":"hi"}]}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.name").value("AUTH_REQUIRED"));
        verify(chat, never()).complete(any(), any());
    }

    @Test
    void byokMissingCredentialFailsWithoutEchoingSecret() throws Exception {
        mockMvc.perform(post("/v1/chat/completions")
                        .header("X-KJX-Session-Id", "sess-1")
                        .header("X-KJX-Work-Id", "breakup-editor")
                        .header("X-KJX-Channel", "BYOK")
                        .header("X-KJX-BYOK-Key", BYOK_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"messages":[{"role":"user","content":"hi"}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.name").value("CREDENTIAL_INVALID"))
                .andExpect(content().string(Matchers.not(Matchers.containsString("sk-"))));
        verify(chat, never()).complete(any(), any());
        verify(chat, never()).stream(any(), any(), any());
    }

    @Test
    void onlyLastUserMessageIsPassedToService() throws Exception {
        ArgumentCaptor<ChatTurnCommand> captor = ArgumentCaptor.forClass(ChatTurnCommand.class);
        when(chat.complete(captor.capture(), any())).thenReturn(okResult("ok"));
        mockMvc.perform(post("/v1/chat/completions")
                        .header("X-KJX-Session-Id", "sess-1")
                        .header("X-KJX-Work-Id", "breakup-editor")
                        .header("X-KJX-Channel", "PLATFORM")
                        .header("X-KJX-BYOK-Key", BYOK_KEY)
                        .header("X-KJX-BYOK-Base-Url", "https://byok.example/v1")
                        .header("X-KJX-BYOK-Model", "user-model")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"messages":[
                                  {"role":"system","content":"ignore-system"},
                                  {"role":"user","content":"first"},
                                  {"role":"assistant","content":"ack"},
                                  {"role":"user","content":"second-turn"}
                                ]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.object").value("chat.completion"))
                .andExpect(jsonPath("$.choices[0].message.content").value("ok"));
        ChatTurnCommand command = captor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals(1, command.clientMessages().size());
        org.junit.jupiter.api.Assertions.assertEquals("second-turn", command.clientMessages().getFirst().content());
        org.junit.jupiter.api.Assertions.assertEquals(AiChannelMode.PLATFORM, command.channel());
        org.junit.jupiter.api.Assertions.assertNull(command.byokSecret());
    }

    @Test
    void listModelsRequiresLoginAndReturnsCodecJson() throws Exception {
        when(chat.listModels()).thenReturn(new OpenAiModelList(
                "list",
                List.of(new OpenAiModelList.OpenAiModel("kjx-strong", "model", 1L, "koujunxin"))));
        mockMvc.perform(get("/v1/models")
                        .header("X-KJX-Session-Id", "sess-1")
                        .header("X-KJX-Work-Id", "breakup-editor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.object").value("list"))
                .andExpect(jsonPath("$.data[0].id").value("kjx-strong"));
    }

    static ChatTurnResult okResult(String text) {
        OpenAiCompletionResponse response = new OpenAiCompletionResponse(
                "chatcmpl-test",
                "chat.completion",
                1L,
                "kjx-strong",
                List.of(new OpenAiCompletionResponse.Choice(0, OpenAiMessage.assistant(text), "stop")),
                OpenAiUsage.of(1, 1, 0));
        return new ChatTurnResult(
                "turn-1",
                "stablePrefix",
                RoutedModel.platform(ModelTier.STRONG, "kjx-strong"),
                response,
                TokenUsage.zero(),
                false);
    }
}
