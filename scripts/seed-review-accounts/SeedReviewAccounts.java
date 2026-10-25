import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;

import com.koujunxin.account.crypto.PasswordHasher;
import com.koujunxin.account.crypto.PhoneCipher;

/**
 * 只写本机审查账号。手机号密文用环境里的 PHONE_CRYPTO_KEY / PHONE_HMAC_SECRET，不把密钥写进仓库。
 * 重复执行会先清掉 remark = page-review 的账号和部门 910801 上的会员数据。
 */
public final class SeedReviewAccounts {

    static final String PASSWORD = "Review1pass";
    static final long DEPT = 910_801L;
    static final String REMARK = "page-review";

    private SeedReviewAccounts() {
    }

    public static void main(String[] args) throws Exception {
        requireLocal();
        PhoneCipher cipher = PhoneCipher.fromEnv();
        String passwordHash = new PasswordHasher().hash(PASSWORD);
        if (passwordHash.length() > 128) {
            throw new IllegalStateException("password hash is longer than kjx_account.password_hash");
        }
        try (Connection connection = DriverManager.getConnection(jdbcUrl(), env("DB_USER"), env("DB_PASSWORD"))) {
            connection.setAutoCommit(false);
            clear(connection);
            ensureHost(connection);
            long layoutId = ensureWork(connection, "review-desk", "审查样例：对话布局", "layout", "PUBLISHED");
            long gameId = ensureWork(connection, "review-game", "审查样例：文游", "game", "PUBLISHED");
            insertAccounts(connection, cipher, passwordHash, layoutId, gameId);
            connection.commit();
        }
        System.out.println("seeded review accounts");
    }

    private static void insertAccounts(Connection connection, PhoneCipher cipher, String passwordHash, long layoutId,
                                       long gameId) throws Exception {
        Account[] accounts = {
                new Account("13900001001", "未绑定", 1, 0, null, null, null, false, null),
                new Account("13900001002", "审核中", 1, 1, "PENDING_REVIEW", "NOT_STARTED", null, false, null),
                new Account("13900001003", "有效会员", 1, 2, "MATCHED", "VALID", "2027-08-09T23:59:59", true, "user"),
                new Account("13900001004", "已过期", 1, 2, "MATCHED", "EXPIRED", "2026-01-01T23:59:59", true, "official_add"),
                new Account("13900001005", "尚未生效", 1, 2, "MATCHED", "NOT_STARTED", "2028-01-01T23:59:59", false, null),
                new Account("13900001006", "已驳回", 1, 3, "REJECTED", "NOT_STARTED", null, false, null),
                new Account("13900001007", "解绑中", 1, 4, "UNBINDING", "VALID", "2027-08-09T23:59:59", false, null),
                new Account("13900001008", "注销中", 3, 0, null, null, null, false, null)
        };
        int index = 0;
        for (Account account : accounts) {
            index++;
            long accountId = insertAccount(connection, cipher, passwordHash, account);
            if (account.bindingStatus == null) {
                continue;
            }
            String code = "REVW-2345678" + Integer.toHexString(index).toUpperCase();
            boolean bound = "MATCHED".equals(account.bindingStatus) || "UNBINDING".equals(account.bindingStatus);
            long memberId = insertMember(connection, account, code, accountId, bound);
            insertBinding(connection, account, accountId, memberId, code);
            if (account.entitle) {
                insertEntitlement(connection, accountId, memberId, index == 3 ? layoutId : gameId, account.source);
            }
        }
    }

    private static long insertAccount(Connection connection, PhoneCipher cipher, String passwordHash, Account account)
            throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO kjx_account
                  (phone, phone_hash, password_hash, nickname, status, bind_status, cancel_apply_at, remark, creator)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'page-review')
                """,
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, cipher.encrypt(account.phone));
            statement.setString(2, cipher.hmacHex(account.phone));
            statement.setString(3, passwordHash);
            statement.setString(4, account.nickname);
            statement.setInt(5, account.status);
            statement.setInt(6, account.bindStatus);
            if (account.status == 3) {
                statement.setTimestamp(7, Timestamp.valueOf(LocalDateTime.of(2026, 9, 20, 10, 0)));
            } else {
                statement.setNull(7, Types.TIMESTAMP);
            }
            statement.setString(8, REMARK);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new IllegalStateException("account id missing");
                }
                return keys.getLong(1);
            }
        }
    }

    private static long insertMember(Connection connection, Account account, String code, long accountId, boolean bound)
            throws Exception {
        LocalDateTime expire = account.expireAt == null
                ? LocalDateTime.of(2027, 8, 9, 23, 59, 59)
                : LocalDateTime.parse(account.expireAt);
        LocalDateTime start = "NOT_STARTED".equals(account.memberStatus)
                ? LocalDateTime.of(2027, 1, 1, 0, 0)
                : LocalDateTime.of(2026, 6, 1, 0, 0);
        int available = "EXPIRED".equals(account.memberStatus) ? 12 : "NOT_STARTED".equals(account.memberStatus) ? 4 : 14;
        int unlocked = account.entitle ? 1 : 0;
        try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO kjx_member
                  (member_code, douyin_id, douyin_id_raw, dept_id, subscribed_months, subscribed_days,
                   expire_at, start_at, available_count, unlocked_count, remain_count, status,
                   bind_state, bound_account_id, bound_at, remark, creator)
                VALUES (?, ?, ?, ?, 15, 435, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'page-review')
                """,
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, code);
            statement.setString(2, "dy" + account.phone.substring(7));
            statement.setString(3, "dy" + account.phone.substring(7));
            statement.setLong(4, DEPT);
            statement.setTimestamp(5, Timestamp.valueOf(expire));
            statement.setTimestamp(6, Timestamp.valueOf(start));
            statement.setInt(7, available);
            statement.setInt(8, unlocked);
            statement.setInt(9, available - unlocked);
            statement.setString(10, account.memberStatus);
            statement.setInt(11, bound ? 1 : 0);
            if (bound) {
                statement.setLong(12, accountId);
                statement.setTimestamp(13, Timestamp.valueOf(LocalDateTime.of(2026, 9, 1, 12, 0)));
            } else {
                statement.setNull(12, Types.BIGINT);
                statement.setNull(13, Types.TIMESTAMP);
            }
            statement.setString(14, REMARK);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new IllegalStateException("member id missing");
                }
                return keys.getLong(1);
            }
        }
    }

    private static void insertBinding(Connection connection, Account account, long accountId, long memberId, String code)
            throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO kjx_member_binding
                  (dept_id, account_id, member_id, douyin_id_input, douyin_id_raw, member_code_input,
                   claim_months, status, matched_at, reject_reason)
                VALUES (?, ?, ?, ?, ?, ?, 15, ?, ?, ?)
                """)) {
            statement.setLong(1, DEPT);
            statement.setLong(2, accountId);
            statement.setLong(3, memberId);
            String douyin = "dy" + account.phone.substring(7);
            statement.setString(4, douyin);
            statement.setString(5, douyin);
            statement.setString(6, code);
            statement.setString(7, account.bindingStatus);
            if ("MATCHED".equals(account.bindingStatus)) {
                statement.setTimestamp(8, Timestamp.valueOf(LocalDateTime.of(2026, 9, 1, 12, 0)));
            } else {
                statement.setNull(8, Types.TIMESTAMP);
            }
            if ("REJECTED".equals(account.bindingStatus)) {
                statement.setString(9, "三要素与主播底稿不一致");
            } else {
                statement.setNull(9, Types.VARCHAR);
            }
            statement.executeUpdate();
        }
    }

    private static void insertEntitlement(Connection connection, long accountId, long memberId, long workId, String source)
            throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO kjx_member_entitlement
                  (dept_id, member_id, account_id, work_id, work_version, source, unlocked_at)
                VALUES (?, ?, ?, ?, '1', ?, ?)
                """)) {
            statement.setLong(1, DEPT);
            statement.setLong(2, memberId);
            statement.setLong(3, accountId);
            statement.setLong(4, workId);
            statement.setString(5, source);
            statement.setTimestamp(6, Timestamp.valueOf(LocalDateTime.of(2026, 9, 10, 15, 0)));
            statement.executeUpdate();
        }
    }

    private static void ensureHost(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO kjx_host (dept_id, host_code, dy_account, remark, creator)
                VALUES (?, 'REVW', 'review-host', ?, 'page-review')
                ON DUPLICATE KEY UPDATE host_code = 'REVW', deleted = b'0', remark = VALUES(remark)
                """)) {
            statement.setLong(1, DEPT);
            statement.setString(2, REMARK);
            statement.executeUpdate();
        }
    }

    private static long ensureWork(Connection connection, String code, String name, String type, String status)
            throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO kjx_work
                  (work_code, name, work_type, summary, status, current_version, content_version,
                   access_form, allowed_channels, publish_time, creator, updater)
                VALUES (?, ?, ?, '页面审查用样例，不是正式作品内容。', ?, '1', '1', 'A', 'PLATFORM', ?, 'page-review', 'page-review')
                ON DUPLICATE KEY UPDATE
                  name = VALUES(name), status = VALUES(status), deleted = b'0', publish_time = VALUES(publish_time)
                """)) {
            statement.setString(1, code);
            statement.setString(2, name);
            statement.setString(3, type);
            statement.setString(4, status);
            statement.setTimestamp(5, Timestamp.valueOf(LocalDateTime.of(2026, 9, 1, 9, 0)));
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id FROM kjx_work WHERE work_code = ?")) {
            statement.setString(1, code);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new IllegalStateException("work missing: " + code);
                }
                return rows.getLong(1);
            }
        }
    }

    private static void clear(Connection connection) throws Exception {
        execute(connection, "DELETE FROM kjx_member_entitlement WHERE dept_id = " + DEPT);
        execute(connection, "DELETE FROM kjx_member_binding WHERE dept_id = " + DEPT);
        execute(connection, "DELETE FROM kjx_member WHERE dept_id = " + DEPT + " OR remark = '" + REMARK + "'");
        execute(connection, "DELETE FROM kjx_account WHERE remark = '" + REMARK + "'");
    }

    private static void execute(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private static void requireLocal() {
        String host = env("DB_HOST");
        if (!"127.0.0.1".equals(host) && !"localhost".equals(host)) {
            throw new IllegalStateException("refusing to seed a non-local database");
        }
    }

    private static String jdbcUrl() {
        return "jdbc:mysql://" + env("DB_HOST") + ":" + env("DB_PORT") + "/" + env("DB_NAME")
                + "?useUnicode=true&characterEncoding=UTF-8&connectionCollation=utf8mb4_0900_ai_ci"
                + "&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true";
    }

    private static String env(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required");
        }
        return value.trim();
    }

    private record Account(
            String phone,
            String nickname,
            int status,
            int bindStatus,
            String bindingStatus,
            String memberStatus,
            String expireAt,
            boolean entitle,
            String source) {
    }
}
