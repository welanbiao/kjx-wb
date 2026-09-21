package com.koujunxin.account.application;

import java.util.List;
import java.util.Map;

public final class AccountResults {

    private AccountResults() {
    }

    public record SendCodeResult(int expireIn) {
    }

    public record RegisterResult(String token, long accountId) {
    }

    public record AccountBrief(long id, String nickname, String avatar, String phoneMask) {
    }

    public record LoginResult(String token, long expireIn, AccountBrief account, int bindStatus, Object member) {
    }

    public record ProfileResult(
            long id,
            String nickname,
            String avatar,
            String phoneMask,
            int status,
            int bindStatus,
            String cancelApplyAt,
            String lastLoginAt,
            String createTime
    ) {
    }

    public record DeviceView(
            long id,
            String deviceId,
            String deviceName,
            String platform,
            String lastRegion,
            String lastActiveAt,
            boolean current
    ) {
    }

    public record DeviceListResult(List<DeviceView> data) {
    }

    public record CancelResult(String cancelApplyAt, String effectiveAt) {
    }

    public record EmptyResult() {
    }
}
