package com.koujunxin.work.asset;

import java.util.List;
import java.util.Optional;

public interface AssetRepository {

    AssetRecord upsert(AssetRecord record);

    Optional<AssetRecord> findByPath(long workId, String version, String path);

    Optional<AssetRecord> findByKey(long workId, String version, String assetKey);

    List<AssetRecord> list(long workId, String version);
}
