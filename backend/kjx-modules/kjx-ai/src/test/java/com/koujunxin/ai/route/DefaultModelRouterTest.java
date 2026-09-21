package com.koujunxin.ai.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.koujunxin.ai.api.AiChannelMode;
import com.koujunxin.ai.api.AiException;
import com.koujunxin.ai.api.AiTaskKind;
import com.koujunxin.ai.api.ByokSecret;
import com.koujunxin.ai.api.ModelTier;
import com.koujunxin.ai.api.RoutedModel;
import com.koujunxin.ai.api.port.ModelRouter;

class DefaultModelRouterTest {

    private final DefaultModelRouter router = new DefaultModelRouter();

    @Test
    void platformChatUsesStrongModelAndByokMissingSecretFailsWithoutFallback() {
        RoutedModel platform = router.route(new ModelRouter.RouteRequest(
                AiChannelMode.PLATFORM, AiTaskKind.CHAT, null, null, null));
        assertEquals(AiChannelMode.PLATFORM, platform.channel());
        assertEquals(ModelTier.STRONG, platform.tier());
        assertEquals("kjx-strong", platform.modelId());
        assertNull(platform.baseUrl());

        RoutedModel cheap = router.route(new ModelRouter.RouteRequest(
                AiChannelMode.PLATFORM, AiTaskKind.SUMMARY, null, null, null));
        assertEquals("kjx-cheap", cheap.modelId());

        AiException missing = assertThrows(AiException.class, () -> router.route(new ModelRouter.RouteRequest(
                AiChannelMode.BYOK, AiTaskKind.CHAT, "https://gw.example/v1", "my-model", null)));
        assertEquals("CREDENTIAL_INVALID", missing.name());
    }

    @Test
    void byokUsesCallerEndpointAndDoesNotReadChannelModuleTypes() {
        ByokSecret secret = ByokSecret.of("sk-test-not-for-logs");
        RoutedModel routed = router.route(new ModelRouter.RouteRequest(
                AiChannelMode.BYOK, AiTaskKind.CHAT, "https://gw.example/v1", "user-model", secret));
        assertEquals(AiChannelMode.BYOK, routed.channel());
        assertEquals("user-model", routed.modelId());
        assertEquals("https://gw.example/v1", routed.baseUrl());
        assertFalseContainsSecret(routed.toString(), secret.toString());
        secret.wipe();
    }

    private static void assertFalseContainsSecret(String routed, String redacted) {
        org.junit.jupiter.api.Assertions.assertFalse(routed.contains("sk-test-not-for-logs"));
        org.junit.jupiter.api.Assertions.assertEquals("ByokSecret[redacted]", redacted);
    }
}
