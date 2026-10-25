package com.koujunxin.account.application;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.koujunxin.account.api.AccountFailures;
import com.koujunxin.account.api.port.AppSessionPort;
import com.koujunxin.account.api.port.BoundMemberQueryPort;
import com.koujunxin.account.api.port.CaptchaVerificationPort;
import com.koujunxin.account.api.port.SmsDispatchPort;
import com.koujunxin.account.config.AccountProperties;
import com.koujunxin.account.crypto.PasswordHasher;
import com.koujunxin.account.crypto.PhoneCipher;
import com.koujunxin.account.domain.AccountRecord;
import com.koujunxin.account.domain.DeviceRecord;
import com.koujunxin.account.domain.SmsScene;
import com.koujunxin.account.jdbc.AccountRepository;
import com.koujunxin.account.jdbc.DeviceRepository;
import com.koujunxin.account.jdbc.VerifyCodeAuditRepository;
import com.koujunxin.account.notify.SecurityOutbox;
import com.koujunxin.account.security.LoginLockStore;
import com.koujunxin.account.sms.SmsCodeStore;
import com.koujunxin.account.sms.SmsRateLimiter;
import com.koujunxin.account.web.AccountTimes;
import com.koujunxin.common.idempotent.IdempotencyCommand;
import com.koujunxin.common.idempotent.IdempotencyConflictException;
import com.koujunxin.common.idempotent.IdempotencyResult;
import com.koujunxin.common.idempotent.IdempotencyService;

public class AccountApplicationService {

    private static final Logger log = LoggerFactory.getLogger(AccountApplicationService.class);
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AccountProperties properties;
    private final Clock clock;
    private final PhoneCipher phoneCipher;
    private final PasswordHasher passwordHasher;
    private final CaptchaVerificationPort captcha;
    private final SmsCodeStore smsCodes;
    private final SmsRateLimiter smsRateLimiter;
    private final SmsDispatchPort smsDispatch;
    private final LoginLockStore loginLocks;
    private final AccountRepository accounts;
    private final DeviceRepository devices;
    private final VerifyCodeAuditRepository verifyAudit;
    private final AppSessionPort sessions;
    private final BoundMemberQueryPort members;
    private final SecurityOutbox outbox;
    private final IdempotencyService idempotency;
    private final TransactionTemplate tx;
    private final ObjectMapper objectMapper;

    public AccountApplicationService(
            AccountProperties properties,
            Clock clock,
            PhoneCipher phoneCipher,
            PasswordHasher passwordHasher,
            CaptchaVerificationPort captcha,
            SmsCodeStore smsCodes,
            SmsRateLimiter smsRateLimiter,
            SmsDispatchPort smsDispatch,
            LoginLockStore loginLocks,
            AccountRepository accounts,
            DeviceRepository devices,
            VerifyCodeAuditRepository verifyAudit,
            AppSessionPort sessions,
            BoundMemberQueryPort members,
            SecurityOutbox outbox,
            IdempotencyService idempotency,
            TransactionTemplate tx,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.clock = clock;
        this.phoneCipher = phoneCipher;
        this.passwordHasher = passwordHasher;
        this.captcha = captcha;
        this.smsCodes = smsCodes;
        this.smsRateLimiter = smsRateLimiter;
        this.smsDispatch = smsDispatch;
        this.loginLocks = loginLocks;
        this.accounts = accounts;
        this.devices = devices;
        this.verifyAudit = verifyAudit;
        this.sessions = sessions;
        this.members = members;
        this.outbox = outbox;
        this.idempotency = idempotency;
        this.tx = tx;
        this.objectMapper = objectMapper;
    }

    public AccountResults.SendCodeResult sendCode(String phone, String sceneWire, String captchaId,
                                                  Map<String, Object> captchaData, ClientContext ctx) {
        requirePhone(phone);
        SmsScene scene = SmsScene.fromWire(sceneWire);
        if (captchaId == null || captchaId.isBlank() || captchaData == null) {
            throw AccountFailures.captchaInvalid();
        }
        String phoneHash = phoneCipher.hmacHex(phone);
        String ipHash = ctx.ip() == null ? "none" : phoneCipher.hmacUtf8(ctx.ip());
        requireIdempotencyKey(ctx, "send_code");
        return readJson(inTx(() -> idempotent("send_code", 0L, ctx, Map.of("scene", scene.wire(), "phoneHash", phoneHash), () -> {
            if (!captcha.verify(captchaId, captchaData)) {
                throw AccountFailures.captchaInvalid();
            }
            if (!smsRateLimiter.tryAcquire(phoneHash, ipHash, properties.smsInterval(),
                    properties.smsDailyLimit(), properties.smsIpHourlyLimit())) {
                throw AccountFailures.rateLimited();
            }
            String code = generateCode();
            String codeHash = codeHash(scene, phoneHash, code);
            smsCodes.put(scene, phoneHash, codeHash, properties.smsTtl());
            verifyAudit.insert(phoneHash, PhoneCipher.mask(phone), scene.wire(), codeHash,
                    AccountRepository.ipBytes(ctx.ip()), now().plus(properties.smsTtl()));
            smsDispatch.dispatch(scene.wire(), phone, code);
            log.info("sms code accepted scene={} requestId={}", scene.wire(), ctx.requestId());
            return toJson(new AccountResults.SendCodeResult((int) properties.smsTtl().toSeconds()));
        })), AccountResults.SendCodeResult.class);
    }

    public AccountResults.RegisterResult register(String phone, String password, String code,
                                                  Boolean agreeTerms, ClientContext ctx) {
        requirePhone(phone);
        if (!Boolean.TRUE.equals(agreeTerms)) {
            throw AccountFailures.badRequest("必须同意用户协议");
        }
        try {
            passwordHasher.validatePolicy(password);
        } catch (IllegalArgumentException ex) {
            throw AccountFailures.badRequest(ex.getMessage());
        }
        requireIdempotencyKey(ctx, "register");
        String phoneHash = phoneCipher.hmacHex(phone);
        String passwordHash = passwordHasher.hash(password);
        String cipher = phoneCipher.encrypt(phone);
        String mask = PhoneCipher.mask(phone);
        RegisteredAccount registered = readJson(inTx(() -> idempotent(
                "register", 0L, ctx, Map.of("phoneHash", phoneHash), () -> {
            consumeCode(SmsScene.REGISTER, phone, code);
            long accountId = accounts.insert(cipher, phoneHash, passwordHash, "用户" + phone.substring(7),
                    AccountRepository.ipBytes(ctx.ip()), ctx.deviceId(), now());
            outbox.accountRegistered(accountId, mask, ctx.requestId(), ctx.idempotencyKey());
            log.info("account registered id={} requestId={}", accountId, ctx.requestId());
            return toJson(new RegisteredAccount(accountId));
        })), RegisteredAccount.class);

        sessions.revokeDevice(registered.accountId(), ctx.deviceId());
        AppSessionPort.IssuedSession session = sessions.issue(registered.accountId(), ctx.deviceId());
        try {
            inTx(() -> {
                devices.upsert(registered.accountId(), ctx.deviceId(), ctx.deviceName(), ctx.platform(),
                        AccountRepository.ipBytes(ctx.ip()), null, session.tokenHash(), now());
                return "{}";
            });
        } catch (RuntimeException ex) {
            sessions.revokeDevice(registered.accountId(), ctx.deviceId());
            throw ex;
        }
        return new AccountResults.RegisterResult(session.token(), registered.accountId());
    }

    public AccountResults.LoginResult loginByPassword(String phone, String password, ClientContext ctx) {
        return loginByPassword(phone, password, null, null, ctx);
    }

    public AccountResults.LoginResult loginByPassword(
            String phone, String password, String captchaId, Map<String, Object> captchaData, ClientContext ctx) {
        if (captchaId != null && !captchaId.isBlank()) {
            if (captchaData == null || !captcha.verify(captchaId, captchaData)) {
                throw AccountFailures.captchaInvalid();
            }
        }
        requirePhone(phone);
        AccountRecord account = loadForLogin(phone);
        if (!passwordHasher.matches(password, account.passwordHash())) {
            onLoginFailure(account);
            throw AccountFailures.badRequest("手机号或密码错误");
        }
        return completeLogin(account, phone, ctx);
    }

    public AccountResults.LoginResult loginByCode(String phone, String code, ClientContext ctx) {
        requirePhone(phone);
        AccountRecord account = loadForLogin(phone);
        consumeCode(SmsScene.LOGIN, phone, code);
        return completeLogin(account, phone, ctx);
    }

    public AccountResults.EmptyResult logout(ClientContext ctx) {
        long accountId = requireLogin();
        sessions.revokeCurrent();
        devices.clearTokenHash(accountId, ctx.deviceId());
        return new AccountResults.EmptyResult();
    }

    public AccountResults.LoginResult refresh(ClientContext ctx) {
        AccountRecord account = requireAccount();
        AppSessionPort.IssuedSession session = sessions.refresh(account.id(), ctx.deviceId());
        devices.upsert(account.id(), ctx.deviceId(), ctx.deviceName(), ctx.platform(),
                AccountRepository.ipBytes(ctx.ip()), null, session.tokenHash(), now());
        return toLoginResult(account, session, phoneCipher.decrypt(account.phoneCipher()));
    }

    public AccountResults.EmptyResult resetPassword(String phone, String code, String newPassword, ClientContext ctx) {
        requirePhone(phone);
        try {
            passwordHasher.validatePolicy(newPassword);
        } catch (IllegalArgumentException ex) {
            throw AccountFailures.badRequest(ex.getMessage());
        }
        requireIdempotencyKey(ctx, "reset_pwd");
        AccountRecord account = accounts.findByPhoneHash(phoneCipher.hmacHex(phone))
                .orElseThrow(AccountFailures::verifyCodeInvalid);
        assertLoginAllowed(account);
        String hash = passwordHasher.hash(newPassword);
        String mask = PhoneCipher.mask(phone);
        readJson(inTx(() -> idempotent("reset_pwd", account.id(), ctx, Map.of("accountId", account.id()), () -> {
            consumeCode(SmsScene.RESET_PWD, phone, code);
            accounts.updatePassword(account.id(), hash);
            sessions.revokeAll(account.id());
            outbox.passwordChanged(account.id(), mask, ctx.requestId(), ctx.idempotencyKey());
            log.info("password reset accountId={} requestId={}", account.id(), ctx.requestId());
            return "{}";
        })), Map.class);
        return new AccountResults.EmptyResult();
    }

    public AccountResults.ProfileResult profile() {
        AccountRecord account = requireAccount();
        return toProfile(account);
    }

    public AccountResults.ProfileResult updateProfile(String nickname, String avatar) {
        AccountRecord account = requireAccount();
        if (nickname != null && (nickname.isBlank() || nickname.length() > 64)) {
            throw AccountFailures.badRequest("昵称不合法");
        }
        if (avatar != null && avatar.length() > 512) {
            throw AccountFailures.badRequest("头像不合法");
        }
        accounts.updateProfile(account.id(), nickname, avatar);
        return toProfile(accounts.findById(account.id()).orElse(account));
    }

    public AccountResults.EmptyResult changePassword(String oldPassword, String newPassword, ClientContext ctx) {
        AccountRecord account = requireAccount();
        requireIdempotencyKey(ctx, "change_pwd");
        if (!passwordHasher.matches(oldPassword, account.passwordHash())) {
            throw AccountFailures.badRequest("原密码错误");
        }
        try {
            passwordHasher.validatePolicy(newPassword);
        } catch (IllegalArgumentException ex) {
            throw AccountFailures.badRequest(ex.getMessage());
        }
        String hash = passwordHasher.hash(newPassword);
        String mask = PhoneCipher.mask(phoneCipher.decrypt(account.phoneCipher()));
        readJson(inTx(() -> idempotent("change_pwd", account.id(), ctx, Map.of("accountId", account.id()), () -> {
            accounts.updatePassword(account.id(), hash);
            outbox.passwordChanged(account.id(), mask, ctx.requestId(), ctx.idempotencyKey());
            log.info("password changed accountId={} requestId={}", account.id(), ctx.requestId());
            return "{}";
        })), Map.class);
        return new AccountResults.EmptyResult();
    }

    public List<AccountResults.DeviceView> listDevices() {
        AccountRecord account = requireAccount();
        String currentHash = sessions.currentTokenHash().orElse("");
        List<AccountResults.DeviceView> views = new ArrayList<>();
        for (DeviceRecord device : devices.listByAccount(account.id())) {
            views.add(new AccountResults.DeviceView(
                    device.id(),
                    device.deviceId(),
                    device.deviceName(),
                    device.platform(),
                    device.lastRegion(),
                    AccountTimes.format(device.lastActiveAt()),
                    currentHash.equals(device.tokenHash())));
        }
        return views;
    }

    public AccountResults.EmptyResult deleteDevice(long deviceRowId, ClientContext ctx) {
        AccountRecord account = requireAccount();
        requireIdempotencyKey(ctx, "device_kick");
        DeviceRecord device = devices.findById(account.id(), deviceRowId)
                .orElseThrow(() -> AccountFailures.notFound("设备不存在"));
        readJson(inTx(() -> idempotent("device_kick", account.id(), ctx, Map.of("deviceId", device.deviceId()), () -> {
            devices.delete(account.id(), deviceRowId);
            sessions.revokeDevice(account.id(), device.deviceId());
            log.info("device kicked accountId={} rowId={}", account.id(), deviceRowId);
            return "{}";
        })), Map.class);
        return new AccountResults.EmptyResult();
    }

    public AccountResults.CancelResult cancel(String code, String reason, ClientContext ctx) {
        AccountRecord account = requireAccount();
        requireIdempotencyKey(ctx, "cancel");
        if (account.cancelling()) {
            throw AccountFailures.accountCancelling();
        }
        if (account.locked() || account.cancelled()) {
            throw AccountFailures.accountLocked();
        }
        LocalDateTime applyAt = now();
        LocalDateTime effective = applyAt.plusDays(properties.cancelCoolingDays());
        String mask = PhoneCipher.mask(phoneCipher.decrypt(account.phoneCipher()));
        String phone = phoneCipher.decrypt(account.phoneCipher());
        return readJson(inTx(() -> idempotent("cancel", account.id(), ctx, Map.of("reason", Objects.toString(reason, "")), () -> {
            consumeCode(SmsScene.CANCEL, phone, code);
            accounts.applyCancel(account.id(), applyAt);
            outbox.cancelScheduled(account.id(), mask, ctx.requestId(), ctx.idempotencyKey());
            log.info("cancel scheduled accountId={} requestId={}", account.id(), ctx.requestId());
            return toJson(new AccountResults.CancelResult(AccountTimes.format(applyAt), AccountTimes.format(effective)));
        })), AccountResults.CancelResult.class);
    }

    public AccountResults.EmptyResult revokeCancel(ClientContext ctx) {
        AccountRecord account = requireAccount();
        if (!account.cancelling()) {
            throw AccountFailures.badRequest("当前不在注销冷静期");
        }
        LocalDateTime deadline = account.cancelApplyAt().plusDays(properties.cancelCoolingDays());
        if (now().isAfter(deadline)) {
            throw AccountFailures.accountCancelling();
        }
        accounts.revokeCancel(account.id());
        log.info("cancel revoked accountId={}", account.id());
        return new AccountResults.EmptyResult();
    }

    public long requireLogin() {
        return sessions.currentAccountId().orElseThrow(AccountFailures::unauthorized);
    }

    private AccountResults.LoginResult completeLogin(AccountRecord account, String phone, ClientContext ctx) {
        loginLocks.clear(lockSubject(account));
        AppSessionPort.IssuedSession session = sessions.issue(account.id(), ctx.deviceId());
        tx.executeWithoutResult(status -> {
            accounts.markLogin(account.id(), AccountRepository.ipBytes(ctx.ip()), now());
            devices.upsert(account.id(), ctx.deviceId(), ctx.deviceName(), ctx.platform(),
                    AccountRepository.ipBytes(ctx.ip()), null, session.tokenHash(), now());
            outbox.securityLogin(account.id(), PhoneCipher.mask(phone), ctx.requestId());
        });
        log.info("login succeeded accountId={} requestId={}", account.id(), ctx.requestId());
        return toLoginResult(account, session, phone);
    }

    private AccountRecord loadForLogin(String phone) {
        String phoneHash = phoneCipher.hmacHex(phone);
        AccountRecord account = accounts.findByPhoneHash(phoneHash).orElse(null);
        String subject = account == null ? "ph:" + phoneHash : lockSubject(account);
        if (loginLocks.isLocked(subject) || (account != null && account.locked())) {
            throw AccountFailures.accountLocked();
        }
        if (account == null || account.cancelled()) {
            loginLocks.recordFailure(subject, properties.loginFailLimit(), properties.loginLock());
            throw AccountFailures.badRequest("手机号或密码错误");
        }
        if (account.cancelling()) {
            // 冷静期内允许登录以便撤销注销
            return account;
        }
        return account;
    }

    private void onLoginFailure(AccountRecord account) {
        String subject = lockSubject(account);
        loginLocks.recordFailure(subject, properties.loginFailLimit(), properties.loginLock());
        if (loginLocks.isLocked(subject)) {
            throw AccountFailures.accountLocked();
        }
    }

    private void assertLoginAllowed(AccountRecord account) {
        if (account.locked() || loginLocks.isLocked(lockSubject(account))) {
            throw AccountFailures.accountLocked();
        }
        if (account.cancelled()) {
            throw AccountFailures.accountLocked();
        }
    }

    private void consumeCode(SmsScene scene, String phone, String code) {
        if (code == null || !code.matches("^\\d{6}$")) {
            throw AccountFailures.verifyCodeInvalid();
        }
        String phoneHash = phoneCipher.hmacHex(phone);
        SmsCodeStore.ConsumeResult result = smsCodes.consume(scene, phoneHash, codeHash(scene, phoneHash, code));
        if (result != SmsCodeStore.ConsumeResult.MATCHED) {
            throw AccountFailures.verifyCodeInvalid();
        }
    }

    private String codeHash(SmsScene scene, String phoneHash, String code) {
        return phoneCipher.hmacUtf8(scene.wire() + ":" + phoneHash + ":" + code);
    }

    private String generateCode() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    private AccountRecord requireAccount() {
        long id = requireLogin();
        AccountRecord account = accounts.findById(id).orElseThrow(AccountFailures::unauthorized);
        if (account.cancelled()) {
            throw AccountFailures.unauthorized();
        }
        return account;
    }

    private AccountResults.LoginResult toLoginResult(AccountRecord account, AppSessionPort.IssuedSession session, String phone) {
        return new AccountResults.LoginResult(
                session.token(),
                session.expireInSeconds(),
                new AccountResults.AccountBrief(account.id(), account.nickname(), account.avatar(), PhoneCipher.mask(phone)),
                account.bindStatus(),
                members.findSnapshot(account.id()).orElse(null));
    }

    private AccountResults.ProfileResult toProfile(AccountRecord account) {
        String mask = PhoneCipher.mask(phoneCipher.decrypt(account.phoneCipher()));
        return new AccountResults.ProfileResult(
                account.id(),
                account.nickname(),
                account.avatar(),
                mask,
                account.status(),
                account.bindStatus(),
                AccountTimes.format(account.cancelApplyAt()),
                AccountTimes.format(account.lastLoginAt()),
                AccountTimes.format(account.createTime()));
    }

    private static String lockSubject(AccountRecord account) {
        return "id:" + account.id();
    }

    private static void requirePhone(String phone) {
        if (!PhoneCipher.isPhone(phone)) {
            throw AccountFailures.badRequest("手机号格式不正确");
        }
    }

    private static void requireIdempotencyKey(ClientContext ctx, String scene) {
        if (ctx.idempotencyKey() == null || ctx.idempotencyKey().isBlank()) {
            throw AccountFailures.badRequest("X-Idempotency-Key is required");
        }
        if (ctx.idempotencyKey().length() > 64) {
            throw AccountFailures.badRequest("X-Idempotency-Key is required");
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock.withZone(SHANGHAI));
    }

    private String idempotent(String scene, long refId, ClientContext ctx, Map<String, Object> payload,
                              java.util.function.Supplier<String> action) {
        try {
            IdempotencyResult result = idempotency.execute(
                    IdempotencyCommand.of(scene, refId, ctx.idempotencyKey(), payload).withActor(refId == 0 ? null : refId),
                    () -> IdempotencyResult.of("OK", action.get()));
            return result.resultBodyJson();
        } catch (IdempotencyConflictException ex) {
            throw AccountFailures.idempotencyConflict();
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("json serialize failed");
        }
    }

    private String inTx(java.util.function.Supplier<String> jsonAction) {
        String json = tx.execute(status -> jsonAction.get());
        if (json == null) {
            throw new IllegalStateException("transaction returned null");
        }
        return json;
    }

    private <T> T readJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("json parse failed");
        }
    }

    private record RegisteredAccount(long accountId) {
    }
}
