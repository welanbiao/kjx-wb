package com.koujunxin.work.packagecontent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;

import org.junit.jupiter.api.Test;

import com.koujunxin.work.catalog.WorkRecord;

class FormASamplePackageTest {

    @Test
    void formAPackageCanUploadValidatePreviewAndPublish() throws Exception {
        PackageHarness harness = new PackageHarness();
        WorkRecord work = harness.work("demo-form-a");
        byte[] zip = FormAPackageFactory.sampleZip("demo-form-a", "1.0.0");
        WorkPackageRecord uploaded = harness.packages.upload(
                work.id(), "1.0.0", new ByteArrayInputStream(zip), "op");
        assertEquals(WorkPackageStatus.VALIDATED, uploaded.status());
        assertEquals(15, harness.mapper.readTree(uploaded.validateReportJson()).get("findings").size());

        ValidationReport report = harness.packages.validate(work.id(), "1.0.0");
        assertTrue(report.passed());
        assertEquals(15, report.findings().size());

        PackagePreview preview = harness.packages.preview(work.id(), "1.0.0");
        assertEquals("A", preview.form());
        assertFalse(preview.layout().isEmpty());
        assertFalse(preview.tokens().isEmpty());
        assertTrue(preview.assetUrls().containsKey("assets/images/icon.svg"));
        assertFalse(preview.assetUrls().keySet().stream().anyMatch(CdnIsolation::isServerPath));

        WorkRecord published = harness.packages.publish(work.id(), "1.0.0", 10, "ship form a", "op");
        assertEquals("1.0.0", published.currentVersion());
        assertEquals("c-1.0.0", published.contentVersion());
        assertEquals("PUBLISHED", harness.versions.find(work.id(), "1.0.0").orElseThrow().status());
        assertNotNull(harness.packages.get(work.id(), "1.0.0").previewToken());
    }
}
