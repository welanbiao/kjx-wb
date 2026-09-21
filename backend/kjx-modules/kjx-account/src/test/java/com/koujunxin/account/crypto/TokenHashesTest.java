package com.koujunxin.account.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class TokenHashesTest {

    @Test
    void sha256Is64HexAndNotPlaintext() {
        String token = "satoken-plain-value";
        String hash = TokenHashes.sha256Hex(token);
        assertEquals(64, hash.length());
        assertNotEquals(token, hash);
        assertEquals(hash, TokenHashes.sha256Hex(token));
    }
}
