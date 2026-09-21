package com.koujunxin.runtime.ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.koujunxin.runtime.api.RuntimeFailure;

class CanonicalJsonTest {

    @Test
    void checksumIsStableForKeyOrder() {
        String left = CanonicalJson.checksum("1", "c-1", "1", "{\"b\":2,\"a\":1}");
        String right = CanonicalJson.checksum("1", "c-1", "1", "{\"a\":1,\"b\":2}");
        assertEquals(left, right);
    }

    @Test
    void checksumChangesWhenPayloadChanges() {
        String left = CanonicalJson.checksum("1", "c-1", "1", "{\"a\":1}");
        String right = CanonicalJson.checksum("1", "c-1", "1", "{\"a\":2}");
        assertNotEquals(left, right);
    }

    @Test
    void rejectsUnknownSchemaAndTamperedChecksum() {
        assertThrows(RuntimeFailure.class, () -> CanonicalJson.requireSchema("9"));
        String checksum = CanonicalJson.checksum("1", "c-1", "1", "{\"a\":1}");
        RuntimeFailure failure = assertThrows(RuntimeFailure.class,
                () -> CanonicalJson.verifyChecksum("1", "c-1", "1", "{\"a\":2}", checksum));
        assertEquals("CHECKSUM_MISMATCH", failure.errorName());
    }
}
