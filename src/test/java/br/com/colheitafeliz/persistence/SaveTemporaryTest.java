package br.com.colheitafeliz.persistence;

import br.com.colheitafeliz.game.CropCatalogLoader;
import br.com.colheitafeliz.game.GameSession;
import br.com.colheitafeliz.testsupport.ManualClock;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SaveTemporaryTest {
    @TempDir Path directory;

    @Test void failedReplacementPreservesOriginalBytesAndCleansOwnTemporary() throws IOException {
        Path file = directory.resolve("farm.json");
        var clock = new ManualClock();
        var catalog = CropCatalogLoader.load();
        GameSession.open(clock, catalog, new JsonSaveRepository(file), bound -> 99).save();
        byte[] original = Files.readAllBytes(file);
        var broken = new JsonSaveRepository(file, (source, target) -> { throw new IOException("simulated replacement failure"); });
        var session = GameSession.open(clock, catalog, broken, bound -> 99);
        session.state().wallet().credit(50);
        assertFalse(session.trySave());
        assertArrayEquals(original, Files.readAllBytes(file));
        try (var paths = Files.list(directory)) { assertEquals(1, paths.count()); }
    }

    @Test void abandonedTemporaryIsIgnoredAndNeverOverwritten() throws IOException {
        Path file = directory.resolve("farm.json"), oldTemporary = directory.resolve("farm.json.tmp");
        Files.createDirectory(oldTemporary);
        Files.writeString(oldTemporary.resolve("evidence"), "preserve");
        var session = GameSession.open(new ManualClock(), CropCatalogLoader.load(), new JsonSaveRepository(file), bound -> 99);
        session.save();
        assertTrue(new JsonSaveRepository(file).load().isPresent());
        assertEquals("preserve", Files.readString(oldTemporary.resolve("evidence")));
        try (var paths = Files.list(directory)) {
            assertEquals(2, paths.count(), "Only the save and the abandoned temporary remain");
        }
    }
}
