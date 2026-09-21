package com.koujunxin.notification.outbox;

public class OutboxValidationException extends RuntimeException {

    public OutboxValidationException(String message) {
        super(message);
    }
}
