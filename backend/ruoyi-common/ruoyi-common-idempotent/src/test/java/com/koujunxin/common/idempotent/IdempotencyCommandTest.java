package com.koujunxin.common.idempotent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;

class IdempotencyCommandTest {

    @Test
    void rejectsInvalidHash() {
        assertThatThrownBy(() -> new IdempotencyCommand(
                "unlock", 1L, "key-123456", null, "not-a-hash", IdempotencyCommand.DEFAULT_TTL))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void factoryUsesCanonicalHash() {
        IdempotencyCommand command = IdempotencyCommand.of("unlock", 8L, "idem-key-01", Map.of("workCode", "w"));
        assertThat(command.requestHash()).isEqualTo(CanonicalRequestHash.sha256Hex(Map.of("workCode", "w")));
        assertThat(command.ref().scene()).isEqualTo("unlock");
    }

    @Test
    void conflictExceptionMatchesContract() {
        IdempotencyConflictException ex = new IdempotencyConflictException(new IdempotencyRef("unlock", 1L, "k"));
        assertThat(ex.businessCode()).isEqualTo(1090);
        assertThat(ex.httpStatus()).isEqualTo(409);
        assertThat(ex.businessCodeName()).isEqualTo("IDEMPOTENCY_CONFLICT");
    }
}
