package com.koujunxin.work.packagecontent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;

class IsolatedZipExtractorTest {

    @Test
    void rejectsZipSlip() throws Exception {
        byte[] zip = zip(Map.of("../evil.txt", "hacked".getBytes(StandardCharsets.UTF_8)));
        Path file = Files.createTempFile("slip-", ".zip");
        Files.write(file, zip);
        IsolatedZipExtractor extractor = new IsolatedZipExtractor(WorkPackageLimits.standard());
        WorkPackageException ex = assertThrows(WorkPackageException.class, () -> extractor.extract(file));
        assertEquals("PACKAGE_ZIP_SLIP", ex.errorName());
        Files.deleteIfExists(file);
    }

    @Test
    void rejectsTooManyFiles() throws Exception {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("a.txt", "1".getBytes(StandardCharsets.UTF_8));
        files.put("b.txt", "2".getBytes(StandardCharsets.UTF_8));
        files.put("c.txt", "3".getBytes(StandardCharsets.UTF_8));
        WorkPackageLimits limits = new WorkPackageLimits(1024, 2, 1024, 100, 800, 64, 2048, 500, 20, "1.0.0");
        Path file = Files.createTempFile("many-", ".zip");
        Files.write(file, FormAPackageFactory.zip(files));
        IsolatedZipExtractor extractor = new IsolatedZipExtractor(limits);
        WorkPackageException ex = assertThrows(WorkPackageException.class, () -> extractor.extract(file));
        assertEquals("PACKAGE_TOO_MANY_FILES", ex.errorName());
        Files.deleteIfExists(file);
    }

    @Test
    void rejectsZipBombByUncompressedLimit() throws Exception {
        byte[] zeros = new byte[64];
        WorkPackageLimits limits = new WorkPackageLimits(1024, 10, 16, 100, 800, 64, 2048, 500, 20, "1.0.0");
        Path file = Files.createTempFile("bomb-", ".zip");
        Files.write(file, zip(Map.of("big.bin", zeros)));
        IsolatedZipExtractor extractor = new IsolatedZipExtractor(limits);
        WorkPackageException ex = assertThrows(WorkPackageException.class, () -> extractor.extract(file));
        assertEquals("PACKAGE_ZIP_BOMB", ex.errorName());
        Files.deleteIfExists(file);
    }

    @Test
    void extractsIntoIsolatedDirectory() throws Exception {
        byte[] zip = FormAPackageFactory.sampleZip("demo-form-a", "1.0.0");
        Path file = Files.createTempFile("ok-", ".zip");
        Files.write(file, zip);
        IsolatedZipExtractor.ExtractedPackage extracted =
                new IsolatedZipExtractor(WorkPackageLimits.standard()).extract(file);
        assertTrue(Files.isRegularFile(extracted.contentRoot().resolve("manifest.json")));
        assertTrue(extracted.tempRoot().toString().contains("kjx-pkg-"));
        IsolatedZipExtractor.deleteRecursively(extracted.tempRoot());
        Files.deleteIfExists(file);
    }

    private static byte[] zip(Map<String, byte[]> files) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
            for (Map.Entry<String, byte[]> entry : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return buffer.toByteArray();
    }
}
