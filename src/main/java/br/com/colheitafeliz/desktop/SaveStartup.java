package br.com.colheitafeliz.desktop;

import br.com.colheitafeliz.domain.crop.CropCatalog;
import br.com.colheitafeliz.game.GameSession;
import br.com.colheitafeliz.persistence.JsonSaveRepository;
import br.com.colheitafeliz.persistence.SaveFileLock;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;

final class SaveStartup {
    private SaveStartup() {}

    @FunctionalInterface interface Recovery { Path choose(Path failed, IOException error); }

    record RunningGame(Path path, GameSession session, SaveFileLock lock) implements AutoCloseable {
        @Override public void close() throws IOException { lock.close(); }
    }

    static RunningGame open(Path initial, Clock clock, CropCatalog catalog, Recovery recovery) {
        Path path = initial.toAbsolutePath().normalize();
        boolean newOnly = false;
        while (true) {
            SaveFileLock lock = null;
            try {
                if (Files.exists(path)) path = path.toRealPath();
                lock = SaveFileLock.acquire(path);
                if (newOnly && Files.exists(path)) throw new IOException("Escolha um arquivo que ainda não existe; o arquivo selecionado foi preservado.");
                GameSession session = GameSession.open(clock, catalog, new JsonSaveRepository(path));
                return new RunningGame(path, session, lock);
            } catch (IOException e) {
                if (lock != null) try { lock.close(); } catch (IOException closeFailure) { e.addSuppressed(closeFailure); }
                Path selected = recovery.choose(path, e);
                if (selected == null) return null;
                path = selected.toAbsolutePath().normalize();
                newOnly = true;
            }
        }
    }
}
