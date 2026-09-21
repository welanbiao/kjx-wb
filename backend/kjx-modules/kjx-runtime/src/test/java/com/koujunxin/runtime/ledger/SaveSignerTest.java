package com.koujunxin.runtime.ledger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.koujunxin.runtime.api.RuntimeFailure;

class SaveSignerTest {

    @Test
    void signatureBindsAccountSessionAndWork() {
        SaveSigner signer = new SaveSigner("unit-test-runtime-secret");
        String sig = signer.sign(1L, "s1", "work-a", "auto", "1", "c-1", "abc");
        assertDoesNotThrow(() -> signer.verify(1L, "s1", "work-a", "auto", "1", "c-1", "abc", sig));
        assertThrows(RuntimeFailure.class, () -> signer.verify(2L, "s1", "work-a", "auto", "1", "c-1", "abc", sig));
        assertThrows(RuntimeFailure.class, () -> signer.verify(1L, "s2", "work-a", "auto", "1", "c-1", "abc", sig));
        assertThrows(RuntimeFailure.class, () -> signer.verify(1L, "s1", "work-b", "auto", "1", "c-1", "abc", sig));
    }
}
