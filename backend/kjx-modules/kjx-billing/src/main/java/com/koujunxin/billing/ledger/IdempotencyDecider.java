package com.koujunxin.billing.ledger;

public final class IdempotencyDecider {

    public enum Kind {
        APPLY,
        REPLAY,
        CONFLICT
    }

    private IdempotencyDecider() {
    }

    public static Kind decide(String storedHash, String incomingHash) {
        if (storedHash == null) {
            return Kind.APPLY;
        }
        if (storedHash.equals(incomingHash)) {
            return Kind.REPLAY;
        }
        return Kind.CONFLICT;
    }
}
