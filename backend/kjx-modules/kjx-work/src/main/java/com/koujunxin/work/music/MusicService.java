package com.koujunxin.work.music;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.koujunxin.work.asset.ObjectStoragePort;
import com.koujunxin.work.asset.SignedUrl;
import com.koujunxin.work.asset.StorageScope;
import com.koujunxin.work.packagecontent.IsolatedZipExtractor;
import com.koujunxin.work.packagecontent.ManifestDocument;
import com.koujunxin.work.packagecontent.WorkPackageException;

public final class MusicService {

    public static final Duration DEFAULT_TTL = Duration.ofMinutes(10);

    private final MusicRepository music;
    private final ObjectStoragePort storage;
    private final ObjectMapper mapper;

    public MusicService(MusicRepository music, ObjectStoragePort storage, ObjectMapper mapper) {
        this.music = music;
        this.storage = storage;
        this.mapper = mapper;
    }

    public MusicTrackRecord createTrack(MusicTrackRecord record, byte[] audio) {
        if (record.license() == null || record.license().isBlank()) {
            throw WorkPackageException.badRequest("MUSIC_LICENSE_REQUIRED", "license is required");
        }
        String storageKey = record.storageKey();
        String sha = record.sha256();
        Long size = record.sizeBytes();
        if (audio != null) {
            storageKey = "music/" + record.trackCode();
            var meta = storage.put(StorageScope.CDN, storageKey, audio, "audio/mpeg", null, null);
            sha = meta.sha256();
            size = meta.sizeBytes();
        }
        return music.upsertTrack(new MusicTrackRecord(
                record.id(), record.trackCode(), record.title(), record.artist(), record.album(),
                record.durationSec(), record.format(), record.bitrate(), record.moodTags(), record.sceneTags(),
                record.license(), record.volumeDefault(), record.fadeInMs(), record.fadeOutMs(),
                record.watermarkId(), storageKey, sha, size, record.creator(), Instant.now()));
    }

    public SignedUrl signTrack(String trackCode) {
        MusicTrackRecord track = music.findTrack(trackCode)
                .orElseThrow(() -> WorkPackageException.notFound("track not found: " + trackCode));
        if (track.storageKey() == null || track.storageKey().isBlank()) {
            throw WorkPackageException.notFound("track has no audio: " + trackCode);
        }
        return storage.sign(StorageScope.CDN, track.storageKey(), DEFAULT_TTL);
    }

    public void ingestBindings(long workId, String version, Path root, ManifestDocument manifest, String actor)
            throws IOException {
        String musicPath = manifest == null || manifest.music() == null || manifest.music().isBlank()
                ? "theme/music.json"
                : manifest.music();
        Path file = IsolatedZipExtractor.safeResolve(root, musicPath);
        if (!Files.isRegularFile(file)) {
            return;
        }
        JsonNode node = mapper.readTree(Files.readString(file, StandardCharsets.UTF_8));
        JsonNode scenes = node.has("scenes") ? node.get("scenes") : node;
        if (scenes == null || !scenes.isObject()) {
            return;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = scenes.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            music.upsertBinding(new WorkMusicBindingRecord(
                    null, workId, version, field.getKey(), field.getValue().toString(), actor, Instant.now()));
        }
    }

    public List<WorkMusicBindingRecord> bindings(long workId, String version) {
        return music.listBindings(workId, version);
    }

    public MusicPackRecord createPack(MusicPackRecord record) {
        return music.upsertPack(record);
    }

    public List<MusicTrackRecord> listTracks() {
        return music.listTracks();
    }
}
