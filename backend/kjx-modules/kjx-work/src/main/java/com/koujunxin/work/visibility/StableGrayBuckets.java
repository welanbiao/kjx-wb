package com.koujunxin.work.visibility;

import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

/**
 * 跨进程稳定的灰度分桶。同一 {@code accountId + workId} 永远落入同一 0-99 桶，
 * 禁止使用 {@code Math.random}、进程内缓存随机或 {@code Objects.hash}（跨 JVM 不稳定）。
 */
final class StableGrayBuckets {

    private StableGrayBuckets() {
    }

    static int bucket(long accountId, long workId) {
        CRC32 crc = new CRC32();
        crc.update((accountId + ":" + workId).getBytes(StandardCharsets.UTF_8));
        return (int) (crc.getValue() % 100L);
    }

    static boolean included(long accountId, long workId, int grayRatio) {
        if (grayRatio <= 0) {
            return false;
        }
        if (grayRatio >= 100) {
            return true;
        }
        return bucket(accountId, workId) < grayRatio;
    }
}
