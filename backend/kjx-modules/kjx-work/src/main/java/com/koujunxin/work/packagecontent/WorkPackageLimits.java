package com.koujunxin.work.packagecontent;

/**
 * 作品包硬性限制。对齐规格 §9.3。测试可注入更小上限。
 */
public record WorkPackageLimits(
        long maxZipBytes,
        int maxFiles,
        long maxUncompressedBytes,
        int maxCompressionRatio,
        long maxBitmapBytes,
        long maxSvgBytes,
        long maxSfxBytes,
        long maxFontBytes,
        long maxVideoBytes,
        String platformRuntimeVersion
) {
    public static final long DEFAULT_MAX_ZIP = 50L * 1024 * 1024;
    public static final int DEFAULT_MAX_FILES = 500;
    public static final long DEFAULT_MAX_UNCOMPRESSED = 250L * 1024 * 1024;
    public static final int DEFAULT_MAX_RATIO = 100;
    public static final String PLATFORM_RUNTIME = "1.0.0";

    public static WorkPackageLimits standard() {
        return new WorkPackageLimits(
                DEFAULT_MAX_ZIP,
                DEFAULT_MAX_FILES,
                DEFAULT_MAX_UNCOMPRESSED,
                DEFAULT_MAX_RATIO,
                800L * 1024,
                64L * 1024,
                2L * 1024 * 1024,
                500L * 1024,
                20L * 1024 * 1024,
                PLATFORM_RUNTIME);
    }
}
