package com.koujunxin.work.packagecontent;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/**
 * 隔离目录解压：拒绝 Zip Slip、压缩炸弹、过多文件、嵌套 zip。
 */
public final class IsolatedZipExtractor {

    private final WorkPackageLimits limits;

    public IsolatedZipExtractor(WorkPackageLimits limits) {
        this.limits = limits;
    }

    public ExtractedPackage extract(Path zipFile) throws IOException {
        Path root = Files.createTempDirectory("kjx-pkg-");
        long uncompressed = 0;
        try (ZipFile zip = new ZipFile(zipFile.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            int seen = 0;
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String relative = normalizeName(entry.getName());
                if (relative.isEmpty() || relative.endsWith("/")) {
                    if (!relative.isEmpty()) {
                        Files.createDirectories(safeResolve(root, relative));
                    }
                    continue;
                }
                seen++;
                if (seen > limits.maxFiles()) {
                    deleteRecursively(root);
                    throw WorkPackageException.badRequest("PACKAGE_TOO_MANY_FILES",
                            "file count exceeds " + limits.maxFiles());
                }
                if (relative.toLowerCase(Locale.ROOT).endsWith(".zip")) {
                    deleteRecursively(root);
                    throw WorkPackageException.badRequest("PACKAGE_NESTED_ZIP", "nested zip is not allowed: " + relative);
                }
                Path dest = safeResolve(root, relative);
                Files.createDirectories(dest.getParent());
                long entryBytes = copyEntry(zip, entry, dest);
                uncompressed += entryBytes;
                if (uncompressed > limits.maxUncompressedBytes()) {
                    deleteRecursively(root);
                    throw WorkPackageException.badRequest("PACKAGE_ZIP_BOMB",
                            "uncompressed size exceeds " + limits.maxUncompressedBytes());
                }
                long compressed = entry.getCompressedSize();
                if (compressed > 0 && entryBytes / compressed > limits.maxCompressionRatio()) {
                    deleteRecursively(root);
                    throw WorkPackageException.badRequest("PACKAGE_ZIP_BOMB",
                            "compression ratio exceeds " + limits.maxCompressionRatio() + " for " + relative);
                }
            }
        } catch (ZipException ex) {
            deleteRecursively(root);
            throw WorkPackageException.badRequest("PACKAGE_ZIP_CORRUPT", "zip is not readable");
        } catch (WorkPackageException ex) {
            deleteRecursively(root);
            throw ex;
        } catch (IOException ex) {
            deleteRecursively(root);
            throw ex;
        }
        Path contentRoot = unwrapSingleRoot(root);
        List<String> relativeFiles = listRegularFiles(contentRoot);
        return new ExtractedPackage(root, contentRoot, List.copyOf(relativeFiles));
    }

    static Path unwrapSingleRoot(Path root) throws IOException {
        try (var stream = Files.list(root)) {
            List<Path> children = stream.toList();
            if (children.size() == 1 && Files.isDirectory(children.get(0))
                    && !Files.isRegularFile(root.resolve("manifest.json"))) {
                return children.get(0);
            }
        }
        return root;
    }

    static List<String> listRegularFiles(Path contentRoot) throws IOException {
        try (var walk = Files.walk(contentRoot)) {
            return walk.filter(Files::isRegularFile)
                    .map(path -> contentRoot.relativize(path).toString().replace('\\', '/'))
                    .sorted()
                    .toList();
        }
    }

    static String normalizeName(String raw) {
        if (raw == null) {
            return "";
        }
        String name = raw.replace('\\', '/');
        while (name.startsWith("./")) {
            name = name.substring(2);
        }
        if (name.startsWith("/") || name.contains(":")) {
            throw WorkPackageException.badRequest("PACKAGE_ZIP_SLIP", "absolute zip path rejected: " + raw);
        }
        for (String part : name.split("/")) {
            if ("..".equals(part)) {
                throw WorkPackageException.badRequest("PACKAGE_ZIP_SLIP", "zip slip rejected: " + raw);
            }
        }
        return name;
    }

    public static Path safeResolve(Path root, String relative) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path dest = normalizedRoot.resolve(relative).normalize();
        if (!dest.startsWith(normalizedRoot)) {
            throw WorkPackageException.badRequest("PACKAGE_ZIP_SLIP", "zip slip rejected: " + relative);
        }
        return dest;
    }

    private long copyEntry(ZipFile zip, ZipEntry entry, Path dest) throws IOException {
        long written = 0;
        try (InputStream in = zip.getInputStream(entry);
             OutputStream out = Files.newOutputStream(dest)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) >= 0) {
                written += n;
                if (written > limits.maxUncompressedBytes()) {
                    throw WorkPackageException.badRequest("PACKAGE_ZIP_BOMB",
                            "entry expands beyond uncompressed limit: " + entry.getName());
                }
                out.write(buf, 0, n);
            }
        }
        return written;
    }

    public static void deleteRecursively(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            walk.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // best-effort cleanup
                }
            });
        } catch (IOException ignored) {
            // best-effort cleanup
        }
    }

    public record ExtractedPackage(Path tempRoot, Path contentRoot, List<String> files) {
        public Path root() {
            return contentRoot;
        }

        public Path tempRoot() {
            return tempRoot;
        }
    }
}
