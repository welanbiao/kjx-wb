package com.koujunxin.work.api;

import java.util.Optional;

/**
 * 作品只读查询，供 WP-13/WP-20/Runtime 使用。
 */
public interface WorkCatalogQueryPort {

    Optional<WorkSnapshot> findByCode(String workCode);

    Optional<WorkSnapshot> findById(long workId);
}
