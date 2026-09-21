package com.koujunxin.ai.support;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.koujunxin.ai.api.AiException;
import com.koujunxin.ai.api.port.MethodPromptStore;
import com.koujunxin.ai.api.port.PromptCatalog;
import com.koujunxin.ai.prompt.PromptKind;
import com.koujunxin.ai.prompt.PromptRecord;
import com.koujunxin.ai.prompt.PromptStatus;

public final class InMemoryPromptCatalog implements PromptCatalog, MethodPromptStore {

    private final Map<String, PromptRecord> records = new ConcurrentHashMap<>();

    @Override
    public PromptRecord saveWork(PromptRecord record) {
        if (record.kind() != PromptKind.WORK) {
            throw AiException.rejected("WORK catalog rejects METHOD prompt");
        }
        records.put(key(record.workCode(), record.kind(), record.version()), record);
        return record;
    }

    @Override
    public void saveMethod(PromptRecord record) {
        if (record.kind() != PromptKind.METHOD) {
            throw AiException.rejected("METHOD store rejects WORK prompt");
        }
        records.put(key(record.workCode(), record.kind(), record.version()), record);
    }

    @Override
    public void activateWork(String workCode, String version) {
        activate(workCode, PromptKind.WORK, version);
    }

    @Override
    public void activateMethod(String workCode, String version) {
        activate(workCode, PromptKind.METHOD, version);
    }

    @Override
    public Optional<PromptRecord> findActiveWork(String workCode) {
        return records.values().stream()
                .filter(record -> record.kind() == PromptKind.WORK)
                .filter(record -> record.workCode().equals(workCode))
                .filter(record -> record.status() == PromptStatus.ACTIVE)
                .findFirst();
    }

    @Override
    public Optional<PromptRecord> findWork(String workCode, String version) {
        return Optional.ofNullable(records.get(key(workCode, PromptKind.WORK, version)));
    }

    public Optional<PromptRecord> peekMethod(String workCode, String version) {
        return Optional.ofNullable(records.get(key(workCode, PromptKind.METHOD, version)));
    }

    private void activate(String workCode, PromptKind kind, String version) {
        String target = key(workCode, kind, version);
        PromptRecord record = records.get(target);
        if (record == null) {
            throw AiException.rejected("prompt version not found");
        }
        records.replaceAll((key, value) -> {
            if (value.workCode().equals(workCode) && value.kind() == kind && value.status() == PromptStatus.ACTIVE) {
                return value.withStatus(PromptStatus.ARCHIVED);
            }
            return value;
        });
        records.put(target, record.withStatus(PromptStatus.ACTIVE));
    }

    private static String key(String workCode, PromptKind kind, String version) {
        return workCode + "|" + kind + "|" + version;
    }
}
