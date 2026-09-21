package com.koujunxin.work.packagecontent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.koujunxin.work.asset.StorageScope;
import com.koujunxin.work.catalog.WorkRecord;

class WorkPackageSecurityTest {

    @Test
    void rejectsOversizedUpload() {
        WorkPackageLimits limits = new WorkPackageLimits(
                32, 500, 1024, 100, 800 * 1024, 64 * 1024, 2 * 1024 * 1024, 500 * 1024, 20 * 1024 * 1024, "1.0.0");
        PackageHarness harness = new PackageHarness(limits);
        WorkRecord work = harness.work("size-limit");
        byte[] big = new byte[64];
        WorkPackageException ex = assertThrows(WorkPackageException.class,
                () -> harness.packages.upload(work.id(), "1.0.0", new ByteArrayInputStream(big), "op"));
        assertEquals("PACKAGE_TOO_LARGE", ex.errorName());
    }

    @Test
    void rejectsExternalUrl() throws Exception {
        PackageHarness harness = new PackageHarness();
        WorkRecord work = harness.work("has-link");
        byte[] zip = FormAPackageFactory.mutate("has-link", "1.0.0", files ->
                files.put("theme/tokens.json", "{\"cover\":\"https://evil.example/a.png\"}".getBytes(StandardCharsets.UTF_8)));
        WorkPackageException ex = assertThrows(WorkPackageException.class,
                () -> harness.packages.upload(work.id(), "1.0.0", new ByteArrayInputStream(zip), "op"));
        assertEquals("PACKAGE_REJECTED", ex.errorName());
        assertTrue(ex.report().finding(ReviewCheck.NO_EXTERNAL_URL).failed());
    }

    @Test
    void rejectsScriptSvg() throws Exception {
        PackageHarness harness = new PackageHarness();
        WorkRecord work = harness.work("bad-svg");
        byte[] zip = FormAPackageFactory.mutate("bad-svg", "1.0.0", files ->
                files.put("assets/images/icon.svg",
                        "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>"
                                .getBytes(StandardCharsets.UTF_8)));
        WorkPackageException ex = assertThrows(WorkPackageException.class,
                () -> harness.packages.upload(work.id(), "1.0.0", new ByteArrayInputStream(zip), "op"));
        assertTrue(ex.report().finding(ReviewCheck.SVG_SAFE).failed());
    }

    @Test
    void rejectsOnEventSvg() throws Exception {
        PackageHarness harness = new PackageHarness();
        WorkRecord work = harness.work("on-svg");
        byte[] zip = FormAPackageFactory.mutate("on-svg", "1.0.0", files ->
                files.put("assets/images/icon.svg",
                        "<svg xmlns=\"http://www.w3.org/2000/svg\" onload=\"alert(1)\"></svg>"
                                .getBytes(StandardCharsets.UTF_8)));
        WorkPackageException ex = assertThrows(WorkPackageException.class,
                () -> harness.packages.upload(work.id(), "1.0.0", new ByteArrayInputStream(zip), "op"));
        assertTrue(ex.report().finding(ReviewCheck.SVG_SAFE).failed());
    }

    @Test
    void serverFilesCannotBeSignedForCdn() throws Exception {
        PackageHarness harness = new PackageHarness();
        WorkRecord work = harness.work("iso-cdn");
        byte[] zip = FormAPackageFactory.sampleZip("iso-cdn", "1.0.0");
        harness.packages.upload(work.id(), "1.0.0", new ByteArrayInputStream(zip), "op");
        assertTrue(harness.storage.get(StorageScope.SERVER,
                "works/" + work.id() + "/1.0.0/server/prompts/system.md").isPresent());
        WorkPackageException ex = assertThrows(WorkPackageException.class,
                () -> harness.packages.signCdn("works/" + work.id() + "/1.0.0/server/prompts/system.md"));
        assertEquals("SERVER_NOT_ON_CDN", ex.errorName());
        assertFalse(harness.assets.previewUrls(work.id(), "1.0.0").keySet().stream()
                .anyMatch(CdnIsolation::isServerPath));
    }
}
