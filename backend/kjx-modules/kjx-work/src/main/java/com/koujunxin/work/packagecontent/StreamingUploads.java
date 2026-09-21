package com.koujunxin.work.packagecontent;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class StreamingUploads {

    private StreamingUploads() {
    }

    public static HashedCopy copyLimited(InputStream in, Path dest, long maxBytes) throws IOException {
        MessageDigest digest = sha256();
        long total = 0;
        try (DigestInputStream digested = new DigestInputStream(in, digest);
             OutputStream out = Files.newOutputStream(dest, StandardOpenOption.CREATE,
                     StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = digested.read(buf)) >= 0) {
                total += n;
                if (total > maxBytes) {
                    Files.deleteIfExists(dest);
                    throw WorkPackageException.badRequest("PACKAGE_TOO_LARGE",
                            "zip exceeds " + maxBytes + " bytes");
                }
                out.write(buf, 0, n);
            }
        }
        return new HashedCopy(total, HexFormat.of().formatHex(digest.digest()));
    }

    public static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    public static String hex(byte[] data) {
        MessageDigest digest = sha256();
        return HexFormat.of().formatHex(digest.digest(data));
    }

    public static String hex(Path file) throws IOException {
        MessageDigest digest = sha256();
        try (InputStream in = Files.newInputStream(file);
             DigestInputStream digested = new DigestInputStream(in, digest)) {
            digested.transferTo(OutputStream.nullOutputStream());
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    public record HashedCopy(long size, String sha256) {
    }
}
