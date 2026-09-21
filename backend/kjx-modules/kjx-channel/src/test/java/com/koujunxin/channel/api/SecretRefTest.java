package com.koujunxin.channel.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class SecretRefTest {

    @Test
    void acceptsReferenceAndRedactsToString() {
        SecretRef ref = new SecretRef("ref:vault.member.1");
        assertEquals("ref:vault.member.1", ref.value());
        assertFalse(ref.toString().contains("vault"));
        assertFalse(new ByokCredential(ref, "https://api.example.com").toString().contains("vault"));
    }

    @Test
    void rejectsPlaintextKeyWithoutEchoingIt() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> SecretRef.require("sk-live-plaintext-key"));
        assertEquals("secretRef rejected", error.getMessage());
        assertFalse(error.getMessage().contains("sk-live"));
    }
}
