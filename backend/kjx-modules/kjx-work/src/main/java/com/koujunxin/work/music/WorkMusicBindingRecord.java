package com.koujunxin.work.music;

import java.time.Instant;

public record WorkMusicBindingRecord(
        Long id,
        long workId,
        String version,
        String sceneKey,
        String ruleJson,
        String creator,
        Instant createTime
) {
}
