package com.koujunxin.work.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.koujunxin.work.catalog.AccessForm;
import com.koujunxin.work.catalog.CreateContentVersionCommand;
import com.koujunxin.work.catalog.CreateWorkCommand;
import com.koujunxin.work.catalog.CreateWorkVersionCommand;
import com.koujunxin.work.catalog.DuplicateWorkCodeException;
import com.koujunxin.work.catalog.UpdateWorkCommand;
import com.koujunxin.work.catalog.WorkCatalogService;
import com.koujunxin.work.catalog.WorkOptimisticLockException;
import com.koujunxin.work.catalog.WorkRecord;
import com.koujunxin.work.catalog.WorkStatus;
import com.koujunxin.work.catalog.WorkType;

class WorkCatalogMysqlTest extends MysqlWorkSupport {

    private WorkCatalogService catalog;

    @BeforeEach
    void setUp() {
        catalog = catalog();
        truncateWorks();
    }

    @Test
    void flywayCreatesWorkAndVersionTables() {
        Integer works = jdbc().queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'kjx_work'",
                Integer.class);
        Integer workVersions = jdbc().queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'kjx_work_version'",
                Integer.class);
        Integer contentVersions = jdbc().queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'kjx_content_version'",
                Integer.class);
        assertEquals(1, works);
        assertEquals(1, workVersions);
        assertEquals(1, contentVersions);
    }

    @Test
    void crudAndVersionSeparationPersist() {
        WorkRecord created = catalog.create(create("mysql-work"));
        catalog.createWorkVersion(new CreateWorkVersionCommand(created.id(), "1.0.0", "A", null, "/entry", "op"));
        catalog.createContentVersion(new CreateContentVersionCommand(created.id(), "c-9", 2, null, "{\"n\":1}", "op"));
        WorkRecord withCode = catalog.activateWorkVersion(created.id(), "1.0.0", "code", "op");
        WorkRecord withBoth = catalog.activateContentVersion(created.id(), "c-9", "content", "op");
        assertEquals("1.0.0", withBoth.currentVersion());
        assertEquals("c-9", withBoth.contentVersion());
        WorkRecord pending = catalog.update(status(withBoth, WorkStatus.PENDING_REVIEW, "n1"));
        WorkRecord published = catalog.update(status(pending, WorkStatus.PUBLISHED, "n1"));
        assertEquals(WorkStatus.PUBLISHED, published.status());
        assertNotNull(published.publishTime());
        WorkRecord loaded = catalog.getByCode("mysql-work");
        assertEquals(published.id(), loaded.id());
        assertEquals("1.0.0", loaded.currentVersion());
        assertEquals("c-9", loaded.contentVersion());
    }

    @Test
    void duplicateWorkCodeIsConflict() {
        catalog.create(create("dup-code"));
        assertThrows(DuplicateWorkCodeException.class, () -> catalog.create(create("dup-code")));
    }

    @Test
    void concurrentUpdateOnlyOneSucceeds() throws Exception {
        WorkRecord created = catalog.create(create("lock-work"));
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger conflict = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> a = pool.submit(() -> updateOnce(created, "name-a", start, success, conflict));
            Future<?> b = pool.submit(() -> updateOnce(created, "name-b", start, success, conflict));
            start.countDown();
            a.get(15, TimeUnit.SECONDS);
            b.get(15, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, success.get());
        assertEquals(1, conflict.get());
        WorkRecord latest = catalog.getById(created.id());
        assertTrue(latest.name().equals("name-a") || latest.name().equals("name-b"));
        assertNotEquals(created.updateTime(), latest.updateTime());
    }

    private void updateOnce(
            WorkRecord snapshot, String name, CountDownLatch start, AtomicInteger success, AtomicInteger conflict) {
        try {
            start.await(10, TimeUnit.SECONDS);
            catalog.update(new UpdateWorkCommand(
                    snapshot.id(), name, null, null, null, null, null, null, null, null, null,
                    snapshot.updateTime(), "concurrent", "op"));
            success.incrementAndGet();
        } catch (WorkOptimisticLockException ex) {
            conflict.incrementAndGet();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    private static CreateWorkCommand create(String code) {
        return new CreateWorkCommand(code, "Init", WorkType.layout, "s", null, List.of("https://img/a.png"),
                "cat", List.of("t1", "t2"), AccessForm.B, 0, 3, "create", "op");
    }

    private static UpdateWorkCommand status(WorkRecord current, WorkStatus next, String name) {
        return new UpdateWorkCommand(current.id(), name, current.summary(), current.coverUrl(), current.screenshots(),
                current.category(), current.tags(), next, current.accessForm(), current.grayRatio(),
                current.sortWeight(), current.updateTime(), "status", "op");
    }
}
