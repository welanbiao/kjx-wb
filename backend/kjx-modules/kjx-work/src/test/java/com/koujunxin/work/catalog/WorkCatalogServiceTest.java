package com.koujunxin.work.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import com.koujunxin.work.port.NoOpWorkAuditPort;
import com.koujunxin.work.port.NoOpWorkLifecycleOutboxPort;

class WorkCatalogServiceTest {

    private WorkCatalogService catalog;

    @BeforeEach
    void setUp() {
        catalog = new WorkCatalogService(
                new InMemoryWorkRepository(),
                new InMemoryWorkVersionRepository(),
                new InMemoryContentVersionRepository(),
                new TransactionTemplate(new AbstractPlatformTransactionManager() {
                    @Override
                    protected Object doGetTransaction() {
                        return new Object();
                    }

                    @Override
                    protected void doBegin(Object transaction, TransactionDefinition definition) {
                    }

                    @Override
                    protected void doCommit(DefaultTransactionStatus status) {
                    }

                    @Override
                    protected void doRollback(DefaultTransactionStatus status) {
                    }
                }),
                new NoOpWorkAuditPort(),
                new NoOpWorkLifecycleOutboxPort());
    }

    @Test
    void createStartsAsDraftWithoutVersions() {
        WorkRecord created = catalog.create(create("breakup-editor"));
        assertEquals(WorkStatus.DRAFT, created.status());
        assertNull(created.currentVersion());
        assertNull(created.contentVersion());
        assertNull(created.publishTime());
    }

    @Test
    void workAndContentVersionsAreIndependent() {
        WorkRecord created = catalog.create(create("layout-skin"));
        catalog.createWorkVersion(new CreateWorkVersionCommand(created.id(), "1.0.0", "A", null, null, "op"));
        catalog.createContentVersion(new CreateContentVersionCommand(created.id(), "c-1", 3, null, "{}", "op"));
        WorkRecord afterWork = catalog.activateWorkVersion(created.id(), "1.0.0", "set code", "op");
        assertEquals("1.0.0", afterWork.currentVersion());
        assertNull(afterWork.contentVersion());
        WorkRecord afterContent = catalog.activateContentVersion(created.id(), "c-1", "set content", "op");
        assertEquals("1.0.0", afterContent.currentVersion());
        assertEquals("c-1", afterContent.contentVersion());
        catalog.createWorkVersion(new CreateWorkVersionCommand(created.id(), "1.1.0", "A", null, null, "op"));
        WorkRecord bumped = catalog.activateWorkVersion(created.id(), "1.1.0", "bump code", "op");
        assertEquals("1.1.0", bumped.currentVersion());
        assertEquals("c-1", bumped.contentVersion());
    }

    @Test
    void statusMachineAndPublishTime() {
        WorkRecord created = catalog.create(create("story-one"));
        WorkRecord pending = catalog.update(status(created, WorkStatus.PENDING_REVIEW));
        WorkRecord published = catalog.update(status(pending, WorkStatus.PUBLISHED));
        assertEquals(WorkStatus.PUBLISHED, published.status());
        assertNotNull(published.publishTime());
        WorkRecord gray = catalog.update(status(published, WorkStatus.GRAY));
        assertEquals(published.publishTime(), gray.publishTime());
        WorkRecord down = catalog.update(status(gray, WorkStatus.UNPUBLISHED));
        assertEquals(WorkStatus.UNPUBLISHED, down.status());
        assertEquals(published.publishTime(), down.publishTime());
        assertThrows(InvalidWorkTransitionException.class,
                () -> catalog.update(status(down, WorkStatus.DRAFT)));
    }

    @Test
    void duplicateWorkCodeRejected() {
        catalog.create(create("same-code"));
        assertThrows(DuplicateWorkCodeException.class, () -> catalog.create(create("same-code")));
    }

    @Test
    void reasonRequired() {
        assertThrows(WorkDomainException.class, () -> catalog.create(
                new CreateWorkCommand("need-reason", "n", WorkType.layout, null, null, null, null, null,
                        AccessForm.A, 0, 0, " ", "op")));
    }

    private static CreateWorkCommand create(String code) {
        return new CreateWorkCommand(code, "Name " + code, WorkType.game, "s", null, null, "cat",
                java.util.List.of("tag"), AccessForm.A, 0, 1, "create", "op");
    }

    private static UpdateWorkCommand status(WorkRecord current, WorkStatus next) {
        return new UpdateWorkCommand(current.id(), null, null, null, null, null, null, next, null, null, null,
                current.updateTime(), "transition", "op");
    }
}
