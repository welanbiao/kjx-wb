package com.koujunxin.work.packagecontent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.koujunxin.work.catalog.AccessForm;
import com.koujunxin.work.catalog.CreateWorkCommand;
import com.koujunxin.work.catalog.WorkRecord;
import com.koujunxin.work.catalog.WorkType;

class WorkPackageMysqlTest extends PackageMysqlSupport {

    @BeforeEach
    void setUp() {
        truncateAll();
    }

    @Test
    void flywayCreatesWp31Tables() {
        assertEquals(1, table("kjx_work_package"));
        assertEquals(1, table("kjx_asset"));
        assertEquals(1, table("kjx_music_track"));
        assertEquals(1, table("kjx_content_document"));
        assertEquals(1, table("kjx_oss_object"));
        Integer v424 = jdbc().queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '424'",
                Integer.class);
        assertEquals(1, v424);
    }

    @Test
    void uploadPublishRollbackPersistsPreviousVersion() throws Exception {
        WorkRecord work = catalog().create(new CreateWorkCommand(
                "mysql-form-a", "MySQL Form A", WorkType.layout, "s", null, null, "demo", null,
                AccessForm.A, 0, 0, "create", "op"));
        packages().upload(work.id(), "1.0.0",
                new ByteArrayInputStream(FormAPackageFactory.sampleZip("mysql-form-a", "1.0.0")), "op");
        packages().publish(work.id(), "1.0.0", 0, "v1", "op");
        packages().upload(work.id(), "1.1.0",
                new ByteArrayInputStream(FormAPackageFactory.sampleZip("mysql-form-a", "1.1.0")), "op");
        packages().publish(work.id(), "1.1.0", 15, "v2", "op");
        assertEquals("1.1.0", catalog().getById(work.id()).currentVersion());
        packages().rollback(work.id(), "1.1.0", "restore v1", "op");
        WorkRecord restored = catalog().getById(work.id());
        assertEquals("1.0.0", restored.currentVersion());
        assertEquals("c-1.0.0", restored.contentVersion());
        String status = jdbc().queryForObject(
                "SELECT status FROM kjx_work_package WHERE work_id = ? AND version = ?",
                String.class, work.id(), "1.1.0");
        assertEquals("ROLLED_BACK", status);
        Integer ossRows = jdbc().queryForObject("SELECT COUNT(*) FROM kjx_oss_object WHERE work_id = ?",
                Integer.class, work.id());
        assertTrue(ossRows > 0);
    }

    private Integer table(String name) {
        return jdbc().queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?",
                Integer.class, name);
    }
}
