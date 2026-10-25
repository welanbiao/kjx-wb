package com.koujunxin.server.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.koujunxin.runtime.api.port.AccountAiSettingsPort;

class InMemoryAccountAiSettingsStoreTest {

    @Test
    void saveIsPerAccountAndDoesNotPutKeyInToString() {
        InMemoryAccountAiSettingsStore store = new InMemoryAccountAiSettingsStore();
        store.save(9L, "https://gw.example/v1", "sk-never-log", "user-model");
        AccountAiSettingsPort.Snapshot snapshot = store.find(9L).orElseThrow();
        assertEquals("https://gw.example/v1", snapshot.baseUrl());
        assertEquals("sk-never-log", snapshot.apiKey());
        assertEquals("user-model", snapshot.model());
        assertTrue(store.find(8L).isEmpty());
        assertFalse(snapshot.toString().contains("sk-never-log"));
        assertFalse(new AppAiSettingsController.AiSettingsRequest(
                "https://gw.example/v1", "sk-never-log", "user-model").toString().contains("sk-never-log"));
    }
}
