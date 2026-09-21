package com.koujunxin.work.music;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.koujunxin.work.asset.SignedUrl;
import com.koujunxin.work.catalog.WorkRecord;
import com.koujunxin.work.packagecontent.FormAPackageFactory;
import com.koujunxin.work.packagecontent.PackageHarness;
import com.koujunxin.work.packagecontent.WorkPackageException;

class MusicServiceTest {

    @Test
    void packageIngestsBindingsWithoutBgAudioAndCanSignLibraryTrack() throws Exception {
        PackageHarness harness = new PackageHarness();
        WorkRecord work = harness.work("music-demo");
        harness.packages.upload(work.id(), "1.0.0",
                new ByteArrayInputStream(FormAPackageFactory.sampleZip("music-demo", "1.0.0")), "op");
        List<WorkMusicBindingRecord> bindings = harness.music.bindings(work.id(), "1.0.0");
        assertEquals(1, bindings.size());
        assertEquals("intro", bindings.get(0).sceneKey());
        assertTrue(bindings.get(0).ruleJson().contains("track_001"));

        MusicTrackRecord track = harness.music.createTrack(new MusicTrackRecord(
                null, "track_001", "Rain", "Studio", "Demo", 120, "mp3", 192, "calm", "intro",
                "in-house", 80, 800, 800, "wm-1", null, null, null, "op", Instant.now()),
                new byte[] {1, 2, 3, 4});
        SignedUrl signed = harness.music.signTrack(track.trackCode());
        assertTrue(signed.url().startsWith("local://cdn/"));
        assertFalse(signed.toString().contains("&s="));

        assertThrows(WorkPackageException.class, () -> harness.music.createTrack(new MusicTrackRecord(
                null, "no-license", "X", null, null, 1, "mp3", null, null, null, " ",
                100, 800, 800, null, null, null, null, "op", Instant.now()), null));
    }
}
