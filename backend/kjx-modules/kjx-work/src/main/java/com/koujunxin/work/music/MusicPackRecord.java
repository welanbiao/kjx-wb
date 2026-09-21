package com.koujunxin.work.music;

import java.time.Instant;
import java.util.List;

public record MusicPackRecord(
        Long id,
        String packCode,
        String name,
        String summary,
        String creator,
        Instant createTime,
        List<String> trackCodes
) {
}
