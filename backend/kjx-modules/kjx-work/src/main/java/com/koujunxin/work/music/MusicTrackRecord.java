package com.koujunxin.work.music;

import java.time.Instant;

public record MusicTrackRecord(
        Long id,
        String trackCode,
        String title,
        String artist,
        String album,
        int durationSec,
        String format,
        Integer bitrate,
        String moodTags,
        String sceneTags,
        String license,
        int volumeDefault,
        int fadeInMs,
        int fadeOutMs,
        String watermarkId,
        String storageKey,
        String sha256,
        Long sizeBytes,
        String creator,
        Instant createTime
) {
}
