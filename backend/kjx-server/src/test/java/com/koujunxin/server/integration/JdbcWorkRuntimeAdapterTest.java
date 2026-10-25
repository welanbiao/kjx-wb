package com.koujunxin.server.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.koujunxin.runtime.api.RuntimeFailure;
import com.koujunxin.runtime.api.WorkRuntimeManifest;
import com.koujunxin.server.integration.JdbcWorkRuntimeAdapter.PublishedWork;
import com.koujunxin.work.asset.AssetService;
import com.koujunxin.work.asset.ObjectStoragePort;
import com.koujunxin.work.asset.SignedUrl;
import com.koujunxin.work.asset.StorageScope;

@ExtendWith(MockitoExtension.class)
class JdbcWorkRuntimeAdapterTest {

    @Mock
    private JdbcTemplate jdbc;
    @Mock
    private ObjectProvider<AssetService> assetProvider;
    @Mock
    private ObjectProvider<ObjectStoragePort> storageProvider;
    @Mock
    private AssetService assets;
    @Mock
    private ObjectStoragePort storage;

    private JdbcWorkRuntimeAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new JdbcWorkRuntimeAdapter(jdbc, assetProvider, storageProvider, new ObjectMapper());
    }

    @Test
    void unpublishedWorkIsNotFound() {
        doReturn(List.of())
                .when(jdbc).query(eq(JdbcWorkRuntimeAdapter.REQUIRE_SQL), ArgumentMatchers.<RowMapper<?>>any(), eq("ghost"));
        RuntimeFailure ex = assertThrows(RuntimeFailure.class, () -> adapter.require("ghost"));
        assertEquals("WORK_NOT_FOUND", ex.errorName());
        assertEquals(404, ex.httpStatus());
    }

    @Test
    void signAssetRejectsServerPath() {
        RuntimeFailure ex = assertThrows(
                RuntimeFailure.class,
                () -> adapter.signAsset("demo", "1.0.0", "server/prompt.md", "1"));
        assertEquals("SERVER_NOT_ON_CDN", ex.errorName());
        assertEquals(403, ex.httpStatus());
        verify(assets, never()).sign(anyLong(), anyString(), anyString());
        verify(jdbc, never()).query(anyString(), ArgumentMatchers.<RowMapper<?>>any(), anyString());
    }

    @Test
    void requirePublishedWorkBuildsManifest() {
        doReturn(List.of(new PublishedWork(
                        12L, "breakup-editor", "1.2.0", "c-9", "A", "B", "c-pack")))
                .when(jdbc).query(eq(JdbcWorkRuntimeAdapter.REQUIRE_SQL), ArgumentMatchers.<RowMapper<?>>any(), eq("breakup-editor"));
        WorkRuntimeManifest manifest = adapter.require("breakup-editor");
        assertEquals(12L, manifest.workId());
        assertEquals("B", manifest.form());
        assertEquals("1.2.0", manifest.packageVersion());
        assertEquals("c-pack", manifest.contentVersion());
        assertEquals("1", manifest.schemaVersion());
        assertEquals("dist/index.html", manifest.entry());
        assertEquals("https://breakup-editor.game.koujunxin.com", manifest.sandboxOrigin());
    }

    @Test
    void loadStageSkipsMissingCdnFilesAndDoesNotGate() {
        doReturn(List.of(new PublishedWork(8L, "demo", "1.0.0", null, "A", null, null)))
                .when(jdbc).query(eq(JdbcWorkRuntimeAdapter.REQUIRE_SQL), ArgumentMatchers.<RowMapper<?>>any(), eq("demo"));
        when(storageProvider.getIfAvailable()).thenReturn(storage);
        when(assetProvider.getIfAvailable()).thenReturn(assets);
        when(storage.get(eq(StorageScope.CDN), anyString())).thenReturn(Optional.empty());
        when(assets.list(8L, "1.0.0")).thenReturn(List.of());

        var stage = adapter.loadStage("demo", "1.0.0", "1", List.of("1"));
        assertEquals("{}", stage.layoutJson());
        assertEquals("{}", stage.tokensJson());
        assertEquals("[]", stage.nodesJson());
        assertFalse(stage.gated());
    }

    @Test
    void loadStageFiltersNodesByStageAndKeepsUnparsedText() {
        doReturn(List.of(new PublishedWork(8L, "demo", "1.0.0", null, "A", "A", null)))
                .when(jdbc).query(eq(JdbcWorkRuntimeAdapter.REQUIRE_SQL), ArgumentMatchers.<RowMapper<?>>any(), eq("demo"));
        when(storageProvider.getIfAvailable()).thenReturn(storage);
        when(assetProvider.getIfAvailable()).thenReturn(assets);
        when(storage.get(eq(StorageScope.CDN), eq("works/8/1.0.0/cdn/theme/layout.json")))
                .thenReturn(Optional.of("{\"ok\":true}".getBytes()));
        when(storage.get(eq(StorageScope.CDN), eq("works/8/1.0.0/cdn/theme/tokens.json")))
                .thenReturn(Optional.of("{\"ink\":\"#fff\"}".getBytes()));
        when(storage.get(eq(StorageScope.CDN), eq("works/8/1.0.0/cdn/content/nodes.json")))
                .thenReturn(Optional.of("[{\"id\":\"n1\",\"stage\":\"1\"},{\"id\":\"n2\",\"stage\":\"2\"}]".getBytes()));
        when(assets.list(8L, "1.0.0")).thenReturn(List.of());

        var filtered = adapter.loadStage("demo", "1.0.0", "1", List.of("1"));
        assertTrue(filtered.nodesJson().contains("n1"));
        assertFalse(filtered.nodesJson().contains("n2"));
        assertFalse(filtered.gated());

        when(storage.get(eq(StorageScope.CDN), eq("works/8/1.0.0/cdn/content/nodes.json")))
                .thenReturn(Optional.of("not-json".getBytes()));
        var raw = adapter.loadStage("demo", "1.0.0", "1", List.of("1"));
        assertEquals("not-json", raw.nodesJson());
    }

    @Test
    void signAssetAllowsCdnKey() {
        doReturn(List.of(new PublishedWork(8L, "demo", "1.0.0", "c-1", "A", "A", "c-1")))
                .when(jdbc).query(eq(JdbcWorkRuntimeAdapter.REQUIRE_SQL), ArgumentMatchers.<RowMapper<?>>any(), eq("demo"));
        when(assetProvider.getIfAvailable()).thenReturn(assets);
        Instant expires = Instant.parse("2026-09-22T01:00:00Z");
        when(assets.sign(8L, "1.0.0", "cover")).thenReturn(
                new SignedUrl("https://cdn.example/cover.png?sig=short", expires, "works/8/1.0.0/cdn/cover.png", StorageScope.CDN));
        var signed = adapter.signAsset("demo", "1.0.0", "cover", "1");
        assertEquals("cover", signed.assetKey());
        assertEquals("https://cdn.example/cover.png?sig=short", signed.url());
        assertEquals(expires, signed.expiresAt());
    }
}
