package com.koujunxin.work.asset;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.koujunxin.work.packagecontent.CdnIsolation;
import com.koujunxin.work.packagecontent.WorkPackageException;

/**
 * 本地 HMAC 签名。密钥仅驻留内存，不写日志、不入 OSS 元数据表。
 */
public final class HmacSignedUrlSigner {

    private static final Logger log = LoggerFactory.getLogger(HmacSignedUrlSigner.class);
    public static final String LOCAL_DEV_SECRET = "local-dev-not-a-cloud-key";

    private final byte[] secret;

    public HmacSignedUrlSigner(String secret) {
        String value = secret == null || secret.isBlank() ? LOCAL_DEV_SECRET : secret;
        this.secret = value.getBytes(StandardCharsets.UTF_8);
    }

    public SignedUrl sign(StorageScope scope, String objectKey, Duration ttl) {
        if (scope == StorageScope.CDN) {
            CdnIsolation.rejectCdnKey(objectKey);
        }
        Instant expires = Instant.now().plus(ttl == null ? Duration.ofMinutes(10) : ttl);
        String sig = hmac(scope, objectKey, expires.getEpochSecond());
        String url = "local://" + scope.name().toLowerCase() + "/" + objectKey
                + "?e=" + expires.getEpochSecond() + "&s=" + sig;
        log.debug("signed object key={} scope={} exp={}", objectKey, scope, expires);
        return new SignedUrl(url, expires, objectKey, scope);
    }

    public boolean verify(StorageScope scope, String objectKey, long expEpoch, String sig) {
        if (expEpoch < Instant.now().getEpochSecond()) {
            return false;
        }
        return hmac(scope, objectKey, expEpoch).equalsIgnoreCase(sig);
    }

    private String hmac(StorageScope scope, String objectKey, long exp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] out = mac.doFinal((scope.name() + "|" + objectKey + "|" + exp).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(out);
        } catch (Exception ex) {
            throw new WorkPackageException(500, "SIGN_FAILED", "failed to sign object");
        }
    }
}
