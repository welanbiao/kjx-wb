package com.koujunxin.ai.api;

public record RoutedModel(
        AiChannelMode channel,
        ModelTier tier,
        String modelId,
        String baseUrl
) {

    public RoutedModel {
        if (channel == null) {
            throw new IllegalArgumentException("channel is required");
        }
        if (tier == null) {
            throw new IllegalArgumentException("tier is required");
        }
        modelId = requireText(modelId, "modelId");
        if (channel == AiChannelMode.BYOK) {
            baseUrl = requireText(baseUrl, "baseUrl");
        } else {
            baseUrl = null;
        }
    }

    public static RoutedModel platform(ModelTier tier, String modelId) {
        return new RoutedModel(AiChannelMode.PLATFORM, tier, modelId, null);
    }

    public static RoutedModel byok(String modelId, String baseUrl) {
        return new RoutedModel(AiChannelMode.BYOK, ModelTier.STRONG, modelId, baseUrl);
    }

    @Override
    public String toString() {
        return "RoutedModel[channel=" + channel + ", tier=" + tier + ", modelId=" + modelId + "]";
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
