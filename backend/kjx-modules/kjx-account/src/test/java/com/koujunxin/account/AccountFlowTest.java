package com.koujunxin.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.koujunxin.account.api.AccountException;
import com.koujunxin.account.application.AccountResults;
import com.koujunxin.account.application.ClientContext;
import com.koujunxin.account.crypto.PasswordHasher;
import com.koujunxin.account.support.AccountMysqlSupport;

class AccountFlowTest extends AccountMysqlSupport {

    private static final AtomicInteger PHONES = new AtomicInteger(1000);

    private String nextPhone() {
        return "1390000" + String.format("%04d", PHONES.incrementAndGet());
    }

    @Test
    void registerLoginDevicesCancelAndRevoke() {
        String phone = nextPhone();
        String password = "Abcd1234";
        AccountResults.SendCodeResult sent = service().sendCode(
                phone, "register", "ok-1", Map.of("token", "x"), ctx("dev-a", id()));
        assertEquals(300, sent.expireIn());
        String code = sms().last("register", phone);
        assertNotNull(code);
        assertEquals(6, code.length());

        AccountResults.RegisterResult registered = service().register(
                phone, password, code, true, ctx("dev-a", id()));
        assertTrue(registered.accountId() > 0);
        assertNotNull(registered.token());
        sessions().bindCurrent(registered.token());

        assertNoPlainSecrets(phone, password, registered.token(), code);

        AccountResults.LoginResult passwordLogin = service().loginByPassword(phone, password, ctx("dev-b", id()));
        assertEquals(registered.accountId(), passwordLogin.account().id());
        assertEquals("139****" + phone.substring(7), passwordLogin.account().phoneMask());
        assertNull(passwordLogin.member());
        sessions().bindCurrent(passwordLogin.token());

        service().sendCode(phone, "login", "ok-2", Map.of("token", "x"), ctx("dev-b", id()));
        AccountResults.LoginResult codeLogin = service().loginByCode(
                phone, sms().last("login", phone), ctx("dev-b", id()));
        assertEquals(registered.accountId(), codeLogin.account().id());
        sessions().bindCurrent(codeLogin.token());

        List<AccountResults.DeviceView> devices = service().listDevices();
        assertFalse(devices.isEmpty());
        assertTrue(devices.stream().anyMatch(AccountResults.DeviceView::current));
        devices.forEach(d -> assertFalse(d.deviceId() == null || d.deviceId().isBlank()));
        devices.stream().filter(d -> !d.current()).findFirst()
                .ifPresent(d -> service().deleteDevice(d.id(), ctx("dev-b", id())));

        service().sendCode(phone, "cancel", "ok-3", Map.of("token", "x"), ctx("dev-b", id()));
        AccountResults.CancelResult cancel = service().cancel(sms().last("cancel", phone), "test", ctx("dev-b", id()));
        assertNotNull(cancel.cancelApplyAt());
        assertNotNull(cancel.effectiveAt());
        AccountResults.ProfileResult profile = service().profile();
        assertEquals(3, profile.status());

        service().revokeCancel(ctx("dev-b", id()));
        assertEquals(1, service().profile().status());

        Integer outbox = jdbc().queryForObject("SELECT COUNT(*) FROM kjx_outbox_event WHERE aggregate_id = ?",
                Integer.class, String.valueOf(registered.accountId()));
        assertTrue(outbox >= 3);
        Integer leaked = jdbc().queryForObject(
                "SELECT COUNT(*) FROM kjx_outbox_event WHERE payload LIKE ?",
                Integer.class, "%" + phone + "%");
        assertEquals(0, leaked);
    }

    @Test
    void concurrentSamePhoneInsertOnlyOneSucceeds() throws Exception {
        String phone = nextPhone();
        String phoneHash = phones().hmacHex(phone);
        String cipher = phones().encrypt(phone);
        String passwordHash = new PasswordHasher().hash("Abcd1234");
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger conflict = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    accounts().insert(cipher, phoneHash, passwordHash, "n", null, "dev", LocalDateTime.now());
                    ok.incrementAndGet();
                } catch (AccountException ex) {
                    assertEquals(409, ex.bizCode());
                    conflict.incrementAndGet();
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> future : futures) {
            future.get(20, TimeUnit.SECONDS);
        }
        pool.shutdownNow();
        assertEquals(1, ok.get());
        assertEquals(7, conflict.get());
        Integer rows = jdbc().queryForObject("SELECT COUNT(*) FROM kjx_account WHERE phone_hash = ?", Integer.class, phoneHash);
        assertEquals(1, rows);
        String storedPhone = jdbc().queryForObject("SELECT phone FROM kjx_account WHERE phone_hash = ?", String.class, phoneHash);
        assertNotEquals(phone, storedPhone);
    }

    @Test
    void registerReplayReturnsAccountWithoutPersistingEitherIssuedToken() {
        String phone = nextPhone();
        service().sendCode(phone, "register", "ok-replay", Map.of("a", 1), ctx("dev-replay", id()));
        String code = sms().last("register", phone);
        String idempotencyKey = id();
        ClientContext context = ctx("dev-replay", idempotencyKey);

        AccountResults.RegisterResult first =
                service().register(phone, "Abcd1234", code, true, context);
        AccountResults.RegisterResult replay =
                service().register(phone, "Abcd1234", code, true, context);

        assertEquals(first.accountId(), replay.accountId());
        assertNotEquals(first.token(), replay.token());
        Integer accountRows = jdbc().queryForObject(
                "SELECT COUNT(*) FROM kjx_account WHERE phone_hash = ?",
                Integer.class, phones().hmacHex(phone));
        assertEquals(1, accountRows);
        Integer tokenLeaks = jdbc().queryForObject(
                """
                SELECT COUNT(*) FROM kjx_idempotency_guard
                 WHERE CAST(result_body AS CHAR) LIKE ?
                    OR CAST(result_body AS CHAR) LIKE ?
                """,
                Integer.class, "%" + first.token() + "%", "%" + replay.token() + "%");
        assertEquals(0, tokenLeaks);
    }

    @Test
    void fivePasswordFailuresLockEvenWithCorrectPassword() {
        String phone = nextPhone();
        seedRegistered(phone, "Abcd1234");
        for (int i = 0; i < 4; i++) {
            AccountException ex = assertThrows(AccountException.class,
                    () -> service().loginByPassword(phone, "Wrong1234", ctx("dev-x", id())));
            assertEquals(400, ex.bizCode());
        }
        AccountException locked = assertThrows(AccountException.class,
                () -> service().loginByPassword(phone, "Wrong1234", ctx("dev-x", id())));
        assertEquals(1013, locked.bizCode());
        AccountException still = assertThrows(AccountException.class,
                () -> service().loginByPassword(phone, "Abcd1234", ctx("dev-x", id())));
        assertEquals(1013, still.bizCode());
    }

    @Test
    void sendCodeAuditHasMaskNotPlaintext() {
        String phone = nextPhone();
        service().sendCode(phone, "register", "ok-r", Map.of("a", 1), ctx("dev-r", id()));
        String hash = phones().hmacHex(phone);
        Integer rows = jdbc().queryForObject(
                "SELECT COUNT(*) FROM kjx_verify_code WHERE target_hash = ? AND target_mask = ?",
                Integer.class, hash, "139****" + phone.substring(7));
        assertEquals(1, rows);
        Integer leaked = jdbc().queryForObject(
                "SELECT COUNT(*) FROM kjx_verify_code WHERE code_hash = ?",
                Integer.class, sms().last("register", phone));
        assertEquals(0, leaked);
    }

    @Test
    void consumedRegisterCodeCannotReuse() {
        String phone = nextPhone();
        service().sendCode(phone, "register", "ok-c", Map.of("a", 1), ctx("dev-c", id()));
        String code = sms().last("register", phone);
        service().register(phone, "Abcd1234", code, true, ctx("dev-c", id()));
        AccountException reuse = assertThrows(AccountException.class,
                () -> service().register(phone, "Abcd1234", code, true, ctx("dev-c", id())));
        assertEquals(1011, reuse.bizCode());
    }

    private void seedRegistered(String phone, String password) {
        service().sendCode(phone, "register", "ok-s", Map.of("a", 1), ctx("dev-s", id()));
        service().register(phone, password, sms().last("register", phone), true, ctx("dev-s", id()));
    }

    private void assertNoPlainSecrets(String phone, String password, String token, String code) {
        Integer phoneHits = jdbc().queryForObject(
                "SELECT COUNT(*) FROM kjx_account WHERE phone = ? OR password_hash = ?",
                Integer.class, phone, password);
        assertEquals(0, phoneHits);
        Integer tokenHits = jdbc().queryForObject(
                "SELECT COUNT(*) FROM kjx_account_device WHERE token_hash = ?",
                Integer.class, token);
        assertEquals(0, tokenHits);
        Integer idempotencyTokenHits = jdbc().queryForObject(
                "SELECT COUNT(*) FROM kjx_idempotency_guard WHERE CAST(result_body AS CHAR) LIKE ?",
                Integer.class, "%" + token + "%");
        assertEquals(0, idempotencyTokenHits);
        Integer codeHits = jdbc().queryForObject(
                "SELECT COUNT(*) FROM kjx_verify_code WHERE code_hash = ?",
                Integer.class, code);
        assertEquals(0, codeHits);
    }

    private static String id() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 32);
    }
}
