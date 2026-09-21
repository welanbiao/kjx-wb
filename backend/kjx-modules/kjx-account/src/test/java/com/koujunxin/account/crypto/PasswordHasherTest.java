package com.koujunxin.account.crypto;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PasswordHasherTest {

    private final PasswordHasher hasher = new PasswordHasher();

    @Test
    void argon2HashDoesNotContainPlaintextAndVerifies() {
        String raw = "Abcd1234";
        String hash = hasher.hash(raw);
        assertTrue(hash.startsWith("$argon2"));
        assertTrue(hash.length() <= 128);
        assertFalse(hash.contains(raw));
        assertTrue(hasher.matches(raw, hash));
        assertFalse(hasher.matches("Abcd1235", hash));
        assertNotEquals(hash, hasher.hash(raw));
    }

    @Test
    void rejectsWeakOrShortPasswords() {
        assertThrows(IllegalArgumentException.class, () -> hasher.hash("abc123"));
        assertThrows(IllegalArgumentException.class, () -> hasher.hash("abcdefgh"));
        assertThrows(IllegalArgumentException.class, () -> hasher.hash("12345678"));
        assertThrows(IllegalArgumentException.class, () -> hasher.hash("password1"));
    }
}
