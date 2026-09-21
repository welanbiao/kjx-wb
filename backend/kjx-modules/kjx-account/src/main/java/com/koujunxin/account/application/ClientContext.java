package com.koujunxin.account.application;

public record ClientContext(
        String deviceId,
        String ip,
        String platform,
        String deviceName,
        String requestId,
        String idempotencyKey
) {
    public ClientContext {
        deviceId = deviceId == null || deviceId.isBlank() ? "unknown" : deviceId;
        if (deviceId.length() > 64) {
            deviceId = deviceId.substring(0, 64);
        }
    }

    public static ClientContext of(String deviceId, String ip, String requestId, String idempotencyKey) {
        return new ClientContext(blankToAnon(deviceId), ip, null, null, requestId, idempotencyKey);
    }

    private static String blankToAnon(String deviceId) {
        return deviceId == null || deviceId.isBlank() ? "unknown" : deviceId;
    }
}
