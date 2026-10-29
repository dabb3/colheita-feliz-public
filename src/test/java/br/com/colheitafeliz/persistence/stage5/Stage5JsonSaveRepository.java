package br.com.colheitafeliz.persistence.stage5;
import br.com.colheitafeliz.persistence.SaveRepository;
import br.com.colheitafeliz.persistence.LoadedGame;

import br.com.colheitafeliz.game.GameSnapshot;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Optional;

public final class Stage5JsonSaveRepository implements SaveRepository {
    private final Path file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public Stage5JsonSaveRepository(Path file) { this.file = file; }

    @Override public void save(GameSnapshot snapshot, Instant gameTime, Instant realTime) throws IOException {
        // A rota nova nunca substitui um save legado ou um arquivo que falhou na leitura.
        if (Files.exists(file)) load();
        Path absolute = file.toAbsolutePath();
        Files.createDirectories(java.util.Objects.requireNonNull(absolute.getParent()));
        Path temporary = absolute.resolveSibling(absolute.getFileName() + ".tmp");
        Stage5GameSave save = new Stage5GameSave(Stage5GameSave.VERSION, Stage5GameSave.RULESET, realTime.toString(), gameTime.toString(), snapshot);
        Files.writeString(temporary, gson.toJson(save), StandardCharsets.UTF_8);
        try {
            Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Override public Optional<LoadedGame> load() throws IOException {
        if (!Files.exists(file)) return Optional.empty();
        try {
            JsonObject data = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            int version = data.get("version").getAsInt();
            if (version != Stage5GameSave.VERSION || !Stage5GameSave.RULESET.equals(data.get("ruleset").getAsString()))
                throw new IOException("Incompatible save; select a new path for the new progression: " + file);
            Stage5GameSave save = gson.fromJson(data, Stage5GameSave.class);
            if (save.snapshot() == null) throw new IOException("Incomplete save file: " + file);
            return Optional.of(new LoadedGame(save.snapshot(), Instant.parse(save.savedAtReal()), Instant.parse(save.gameTime())));
        } catch (RuntimeException e) {
            throw new IOException("Invalid save file: " + file, e);
        }
    }
}
