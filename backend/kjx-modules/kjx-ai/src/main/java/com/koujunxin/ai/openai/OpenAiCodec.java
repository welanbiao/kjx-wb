package com.koujunxin.ai.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class OpenAiCodec {

    private final ObjectMapper mapper;

    public OpenAiCodec() {
        this(new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false));
    }

    public OpenAiCodec(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public OpenAiCompletionRequest readRequest(String json) {
        try {
            return mapper.readValue(json, OpenAiCompletionRequest.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("invalid OpenAI request", ex);
        }
    }

    public String writeResponse(OpenAiCompletionResponse response) {
        return write(response);
    }

    public String writeChunk(OpenAiChunk chunk) {
        return write(chunk);
    }

    public String sseData(String payload) {
        return "data: " + payload + "\n\n";
    }

    public String sseComment(String comment) {
        return ": " + comment + "\n\n";
    }

    public String done() {
        return sseData("[DONE]");
    }

    public String write(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to write OpenAI json", ex);
        }
    }
}
