package com.koujunxin.notification.outbox.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ErrorDigestTest {

    @Test
    void redactsPhoneTokenAndTruncates() {
        String raw = "Bearer abcdef.secret.token phone=13812345678 Authorization: secret-value "
                + "x".repeat(600);
        String digest = ErrorDigest.sanitize(raw);
        assertThat(digest).doesNotContain("13812345678");
        assertThat(digest).doesNotContain("abcdef.secret.token");
        assertThat(digest.length()).isLessThanOrEqualTo(ErrorDigest.MAX_LENGTH);
        assertThat(digest).contains("***");
    }

    @Test
    void ofThrowableUsesClassName() {
        assertThat(ErrorDigest.of(new IllegalStateException("boom"))).contains("IllegalStateException");
    }
}
