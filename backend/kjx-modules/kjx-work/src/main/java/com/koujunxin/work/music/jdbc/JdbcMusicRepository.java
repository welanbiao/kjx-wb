package com.koujunxin.work.music.jdbc;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;

import com.koujunxin.work.music.MusicPackRecord;
import com.koujunxin.work.music.MusicRepository;
import com.koujunxin.work.music.MusicTrackRecord;
import com.koujunxin.work.music.WorkMusicBindingRecord;

public final class JdbcMusicRepository implements MusicRepository {

    private final JdbcTemplate jdbc;

    public JdbcMusicRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public MusicTrackRecord upsertTrack(MusicTrackRecord record) {
        jdbc.update("""
                INSERT INTO kjx_music_track (
                  track_code, title, artist, album, duration_sec, format, bitrate, mood_tags, scene_tags,
                  license, volume_default, fade_in_ms, fade_out_ms, watermark_id, storage_key, sha256, size_bytes,
                  creator, updater
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                ON DUPLICATE KEY UPDATE title = VALUES(title), artist = VALUES(artist), album = VALUES(album),
                  duration_sec = VALUES(duration_sec), format = VALUES(format), bitrate = VALUES(bitrate),
                  mood_tags = VALUES(mood_tags), scene_tags = VALUES(scene_tags), license = VALUES(license),
                  volume_default = VALUES(volume_default), fade_in_ms = VALUES(fade_in_ms),
                  fade_out_ms = VALUES(fade_out_ms), watermark_id = VALUES(watermark_id),
                  storage_key = VALUES(storage_key), sha256 = VALUES(sha256), size_bytes = VALUES(size_bytes),
                  updater = VALUES(updater)
                """,
                record.trackCode(), record.title(), record.artist(), record.album(), record.durationSec(),
                record.format(), record.bitrate(), record.moodTags(), record.sceneTags(), record.license(),
                record.volumeDefault(), record.fadeInMs(), record.fadeOutMs(), record.watermarkId(),
                record.storageKey(), record.sha256(), record.sizeBytes(),
                record.creator() == null ? "" : record.creator(),
                record.creator() == null ? "" : record.creator());
        return findTrack(record.trackCode()).orElseThrow();
    }

    @Override
    public Optional<MusicTrackRecord> findTrack(String trackCode) {
        List<MusicTrackRecord> rows = jdbc.query("""
                SELECT id, track_code, title, artist, album, duration_sec, format, bitrate, mood_tags, scene_tags,
                       license, volume_default, fade_in_ms, fade_out_ms, watermark_id, storage_key, sha256, size_bytes,
                       creator, create_time
                  FROM kjx_music_track WHERE track_code = ?
                """, this::mapTrack, trackCode);
        return rows.stream().findFirst();
    }

    @Override
    public List<MusicTrackRecord> listTracks() {
        return jdbc.query("""
                SELECT id, track_code, title, artist, album, duration_sec, format, bitrate, mood_tags, scene_tags,
                       license, volume_default, fade_in_ms, fade_out_ms, watermark_id, storage_key, sha256, size_bytes,
                       creator, create_time
                  FROM kjx_music_track ORDER BY id
                """, this::mapTrack);
    }

    @Override
    public MusicPackRecord upsertPack(MusicPackRecord record) {
        jdbc.update("""
                INSERT INTO kjx_music_pack (pack_code, name, summary, creator)
                VALUES (?,?,?,?)
                ON DUPLICATE KEY UPDATE name = VALUES(name), summary = VALUES(summary)
                """,
                record.packCode(), record.name(), record.summary(), record.creator() == null ? "" : record.creator());
        if (record.trackCodes() != null) {
            bindTracks(record.packCode(), record.trackCodes());
        }
        return findPack(record.packCode()).orElseThrow();
    }

    @Override
    public Optional<MusicPackRecord> findPack(String packCode) {
        List<MusicPackRecord> packs = jdbc.query("""
                SELECT id, pack_code, name, summary, creator, create_time FROM kjx_music_pack WHERE pack_code = ?
                """, this::mapPack, packCode);
        return packs.stream().findFirst().map(pack -> new MusicPackRecord(
                pack.id(), pack.packCode(), pack.name(), pack.summary(), pack.creator(), pack.createTime(),
                listPackTracks(pack.id())));
    }

    @Override
    public void bindTracks(String packCode, List<String> trackCodes) {
        MusicPackRecord pack = findPack(packCode).orElseThrow();
        jdbc.update("DELETE FROM kjx_music_pack_track WHERE pack_id = ?", pack.id());
        if (trackCodes == null) {
            return;
        }
        for (String code : trackCodes) {
            findTrack(code).ifPresent(track ->
                    jdbc.update("INSERT IGNORE INTO kjx_music_pack_track (pack_id, track_id) VALUES (?,?)",
                            pack.id(), track.id()));
        }
    }

    @Override
    public WorkMusicBindingRecord upsertBinding(WorkMusicBindingRecord record) {
        jdbc.update("""
                INSERT INTO kjx_work_music_binding (work_id, version, scene_key, rule_json, creator)
                VALUES (?,?,?,CAST(? AS JSON),?)
                ON DUPLICATE KEY UPDATE rule_json = CAST(VALUES(rule_json) AS JSON)
                """,
                record.workId(), record.version(), record.sceneKey(), record.ruleJson(),
                record.creator() == null ? "" : record.creator());
        List<WorkMusicBindingRecord> rows = jdbc.query("""
                SELECT id, work_id, version, scene_key, CAST(rule_json AS CHAR) AS rule_json, creator, create_time
                  FROM kjx_work_music_binding WHERE work_id = ? AND version = ? AND scene_key = ?
                """, this::mapBinding, record.workId(), record.version(), record.sceneKey());
        return rows.get(0);
    }

    @Override
    public List<WorkMusicBindingRecord> listBindings(long workId, String version) {
        return jdbc.query("""
                SELECT id, work_id, version, scene_key, CAST(rule_json AS CHAR) AS rule_json, creator, create_time
                  FROM kjx_work_music_binding WHERE work_id = ? AND version = ?
                """, this::mapBinding, workId, version);
    }

    private List<String> listPackTracks(long packId) {
        return jdbc.query("""
                SELECT t.track_code FROM kjx_music_pack_track pt
                  JOIN kjx_music_track t ON t.id = pt.track_id
                 WHERE pt.pack_id = ?
                """, (rs, i) -> rs.getString(1), packId);
    }

    private MusicTrackRecord mapTrack(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        Timestamp created = rs.getTimestamp("create_time");
        Integer bitrate = rs.getObject("bitrate") == null ? null : rs.getInt("bitrate");
        Long size = rs.getObject("size_bytes") == null ? null : rs.getLong("size_bytes");
        return new MusicTrackRecord(
                rs.getLong("id"), rs.getString("track_code"), rs.getString("title"), rs.getString("artist"),
                rs.getString("album"), rs.getInt("duration_sec"), rs.getString("format"), bitrate,
                rs.getString("mood_tags"), rs.getString("scene_tags"), rs.getString("license"),
                rs.getInt("volume_default"), rs.getInt("fade_in_ms"), rs.getInt("fade_out_ms"),
                rs.getString("watermark_id"), rs.getString("storage_key"), rs.getString("sha256"), size,
                rs.getString("creator"), created == null ? null : created.toInstant());
    }

    private MusicPackRecord mapPack(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        Timestamp created = rs.getTimestamp("create_time");
        return new MusicPackRecord(
                rs.getLong("id"), rs.getString("pack_code"), rs.getString("name"), rs.getString("summary"),
                rs.getString("creator"), created == null ? null : created.toInstant(), List.of());
    }

    private WorkMusicBindingRecord mapBinding(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        Timestamp created = rs.getTimestamp("create_time");
        return new WorkMusicBindingRecord(
                rs.getLong("id"), rs.getLong("work_id"), rs.getString("version"), rs.getString("scene_key"),
                rs.getString("rule_json"), rs.getString("creator"), created == null ? null : created.toInstant());
    }
}
