package com.koujunxin.ai.openai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class OpenAiCodecTest {

    private final OpenAiCodec codec = new OpenAiCodec();

    @Test
    void parsesRequestAndWritesResponseAndSse() {
        OpenAiCompletionRequest request = codec.readRequest("""
                {
                  "model": "gpt-4",
                  "stream": true,
                  "max_tokens": 32,
                  "messages": [{"role":"user","content":"hi"}],
                  "tools": [{"type":"function","function":{"name":"lookup","parameters":{"type":"object"}}}]
                }
                """);
        assertEquals("gpt-4", request.model());
        assertEquals(Boolean.TRUE, request.stream());
        assertEquals(1, request.messages().size());
        assertEquals("lookup", request.tools().getFirst().function().name());

        OpenAiCompletionResponse response = new OpenAiCompletionResponse(
                "chatcmpl-1",
                "chat.completion",
                1L,
                "kjx-strong",
                List.of(new OpenAiCompletionResponse.Choice(0, OpenAiMessage.assistant("ok"), "stop")),
                OpenAiUsage.of(3, 1, 2));
        String json = codec.writeResponse(response);
        assertTrue(json.contains("\"object\":\"chat.completion\""));
        assertTrue(json.contains("\"finish_reason\":\"stop\""));
        assertTrue(json.contains("cached_tokens"));

        String sse = codec.sseData(codec.writeChunk(OpenAiChunk.delta("chatcmpl-1", "kjx-strong", 1L, "你")));
        assertTrue(sse.startsWith("data: {"));
        assertTrue(sse.contains("chat.completion.chunk"));
        assertEquals("data: [DONE]\n\n", codec.done());
        assertEquals(": ping\n\n", codec.sseComment("ping"));
    }
}
