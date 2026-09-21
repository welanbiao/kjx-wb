package com.koujunxin.work.packagecontent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.koujunxin.work.asset.AssetService;
import com.koujunxin.work.asset.HmacSignedUrlSigner;
import com.koujunxin.work.asset.InMemoryAssetRepository;
import com.koujunxin.work.asset.InMemoryObjectStorage;
import com.koujunxin.work.asset.ObjectStoragePort;
import com.koujunxin.work.catalog.AccessForm;
import com.koujunxin.work.catalog.CreateWorkCommand;
import com.koujunxin.work.catalog.InMemoryContentVersionRepository;
import com.koujunxin.work.catalog.InMemoryWorkRepository;
import com.koujunxin.work.catalog.InMemoryWorkVersionRepository;
import com.koujunxin.work.catalog.WorkCatalogService;
import com.koujunxin.work.catalog.WorkRecord;
import com.koujunxin.work.catalog.WorkType;
import com.koujunxin.work.catalog.WorkVersionRepository;
import com.koujunxin.work.content.ContentLibraryService;
import com.koujunxin.work.content.InMemoryContentDocumentRepository;
import com.koujunxin.work.music.InMemoryMusicRepository;
import com.koujunxin.work.music.MusicService;
import com.koujunxin.work.port.NoOpWorkAuditPort;
import com.koujunxin.work.port.NoOpWorkLifecycleOutboxPort;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

public final class PackageHarness {

    public final ObjectMapper mapper = new ObjectMapper();
    public final WorkPackageLimits limits;
    public final WorkCatalogService catalog;
    public final WorkVersionRepository versions;
    public final ObjectStoragePort storage;
    public final WorkPackageService packages;
    public final AssetService assets;
    public final MusicService music;

    public PackageHarness() {
        this(WorkPackageLimits.standard());
    }

    public PackageHarness(WorkPackageLimits limits) {
        this.limits = limits;
        this.versions = new InMemoryWorkVersionRepository();
        this.catalog = new WorkCatalogService(
                new InMemoryWorkRepository(),
                versions,
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
        HmacSignedUrlSigner signer = new HmacSignedUrlSigner(HmacSignedUrlSigner.LOCAL_DEV_SECRET);
        this.storage = new InMemoryObjectStorage(signer);
        this.assets = new AssetService(new InMemoryAssetRepository(), storage);
        this.music = new MusicService(new InMemoryMusicRepository(), storage, mapper);
        this.packages = new WorkPackageService(
                catalog,
                versions,
                new InMemoryWorkPackageRepository(),
                storage,
                new WorkPackageInspector(mapper, limits),
                new IsolatedZipExtractor(limits),
                assets,
                new ContentLibraryService(new InMemoryContentDocumentRepository(), catalog, mapper),
                music,
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
                limits,
                mapper);
    }

    public WorkRecord work(String code) {
        return catalog.create(new CreateWorkCommand(
                code, "Demo " + code, WorkType.layout, "s", null, null, "demo", null,
                AccessForm.A, 0, 0, "create", "op"));
    }
}
