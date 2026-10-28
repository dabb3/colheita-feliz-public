package br.com.colheitafeliz.persistence;

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

public final class JsonSaveRepository implements SaveRepository {
    private final Path file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public JsonSaveRepository(Path file) { this.file = file; }

    @Override public void save(GameSnapshot snapshot, Instant gameTime, Instant realTime) throws IOException {
        // A rota nova nunca substitui um save legado ou um arquivo que falhou na leitura.
        if (Files.exists(file)) load();
        Path absolute = file.toAbsolutePath();
        Files.createDirectories(absolute.getParent());
        Path temporary = absolute.resolveSibling(absolute.getFileName() + ".tmp");
        GameSave save = new GameSave(GameSave.VERSION, GameSave.RULESET, realTime.toString(), gameTime.toString(), snapshot);
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
            if ((version != GameSave.VERSION && version != GameSave.STAGE_2_VERSION)
                    || !GameSave.RULESET.equals(data.get("ruleset").getAsString()))
                throw new IOException("Incompatible save; select a new path for the new economy: " + file);
            // O adubo agregado da etapa 2 não é convertido (seção 10); com estoque, recusa em vez de zerá-lo no próximo save.
            if (version == GameSave.STAGE_2_VERSION && data.has("snapshot") && data.get("snapshot").isJsonObject()
                    && data.getAsJsonObject("snapshot").has("fertilizer")
                    && data.getAsJsonObject("snapshot").get("fertilizer").getAsInt() > 0)
                throw new IOException("Incompatible save; it holds stage 2 fertilizer stock that would be lost, select a new path: " + file);
            GameSave save = gson.fromJson(data, GameSave.class);
            if (save.snapshot() == null) throw new IOException("Incomplete save file: " + file);
            return Optional.of(new LoadedGame(save.snapshot(), Instant.parse(save.savedAtReal()), Instant.parse(save.gameTime())));
        } catch (RuntimeException e) {
            throw new IOException("Invalid save file: " + file, e);
        }
    }
}
