package com.koujunxin.work.packagecontent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;

import org.junit.jupiter.api.Test;

import com.koujunxin.work.catalog.WorkRecord;

class PackageRollbackTest {

    @Test
    void rollbackRestoresPreviousVersionAndContent() throws Exception {
        PackageHarness harness = new PackageHarness();
        WorkRecord work = harness.work("roll-demo");
        harness.packages.upload(work.id(), "1.0.0",
                new ByteArrayInputStream(FormAPackageFactory.sampleZip("roll-demo", "1.0.0")), "op");
        harness.packages.publish(work.id(), "1.0.0", 0, "publish v1", "op");

        harness.packages.upload(work.id(), "1.1.0",
                new ByteArrayInputStream(FormAPackageFactory.sampleZip("roll-demo", "1.1.0")), "op");
        WorkRecord v2 = harness.packages.publish(work.id(), "1.1.0", 20, "publish v2", "op");
        assertEquals("1.1.0", v2.currentVersion());
        assertEquals("c-1.1.0", v2.contentVersion());
        assertEquals("1.0.0", harness.packages.get(work.id(), "1.1.0").previousVersion());

        WorkRecord restored = harness.packages.rollback(work.id(), "1.1.0", "rollback to v1", "op");
        assertEquals("1.0.0", restored.currentVersion());
        assertEquals("c-1.0.0", restored.contentVersion());
        assertEquals(WorkPackageStatus.ROLLED_BACK, harness.packages.get(work.id(), "1.1.0").status());
        assertEquals(WorkPackageStatus.PUBLISHED, harness.packages.get(work.id(), "1.0.0").status());
        assertEquals("ROLLED_BACK", harness.versions.find(work.id(), "1.1.0").orElseThrow().status());
        assertEquals("PUBLISHED", harness.versions.find(work.id(), "1.0.0").orElseThrow().status());
    }

    @Test
    void rollbackWithoutPreviousFails() throws Exception {
        PackageHarness harness = new PackageHarness();
        WorkRecord work = harness.work("no-prev");
        harness.packages.upload(work.id(), "1.0.0",
                new ByteArrayInputStream(FormAPackageFactory.sampleZip("no-prev", "1.0.0")), "op");
        harness.packages.publish(work.id(), "1.0.0", 0, "only version", "op");
        WorkPackageException ex = assertThrows(WorkPackageException.class,
                () -> harness.packages.rollback(work.id(), "1.0.0", "cannot", "op"));
        assertEquals("PACKAGE_NO_PREVIOUS", ex.errorName());
    }
}
