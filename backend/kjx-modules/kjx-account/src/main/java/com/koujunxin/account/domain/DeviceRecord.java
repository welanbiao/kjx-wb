package com.koujunxin.account.domain;

import java.time.LocalDateTime;

public record DeviceRecord(
        long id,
        long accountId,
        String deviceId,
        String deviceName,
        String platform,
        String lastRegion,
        LocalDateTime lastActiveAt,
        String tokenHash
) {
}
