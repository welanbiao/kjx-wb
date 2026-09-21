package com.koujunxin.channel.api;

public interface ChannelCredentialStore {

    long save(long memberId, long deptId, String provider, SecretRef secretRef, String endpointHost);
}
