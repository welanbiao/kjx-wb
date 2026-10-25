package com.koujunxin.runtime.breakup;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.koujunxin.runtime.api.RuntimeFailure;

/**
 * CDN 素材短时签名。密钥不写日志。server/ 不签发。
 */
public final class BreakupAssetService {

    public static final String ASSET_PREFIX = "/app/works/breakup-editor/assets";

    private final byte[] secret;
    private final Path sourceRoot;
    private final Path ossRoot;

    public BreakupAssetService(String hmacSecret) {
        this.secret = (hmacSecret == null || hmacSecret.isBlank()
                ? "local-dev-not-a-runtime-key"
                : hmacSecret).getBytes(StandardCharsets.UTF_8);
        this.sourceRoot = firstExisting(
                System.getenv("KJX_BREAKUP_ASSETS_SRC"),
                "D:/分手结局修改器-v2-给产品/assets");
        String oss = System.getenv("KJX_WORK_OSS_ROOT");
        this.ossRoot = oss == null || oss.isBlank() ? null : Path.of(oss);
    }

    public Map<String, String> signAll(Iterable<String> keys) {
        Map<String, String> urls = new LinkedHashMap<>();
        for (String key : keys) {
            if (key == null || key.isBlank() || looksServer(key)) {
                continue;
            }
            urls.put(normalize(key), sign(normalize(key)));
        }
        return urls;
    }

    public String sign(String key) {
        String safe = normalize(key);
        if (looksServer(safe)) {
            throw RuntimeFailure.forbidden("SERVER_NOT_ON_CDN", "server/ artifacts cannot be signed");
        }
        long exp = Instant.now().plusSeconds(600).getEpochSecond();
        String sig = hmac(safe, exp);
        return ASSET_PREFIX + "?key=" + URLEncoder.encode(safe, StandardCharsets.UTF_8) + "&e=" + exp + "&s=" + sig;
    }

    public byte[] read(String key, long exp, String sig) {
        String safe = normalize(key);
        if (looksServer(safe)) {
            throw RuntimeFailure.forbidden("SERVER_NOT_ON_CDN", "server/ artifacts cannot be signed");
        }
        if (exp < Instant.now().getEpochSecond() || !hmac(safe, exp).equalsIgnoreCase(sig)) {
            throw RuntimeFailure.forbidden("ASSET_SIG_INVALID", "signed url expired or invalid");
        }
        return load(safe).orElseThrow(() -> RuntimeFailure.notFound("ASSET_NOT_FOUND", "asset not found"));
    }

    public Optional<byte[]> load(String key) {
        String safe = normalize(key);
        if (ossRoot != null) {
            Path oss = ossRoot.resolve("cdn").resolve("works/breakup-editor/1.0.0/cdn/assets").resolve(safe).normalize();
            Path base = ossRoot.resolve("cdn").toAbsolutePath().normalize();
            if (oss.startsWith(base) && Files.isRegularFile(oss)) {
                try {
                    return Optional.of(Files.readAllBytes(oss));
                } catch (Exception ex) {
                    return Optional.empty();
                }
            }
        }
        if (sourceRoot != null) {
            Path file = sourceRoot.resolve(safe).normalize();
            if (file.startsWith(sourceRoot.toAbsolutePath().normalize()) && Files.isRegularFile(file)) {
                try {
                    return Optional.of(Files.readAllBytes(file));
                } catch (Exception ex) {
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

    public String rewriteCss(String css, Map<String, String> urls) {
        if (css == null || css.isBlank()) {
            return "";
        }
        String out = css;
        for (Map.Entry<String, String> entry : urls.entrySet()) {
            String key = entry.getKey();
            String url = entry.getValue();
            out = out.replace("../assets/" + key, url);
            out = out.replace("assets/" + key, url);
            out = out.replace("url(\"" + key + "\")", "url(\"" + url + "\")");
            out = out.replace("url('" + key + "')", "url('" + url + "')");
            out = out.replace("url(\"../assets/" + key + "\")", "url(\"" + url + "\")");
            out = out.replace("url('../assets/" + key + "')", "url('" + url + "')");
        }
        out = out.replace("../assets/fonts/noto-serif-sc-600-subset.woff2",
                urls.getOrDefault("fonts/noto-serif-sc-600-subset.woff2", ""));
        return out;
    }

    public static String contentType(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".svg")) {
            return "image/svg+xml";
        }
        if (lower.endsWith(".mp3")) {
            return "audio/mpeg";
        }
        if (lower.endsWith(".woff2")) {
            return "font/woff2";
        }
        if (lower.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }
        return "application/octet-stream";
    }

    public static String normalize(String key) {
        String safe = key == null ? "" : key.replace('\\', '/');
        if (safe.startsWith("assets/")) {
            safe = safe.substring("assets/".length());
        }
        if (safe.startsWith("/")) {
            safe = safe.substring(1);
        }
        if (safe.contains("..") || safe.contains(":")) {
            throw RuntimeFailure.badRequest("OSS_BAD_KEY", "invalid object key");
        }
        return safe;
    }

    static boolean looksServer(String key) {
        String lower = key == null ? "" : key.toLowerCase(Locale.ROOT);
        return lower.contains("server/") || lower.contains("/server");
    }

    private String hmac(String key, long exp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] out = mac.doFinal(("CDN|" + key + "|" + exp).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(out);
        } catch (Exception ex) {
            throw RuntimeFailure.badRequest("SIGN_FAILED", "failed to sign object");
        }
    }

    private static Path firstExisting(String... candidates) {
        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            Path path = Path.of(candidate);
            if (Files.isDirectory(path)) {
                return path.toAbsolutePath().normalize();
            }
        }
        return null;
    }
}
