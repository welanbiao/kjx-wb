package com.koujunxin.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.koujunxin.notification.outbox.OutboxPublisher;
import com.koujunxin.notification.outbox.OutboxRepository;
import com.koujunxin.notification.outbox.OutboxWorker;
import com.koujunxin.notification.outbox.OutboxWriter;
import com.koujunxin.notification.outbox.consume.ConsumerIdempotencyPort;

class NotificationModuleTest {

    @Test
    void moduleNameIsStable() {
        assertEquals("kjx-notification", NotificationModule.NAME);
        assertNotNull(NotificationModule.class.getPackage());
    }

    @Test
    void outboxContractsAreInterfaces() {
        assertTrue(OutboxWriter.class.isInterface());
        assertTrue(OutboxRepository.class.isInterface());
        assertTrue(OutboxPublisher.class.isInterface());
        assertTrue(OutboxWorker.class.isInterface());
        assertTrue(ConsumerIdempotencyPort.class.isInterface());
    }
}
