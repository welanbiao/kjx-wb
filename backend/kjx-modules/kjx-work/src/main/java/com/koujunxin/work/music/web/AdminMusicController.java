package com.koujunxin.work.music.web;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.koujunxin.work.asset.SignedUrl;
import com.koujunxin.work.music.MusicPackRecord;
import com.koujunxin.work.music.MusicService;
import com.koujunxin.work.music.MusicTrackRecord;
import com.koujunxin.work.web.ApiEnvelope;

@RestController
@RequestMapping("/admin-api/kjx/music")
@ConditionalOnBean(MusicService.class)
public class AdminMusicController {

    private final MusicService music;

    public AdminMusicController(MusicService music) {
        this.music = music;
    }

    @GetMapping("/tracks")
    public ApiEnvelope<List<MusicTrackRecord>> tracks(
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        return ApiEnvelope.ok(requestId, music.listTracks());
    }

    @PostMapping("/tracks")
    public ApiEnvelope<MusicTrackRecord> createTrack(
            @RequestBody TrackBody body,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        MusicTrackRecord created = music.createTrack(new MusicTrackRecord(
                null, body.trackCode(), body.title(), body.artist(), body.album(),
                body.durationSec() == null ? 0 : body.durationSec(), body.format(), body.bitrate(),
                body.moodTags(), body.sceneTags(), body.license(),
                body.volumeDefault() == null ? 100 : body.volumeDefault(),
                body.fadeInMs() == null ? 800 : body.fadeInMs(),
                body.fadeOutMs() == null ? 800 : body.fadeOutMs(),
                body.watermarkId(), null, null, null, "admin", Instant.now()), null);
        return ApiEnvelope.ok(requestId, created);
    }

    @PostMapping("/packs")
    public ApiEnvelope<MusicPackRecord> createPack(
            @RequestBody PackBody body,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        return ApiEnvelope.ok(requestId, music.createPack(new MusicPackRecord(
                null, body.packCode(), body.name(), body.summary(), "admin", Instant.now(), body.trackCodes())));
    }

    @PostMapping("/tracks/{trackCode}/sign")
    public ApiEnvelope<Map<String, Object>> sign(
            @PathVariable String trackCode,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        SignedUrl signed = music.signTrack(trackCode);
        return ApiEnvelope.ok(requestId, Map.of(
                "objectKey", signed.objectKey(),
                "expiresAt", signed.expiresAt().toString(),
                "ttlSeconds", Duration.ofMinutes(10).toSeconds(),
                "url", signed.url()));
    }

    public record TrackBody(
            String trackCode,
            String title,
            String artist,
            String album,
            Integer durationSec,
            String format,
            Integer bitrate,
            String moodTags,
            String sceneTags,
            String license,
            Integer volumeDefault,
            Integer fadeInMs,
            Integer fadeOutMs,
            String watermarkId
    ) {
    }

    public record PackBody(String packCode, String name, String summary, List<String> trackCodes) {
    }
}
