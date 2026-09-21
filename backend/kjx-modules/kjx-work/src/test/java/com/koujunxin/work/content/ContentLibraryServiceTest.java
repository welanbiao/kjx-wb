package com.koujunxin.work.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;

import org.junit.jupiter.api.Test;

import com.koujunxin.work.catalog.WorkRecord;
import com.koujunxin.work.packagecontent.FormAPackageFactory;
import com.koujunxin.work.packagecontent.PackageHarness;

class ContentLibraryServiceTest {

    @Test
    void ingestCreatesContentDocumentsAndCatalogVersion() throws Exception {
        PackageHarness harness = new PackageHarness();
        WorkRecord work = harness.work("content-demo");
        harness.packages.upload(work.id(), "1.0.0",
                new ByteArrayInputStream(FormAPackageFactory.sampleZip("content-demo", "1.0.0")), "op");
        assertEquals(1, harness.catalog.listContentVersions(work.id()).size());
        assertEquals("c-1.0.0", harness.catalog.listContentVersions(work.id()).get(0).version());
        assertTrue(harness.catalog.listContentVersions(work.id()).get(0).contentJson().contains("n1"));
    }
}
