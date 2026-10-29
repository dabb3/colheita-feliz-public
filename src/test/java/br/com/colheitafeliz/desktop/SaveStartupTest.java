package br.com.colheitafeliz.desktop;

import br.com.colheitafeliz.game.CropCatalogLoader;
import br.com.colheitafeliz.persistence.JsonSaveRepository;
import br.com.colheitafeliz.persistence.SaveFileLock;
import br.com.colheitafeliz.testsupport.ManualClock;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SaveStartupTest {
    @TempDir Path directory;

    @Test void overflowingOfflineTimeUsesRecoveryAndReleasesLock() throws IOException {
        Path file = directory.resolve("overflow.json");
        var clock = new ManualClock();
        br.com.colheitafeliz.game.GameSession.open(clock, CropCatalogLoader.load(), new JsonSaveRepository(file)).save();
        var json = com.google.gson.JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        json.addProperty("gameTime", java.time.Instant.MAX.toString());
        json.addProperty("savedAtReal", clock.instant().minusSeconds(1).toString());
        Files.writeString(file, json.toString());
        byte[] original = Files.readAllBytes(file);
        assertNull(SaveStartup.open(file, clock, CropCatalogLoader.load(), (path, error) -> null));
        assertArrayEquals(original, Files.readAllBytes(file));
        try (var lock = SaveFileLock.acquire(file)) { assertNotNull(lock); }
    }

    @Test void diskRootIsNotAValidSaveFile() {
        Path root = directory.toAbsolutePath().getRoot();
        assertThrows(IOException.class, () -> SaveFileLock.acquire(root));
        Path suggested = SaveRecoveryDialog.newFarmPath(root);
        assertEquals("nova-fazenda.json", suggested.getFileName().toString());
        assertEquals(Path.of(System.getProperty("user.home")), suggested.getParent());
    }

    @Test void lockIsAlsoEnforcedAcrossSeparateJavaProcesses() throws Exception {
        Path file = directory.resolve("process.json");
        String mainClasses = Path.of(SaveFileLock.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        String testClasses = Path.of(SaveStartupTest.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        try (var lock = SaveFileLock.acquire(file)) {
            assertNotNull(lock);
            Process other = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    "-cp", mainClasses + java.io.File.pathSeparator + testClasses,
                    LockProbe.class.getName(), file.toString()).redirectErrorStream(true).start();
            try {
                assertTrue(other.waitFor(15, java.util.concurrent.TimeUnit.SECONDS));
                assertEquals(2, other.exitValue(), new String(other.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            } finally { if (other.isAlive()) other.destroyForcibly(); }
        }
    }

    public static final class LockProbe {
        private LockProbe() {}
        public static void main(String[] args) throws IOException {
            try (var lock = SaveFileLock.acquire(Path.of(args[0]))) {
                System.out.println(lock.getClass().getSimpleName());
            } catch (IOException e) { System.exit(2); }
        }
    }

    @Test void cancelPreservesInvalidFileAndReleasesLock() throws IOException {
        Path broken = directory.resolve("broken.json");
        Files.writeString(broken, "invalid original");
        assertNull(SaveStartup.open(broken, new ManualClock(), CropCatalogLoader.load(), (path, error) -> null));
        assertEquals("invalid original", Files.readString(broken));
        try (var lock = SaveFileLock.acquire(broken)) { assertNotNull(lock); }
    }

    @Test void recoveryUsesOnlyNewPathAndPreservesExistingFarms() throws IOException {
        Path broken = directory.resolve("broken.json"), occupied = directory.resolve("occupied.json");
        Path fresh = directory.resolve("new farm.json");
        Files.writeString(broken, "original");
        Files.writeString(occupied, "do not overwrite");
        var choices = new java.util.ArrayDeque<>(java.util.List.of(occupied, fresh));
        try (var running = SaveStartup.open(broken, new ManualClock(), CropCatalogLoader.load(),
                (path, error) -> choices.removeFirst())) {
            assertTrue(choices.isEmpty());
            assertTrue(running.session().fresh());
            assertEquals(fresh.toAbsolutePath(), running.path());
            running.session().save();
            assertThrows(IOException.class, () -> SaveFileLock.acquire(fresh));
        }
        assertTrue(new JsonSaveRepository(fresh).load().isPresent());
        assertEquals("original", Files.readString(broken));
        assertEquals("do not overwrite", Files.readString(occupied));
        try (var lock = SaveFileLock.acquire(fresh)) { assertNotNull(lock); }
    }

    @Test void secondInstanceCannotOpenSameFarmButOtherPathWorks() throws IOException {
        Path file = directory.resolve("farm.json");
        try (var first = SaveStartup.open(file, new ManualClock(), CropCatalogLoader.load(), (path, error) -> null)) {
            assertNotNull(first);
            assertNull(SaveStartup.open(file, new ManualClock(), CropCatalogLoader.load(), (path, error) -> null));
            try (var other = SaveStartup.open(directory.resolve("other.json"), new ManualClock(),
                    CropCatalogLoader.load(), (path, error) -> null)) { assertNotNull(other); }
        }
    }
}
