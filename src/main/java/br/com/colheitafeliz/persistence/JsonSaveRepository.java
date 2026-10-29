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
    private final Replacement replacement;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public JsonSaveRepository(Path file) { this(file, JsonSaveRepository::replace); }

    @FunctionalInterface interface Replacement { void move(Path source, Path target) throws IOException; }
    JsonSaveRepository(Path file, Replacement replacement) { this.file = file; this.replacement = replacement; }

    @Override public void save(GameSnapshot snapshot, Instant gameTime, Instant realTime) throws IOException {
        // A rota nova nunca substitui um save legado ou um arquivo que falhou na leitura.
        if (Files.exists(file)) load();
        Path absolute = file.toAbsolutePath();
        Files.createDirectories(absolute.getParent());
        Path temporary = Files.createTempFile(absolute.getParent(), absolute.getFileName() + ".save-", ".tmp");
        GameSave save = new GameSave(GameSave.VERSION, GameSave.RULESET, realTime.toString(), gameTime.toString(), snapshot);
        try {
            Files.writeString(temporary, gson.toJson(save), StandardCharsets.UTF_8);
            replacement.move(temporary, absolute);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void replace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Override public Optional<LoadedGame> load() throws IOException {
        if (!Files.exists(file)) return Optional.empty();
        try {
            JsonObject data = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            int version = data.get("version").getAsInt();
            if ((version != GameSave.VERSION && version != 19) || !GameSave.RULESET.equals(data.get("ruleset").getAsString()))
                throw new IOException("Incompatible save; select a new path for the new progression: " + file);
            if (version == GameSave.VERSION) {
                JsonObject snapshot = data.getAsJsonObject("snapshot");
                if (!snapshot.has("protection") || snapshot.get("protection").isJsonNull())
                    throw new IOException("Missing protection metadata: " + file);
                for (var tile : snapshot.getAsJsonArray("tiles"))
                    if (!tile.getAsJsonObject().has("harvestRevision") || tile.getAsJsonObject().get("harvestRevision").isJsonNull())
                        throw new IOException("Missing tile revision: " + file);
            }
            GameSave save = gson.fromJson(data, GameSave.class);
            if (save.snapshot() == null) throw new IOException("Incomplete save file: " + file);
            return Optional.of(new LoadedGame(save.snapshot(), Instant.parse(save.savedAtReal()), Instant.parse(save.gameTime())));
        } catch (RuntimeException e) {
            throw new IOException("Invalid save file: " + file, e);
        }
    }
}
