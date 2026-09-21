package com.koujunxin.work.packagecontent;

import java.util.List;
import java.util.Locale;

/**
 * {@code server/} 下任何文件不得进入 CDN 产物集合。
 */
public final class CdnIsolation {

    private CdnIsolation() {
    }

    public static boolean isServerPath(String relative) {
        if (relative == null) {
            return false;
        }
        String path = relative.replace('\\', '/').toLowerCase(Locale.ROOT);
        return path.equals("server") || path.startsWith("server/");
    }

    public static List<String> cdnFiles(List<String> files) {
        return files.stream().filter(path -> !isServerPath(path)).toList();
    }

    public static List<String> serverFiles(List<String> files) {
        return files.stream().filter(CdnIsolation::isServerPath).toList();
    }

    public static void rejectCdnKey(String objectKey) {
        if (objectKey == null) {
            return;
        }
        String key = objectKey.replace('\\', '/').toLowerCase(Locale.ROOT);
        if (key.contains("/server/") || key.startsWith("server/") || key.contains("/scope-server/")) {
            throw WorkPackageException.badRequest("SERVER_NOT_ON_CDN",
                    "server/ artifacts cannot be fetched via CDN: " + objectKey);
        }
    }
}
