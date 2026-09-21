package com.koujunxin.ai.degrade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.koujunxin.ai.api.AiChannelMode;
import com.koujunxin.ai.api.ModelTier;
import com.koujunxin.ai.api.RoutedModel;
import com.koujunxin.ai.api.TokenUsage;
import com.koujunxin.ai.api.port.CancellationToken;
import com.koujunxin.ai.api.port.UpstreamChatRequest;
import com.koujunxin.ai.api.port.UpstreamChatResponse;
import com.koujunxin.ai.api.port.UpstreamException;
import com.koujunxin.ai.openai.OpenAiMessage;
import com.koujunxin.ai.openai.OpenAiTool;
import com.koujunxin.ai.openai.OpenAiToolCall;
import com.koujunxin.ai.prompt.AssembledPrompt;
import com.koujunxin.ai.support.ScriptedUpstreamClient;

class OutputDegraderTest {

    private final AssembledPrompt assembled = new AssembledPrompt("1.0.0", "prefix", "[state]\n{}", List.of());
    private final RoutedModel route = RoutedModel.platform(ModelTier.STRONG, "kjx-strong");

    @Test
    void usesToolCallingWhenUpstreamAcceptsTools() {
        ScriptedUpstreamClient client = new ScriptedUpstreamClient().enqueue(request -> {
            org.junit.jupiter.api.Assertions.assertFalse(request.tools().isEmpty());
            return new UpstreamChatResponse(
                    "id",
                    "m",
                    OpenAiMessage.assistantTools(List.of(new OpenAiToolCall(
                            "call-1",
                            "function",
                            new OpenAiToolCall.OpenAiFunctionCall("lookup", "{\"id\":1}")))),
                    "tool_calls",
                    TokenUsage.zero());
        });
        OutputDegrader.DegradedResult result = new OutputDegrader(client).complete(
                assembled, List.of(tool()), null, base(), new CancellationToken());
        assertFalse(result.degradedToText());
        assertEquals("lookup", result.response().toolCalls().getFirst().function().name());
    }

    @Test
    void fallsBackToJsonSchemaThenPlainReadableText() {
        ScriptedUpstreamClient client = new ScriptedUpstreamClient()
                .enqueue(request -> {
                    throw UpstreamException.unsupportedTools();
                })
                .enqueue(request -> {
                    throw UpstreamException.unsupportedSchema();
                })
                .enqueue(request -> ScriptedUpstreamClient.text("不是合法json，只是一句说明。"));
        OutputDegrader.DegradedResult result = new OutputDegrader(client).complete(
                assembled,
                List.of(tool()),
                "{\"type\":\"object\",\"required\":[\"name\"]}",
                base(),
                new CancellationToken());
        assertTrue(result.degradedToText());
        assertEquals("不是合法json，只是一句说明。", result.response().content());
        assertEquals(3, client.completeCalls());
    }

    @Test
    void plainChatDoesNotInjectJsonConstraint() {
        ScriptedUpstreamClient client = new ScriptedUpstreamClient()
                .enqueue(request -> {
                    org.junit.jupiter.api.Assertions.assertTrue(request.messages().stream()
                            .noneMatch(message -> message.content() != null && message.content().contains("只输出一个 JSON")));
                    org.junit.jupiter.api.Assertions.assertTrue(request.tools().isEmpty());
                    return ScriptedUpstreamClient.text("普通回复");
                });
        OutputDegrader.DegradedResult result = new OutputDegrader(client).complete(
                assembled, List.of(), null, base(), new CancellationToken());
        assertFalse(result.degradedToText());
        assertEquals("普通回复", result.response().content());
        assertEquals(1, client.completeCalls());
    }

    @Test
    void jsonSchemaSuccessAfterToolsRejected() {
        ScriptedUpstreamClient client = new ScriptedUpstreamClient()
                .enqueue(request -> {
                    throw UpstreamException.unsupportedTools();
                })
                .enqueue(request -> ScriptedUpstreamClient.text("```json\n{\"name\":\"林\"}\n```"));
        OutputDegrader.DegradedResult result = new OutputDegrader(client).complete(
                assembled,
                List.of(tool()),
                "{\"type\":\"object\",\"required\":[\"name\"]}",
                base(),
                new CancellationToken());
        assertFalse(result.degradedToText());
        assertTrue(result.response().content().contains("\"name\""));
    }

    private UpstreamChatRequest base() {
        return new UpstreamChatRequest(route, assembled.toMessages(), List.of(), null, 64, false, null);
    }

    private static OpenAiTool tool() {
        return new OpenAiTool("function", new OpenAiTool.OpenAiFunction("lookup", "lookup", java.util.Map.of("type", "object")));
    }
}
