package com.koujunxin.account.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;
import java.util.Base64;

import org.junit.jupiter.api.Test;

class PhoneCipherTest {

    @Test
    void encryptIsNotPlaintextAndHmacIsStable() {
        PhoneCipher cipher = randomCipher();
        String phone = "13800138000";
        String encrypted = cipher.encrypt(phone);
        assertNotEquals(phone, encrypted);
        assertTrue(encrypted.matches("^[A-Za-z0-9+/=]+$"));
        assertEquals(phone, cipher.decrypt(encrypted));
        String hash = cipher.hmacHex(phone);
        assertEquals(64, hash.length());
        assertEquals(hash, cipher.hmacHex(phone));
        assertEquals("138****8000", PhoneCipher.mask(phone));
        PhoneCipher otherKey = randomCipher();
        assertNotEquals(hash, otherKey.hmacHex(phone));
    }

    @Test
    void rejectsInvalidPhone() {
        PhoneCipher cipher = randomCipher();
        assertThrows(IllegalArgumentException.class, () -> cipher.encrypt("not-a-phone"));
        assertThrows(IllegalArgumentException.class, () -> cipher.hmacHex("123"));
    }

    static PhoneCipher randomCipher() {
        SecureRandom random = new SecureRandom();
        byte[] aes = new byte[32];
        byte[] hmac = new byte[32];
        random.nextBytes(aes);
        random.nextBytes(hmac);
        return new PhoneCipher(aes, hmac);
    }

    static String keyB64(byte[] raw) {
        return Base64.getEncoder().encodeToString(raw);
    }
}
