package com.koujunxin.work.music;

import java.util.List;
import java.util.Optional;

public interface MusicRepository {

    MusicTrackRecord upsertTrack(MusicTrackRecord record);

    Optional<MusicTrackRecord> findTrack(String trackCode);

    List<MusicTrackRecord> listTracks();

    MusicPackRecord upsertPack(MusicPackRecord record);

    Optional<MusicPackRecord> findPack(String packCode);

    void bindTracks(String packCode, List<String> trackCodes);

    WorkMusicBindingRecord upsertBinding(WorkMusicBindingRecord record);

    List<WorkMusicBindingRecord> listBindings(long workId, String version);
}
