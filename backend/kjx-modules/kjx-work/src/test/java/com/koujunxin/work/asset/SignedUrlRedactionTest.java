package com.koujunxin.work.asset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.koujunxin.work.packagecontent.WorkPackageException;

class SignedUrlRedactionTest {

    @Test
    void toStringDoesNotLeakSignatureOrSecret() {
        HmacSignedUrlSigner signer = new HmacSignedUrlSigner("super-secret-credential");
        InMemoryObjectStorage storage = new InMemoryObjectStorage(signer);
        storage.put(StorageScope.CDN, "works/1/cdn/a.png", new byte[] {1, 2, 3}, "image/png", 1L, "1.0.0");
        SignedUrl signed = storage.sign(StorageScope.CDN, "works/1/cdn/a.png", Duration.ofMinutes(10));
        assertTrue(signed.url().contains("&s="));
        assertFalse(signed.toString().contains("super-secret-credential"));
        assertFalse(signed.toString().contains("&s="));
        assertFalse(signed.toString().contains(signed.url()));
        assertTrue(signed.toString().contains("REDACTED"));
    }

    @Test
    void cdnSignRejectsServerKey() {
        HmacSignedUrlSigner signer = new HmacSignedUrlSigner(HmacSignedUrlSigner.LOCAL_DEV_SECRET);
        InMemoryObjectStorage storage = new InMemoryObjectStorage(signer);
        storage.put(StorageScope.SERVER, "works/1/1.0.0/server/prompts/system.md", "secret".getBytes(),
                "text/markdown", 1L, "1.0.0");
        assertThrows(WorkPackageException.class,
                () -> signer.sign(StorageScope.CDN, "works/1/1.0.0/server/prompts/system.md", Duration.ofMinutes(10)));
    }
}
