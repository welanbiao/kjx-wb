package com.koujunxin.channel.jdbc;

import org.springframework.jdbc.core.JdbcTemplate;

import com.koujunxin.channel.api.ChannelCredentialStore;
import com.koujunxin.channel.api.SecretRef;

/**
 * 只持久化 secretRef 与公网主机名，不保存明文 Key。
 */
public final class JdbcChannelCredentialStore implements ChannelCredentialStore {

    private final JdbcTemplate jdbc;

    public JdbcChannelCredentialStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public long save(long memberId, long deptId, String provider, SecretRef secretRef, String endpointHost) {
        if (memberId <= 0 || deptId <= 0) {
            throw new IllegalArgumentException("member and dept are required");
        }
        if (provider == null || !provider.matches("[A-Z0-9_]{1,32}")) {
            throw new IllegalArgumentException("provider rejected");
        }
        if (secretRef == null) {
            throw new IllegalArgumentException("secretRef rejected");
        }
        String host = requirePublicHost(endpointHost);
        jdbc.update(
                """
                INSERT INTO kjx_channel_credential
                  (member_id, dept_id, provider, secret_ref, endpoint_host, status)
                VALUES (?, ?, ?, ?, ?, 'ACTIVE')
                """,
                memberId, deptId, provider, secretRef.value(), host);
        Long id = jdbc.queryForObject(
                "SELECT id FROM kjx_channel_credential WHERE member_id = ? AND provider = ?",
                Long.class,
                memberId,
                provider);
        if (id == null) {
            throw new IllegalStateException("credential insert failed");
        }
        return id;
    }

    private static String requirePublicHost(String endpointHost) {
        if (endpointHost == null || endpointHost.isBlank() || endpointHost.length() > 255) {
            throw new IllegalArgumentException("endpoint rejected");
        }
        String host = endpointHost.trim().toLowerCase(java.util.Locale.ROOT);
        if (host.contains("/") || host.contains(":") || host.contains("@")) {
            throw new IllegalArgumentException("endpoint rejected");
        }
        return host;
    }
}
