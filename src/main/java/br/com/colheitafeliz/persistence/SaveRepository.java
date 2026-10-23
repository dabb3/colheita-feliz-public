package br.com.colheitafeliz.persistence;

import br.com.colheitafeliz.game.GameSnapshot;
import java.io.IOException;
import java.time.Instant;
import java.util.Optional;

public interface SaveRepository {
    void save(GameSnapshot snapshot, Instant gameTime, Instant realTime) throws IOException;
    Optional<LoadedGame> load() throws IOException;
}
