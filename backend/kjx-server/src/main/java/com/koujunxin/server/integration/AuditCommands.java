package com.koujunxin.server.integration;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.dromara.common.core.utils.ServletUtils;
import org.dromara.common.satoken.utils.LoginHelper;

import com.koujunxin.log.api.port.AuditLogWritePort.AuditLogCommand;
import com.koujunxin.log.api.port.RequestContextPort;

final class AuditCommands {

    private AuditCommands() {
    }

    static AuditLogCommand success(
            RequestContextPort contexts,
            long actorId,
            String actorName,
            String action,
            String targetType,
            String targetId,
            Map<String, Object> before,
            Map<String, Object> after,
            String reason) {
        RequestContextPort.RequestContextSnapshot context = contexts.current();
        return new AuditLogCommand(
                null,
                Instant.now(),
                valueOrUuid(context == null ? null : context.traceId()),
                valueOrUuid(context == null ? null : context.requestId()),
                actorId,
                valueOrDefault(actorName, String.valueOf(actorId)),
                currentRoles(),
                clientIp(),
                null,
                action,
                targetType,
                targetId,
                before,
                after,
                "SUCCESS",
                reason);
    }

    static long currentActorId(String fallback) {
        Long actorId = LoginHelper.getUserId();
        if (actorId != null) {
            return actorId;
        }
        try {
            return Long.parseLong(fallback);
        } catch (RuntimeException ignored) {
            return 0L;
        }
    }

    static String currentActorName(String fallback) {
        String username = LoginHelper.getUsername();
        return valueOrDefault(username, valueOrDefault(fallback, "system"));
    }

    private static List<String> currentRoles() {
        try {
            var user = LoginHelper.getLoginUser();
            if (user == null || user.getRolePermission() == null) {
                return List.of();
            }
            return new ArrayList<>(user.getRolePermission());
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    private static String clientIp() {
        try {
            return ServletUtils.getClientIP();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String valueOrUuid(String value) {
        return valueOrDefault(value, UUID.randomUUID().toString());
    }

    private static String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
