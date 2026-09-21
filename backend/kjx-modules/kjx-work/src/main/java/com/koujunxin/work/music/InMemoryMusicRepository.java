package com.koujunxin.work.music;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class InMemoryMusicRepository implements MusicRepository {

    private final AtomicLong trackIds = new AtomicLong(1);
    private final AtomicLong packIds = new AtomicLong(1);
    private final AtomicLong bindingIds = new AtomicLong(1);
    private final Map<String, MusicTrackRecord> tracks = new ConcurrentHashMap<>();
    private final Map<String, MusicPackRecord> packs = new ConcurrentHashMap<>();
    private final Map<String, WorkMusicBindingRecord> bindings = new ConcurrentHashMap<>();

    @Override
    public MusicTrackRecord upsertTrack(MusicTrackRecord record) {
        MusicTrackRecord existing = tracks.get(record.trackCode());
        long id = existing == null ? trackIds.getAndIncrement() : existing.id();
        MusicTrackRecord saved = new MusicTrackRecord(
                id, record.trackCode(), record.title(), record.artist(), record.album(), record.durationSec(),
                record.format(), record.bitrate(), record.moodTags(), record.sceneTags(), record.license(),
                record.volumeDefault(), record.fadeInMs(), record.fadeOutMs(), record.watermarkId(),
                record.storageKey(), record.sha256(), record.sizeBytes(), record.creator(),
                record.createTime() == null ? Instant.now() : record.createTime());
        tracks.put(record.trackCode(), saved);
        return saved;
    }

    @Override
    public Optional<MusicTrackRecord> findTrack(String trackCode) {
        return Optional.ofNullable(tracks.get(trackCode));
    }

    @Override
    public List<MusicTrackRecord> listTracks() {
        return new ArrayList<>(tracks.values());
    }

    @Override
    public MusicPackRecord upsertPack(MusicPackRecord record) {
        MusicPackRecord existing = packs.get(record.packCode());
        long id = existing == null ? packIds.getAndIncrement() : existing.id();
        List<String> codes = record.trackCodes() == null ? List.of() : List.copyOf(record.trackCodes());
        MusicPackRecord saved = new MusicPackRecord(
                id, record.packCode(), record.name(), record.summary(), record.creator(),
                record.createTime() == null ? Instant.now() : record.createTime(), codes);
        packs.put(record.packCode(), saved);
        return saved;
    }

    @Override
    public Optional<MusicPackRecord> findPack(String packCode) {
        return Optional.ofNullable(packs.get(packCode));
    }

    @Override
    public void bindTracks(String packCode, List<String> trackCodes) {
        MusicPackRecord pack = packs.get(packCode);
        if (pack == null) {
            return;
        }
        packs.put(packCode, new MusicPackRecord(
                pack.id(), pack.packCode(), pack.name(), pack.summary(), pack.creator(), pack.createTime(),
                trackCodes == null ? List.of() : List.copyOf(trackCodes)));
    }

    @Override
    public WorkMusicBindingRecord upsertBinding(WorkMusicBindingRecord record) {
        String key = record.workId() + ":" + record.version() + ":" + record.sceneKey();
        WorkMusicBindingRecord existing = bindings.get(key);
        long id = existing == null ? bindingIds.getAndIncrement() : existing.id();
        WorkMusicBindingRecord saved = new WorkMusicBindingRecord(
                id, record.workId(), record.version(), record.sceneKey(), record.ruleJson(), record.creator(),
                record.createTime() == null ? Instant.now() : record.createTime());
        bindings.put(key, saved);
        return saved;
    }

    @Override
    public List<WorkMusicBindingRecord> listBindings(long workId, String version) {
        return bindings.values().stream()
                .filter(row -> row.workId() == workId && row.version().equals(version))
                .toList();
    }
}
