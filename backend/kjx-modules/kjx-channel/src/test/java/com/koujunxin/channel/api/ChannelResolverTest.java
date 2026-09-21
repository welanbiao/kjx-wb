package com.koujunxin.channel.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ChannelResolverTest {

    private final ChannelResolver resolver = new ChannelResolver();
    private final ByokCredential credential = new ByokCredential(new SecretRef("ref:member-1"), "https://api.example.com");

    @Test
    void workOverridesMemberAndPlatform() {
        ResolvedChannel resolved = resolver.resolve(
                new ChannelPreference(ChannelMode.BYOK, ChannelMode.PLATFORM, ChannelMode.PLATFORM),
                credential);
        assertEquals(ChannelMode.BYOK, resolved.mode());
        assertEquals("work", resolved.source());
    }

    @Test
    void memberOverridesPlatform() {
        ResolvedChannel resolved = resolver.resolve(
                new ChannelPreference(null, ChannelMode.BYOK, ChannelMode.PLATFORM),
                credential);
        assertEquals("member", resolved.source());
    }

    @Test
    void missingByokCredentialDoesNotFallBackToPlatform() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> resolver.resolve(
                new ChannelPreference(ChannelMode.BYOK, null, ChannelMode.PLATFORM),
                null));
        assertTrue(error.getMessage().contains("BYOK"));
    }
}
