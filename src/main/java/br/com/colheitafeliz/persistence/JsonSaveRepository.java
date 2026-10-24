package br.com.colheitafeliz.persistence;

import br.com.colheitafeliz.game.GameSnapshot;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
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
        Path absolute = file.toAbsolutePath();
        Files.createDirectories(absolute.getParent());
        Path temporary = absolute.resolveSibling(absolute.getFileName() + ".tmp");
        GameSave save = new GameSave(GameSave.VERSION, realTime.toString(), gameTime.toString(), snapshot);
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
            if (version == 1) migrateV1(data);
            else if (version == 2 || version == 3) migratePlots(data, 0);
            else if (version >= 4 && version <= 6) migratePlots(data, 6);
            else if (version == 7) migratePlots(data, 7);
            else if (version == 8) migratePlots(data, 5);
            if (version >= 1 && version <= 9) migrateGrowthAnchors(data);
            // v11 acrescentou insetos, v12 a planta morta e o tutorial, v13 os animais, v14 o adubo e
            // v15 a terra seca; todos têm padrão vazio.
            if (version >= 1 && version <= 14) data.addProperty("version", GameSave.VERSION);
            GameSave save = gson.fromJson(data, GameSave.class);
            if (save == null || save.version() != GameSave.VERSION || save.snapshot() == null)
                throw new IOException("Unsupported or incomplete save file");
            return Optional.of(new LoadedGame(save.snapshot(), Instant.parse(save.savedAtReal()),
                    Instant.parse(save.gameTime())));
        } catch (JsonParseException | IllegalArgumentException | IllegalStateException | NullPointerException e) {
            throw new IOException("Invalid save file: " + file, e);
        }
    }

    private static void migrateV1(JsonObject save) {
        JsonObject state = save.getAsJsonObject("snapshot");
        if (state == null) throw new IllegalArgumentException("Missing snapshot");
        state.addProperty("level", 1);
        state.addProperty("currentXp", 0);
        state.addProperty("musicEnabled", true);
        state.addProperty("effectsEnabled", true);
        com.google.gson.JsonArray unlocked = new com.google.gson.JsonArray();
        for (String id : new String[]{"nabo", "cenoura", "tomate", "milho", "morango"}) unlocked.add(id);
        state.add("unlockedCropIds", unlocked);
        state.add("unlockedPlotIds", migrationPlotIds(state, 0));
        save.addProperty("version", GameSave.VERSION);
    }

    private static void migratePlots(JsonObject save, int previousStartingPlots) {
        JsonObject state = save.getAsJsonObject("snapshot");
        if (state == null) throw new IllegalArgumentException("Missing snapshot");
        state.add("unlockedPlotIds", migrationPlotIds(state, previousStartingPlots));
        save.addProperty("version", GameSave.VERSION);
    }

    /**
     * Up to v9, growth was the time since {@code wateredAt}, shifted forward after weeds. Freeze that growth
     * at the save's game time and treat the migration as a fresh watering, so no crop loses progress.
     */
    private static void migrateGrowthAnchors(JsonObject save) {
        JsonObject state = save.getAsJsonObject("snapshot");
        if (state == null) throw new IllegalArgumentException("Missing snapshot");
        Instant gameTime = Instant.parse(save.get("gameTime").getAsString());
        com.google.gson.JsonArray tiles = state.getAsJsonArray("tiles");
        if (tiles != null) for (com.google.gson.JsonElement element : tiles) {
            JsonObject tile = element.getAsJsonObject();
            if (!tile.has("wateredAt") || tile.get("wateredAt").isJsonNull() || tile.has("growthAnchorAt")) continue;
            Instant wateredAt = Instant.parse(tile.get("wateredAt").getAsString());
            Instant end = gameTime;
            if (tile.has("pestAt") && !tile.get("pestAt").isJsonNull()) {
                Instant pestAt = Instant.parse(tile.get("pestAt").getAsString());
                if (pestAt.isBefore(end)) end = pestAt;
            }
            tile.addProperty("grownAtAnchorMillis", Math.max(0, java.time.Duration.between(wateredAt, end).toMillis()));
            tile.addProperty("growthAnchorAt", gameTime.toString());
            tile.addProperty("wateredAt", gameTime.toString());
            if (tile.has("pestAt") && !tile.get("pestAt").isJsonNull())
                tile.addProperty("pestAt", gameTime.toString());
        }
        save.addProperty("version", GameSave.VERSION);
    }

    private static com.google.gson.JsonArray migrationPlotIds(JsonObject state, int previousStartingPlots) {
        boolean hasFarmProgress = false;
        com.google.gson.JsonArray tiles = state.getAsJsonArray("tiles");
        if (tiles != null) for (com.google.gson.JsonElement element : tiles) {
            JsonObject tile = element.getAsJsonObject();
            if (tile.has("state") && !"GRASS".equals(tile.get("state").getAsString())) {
                hasFarmProgress = true;
                break;
            }
        }
        com.google.gson.JsonArray ids = new com.google.gson.JsonArray();
        int unlockedCount = 5;
        if (previousStartingPlots > 0 && state.has("unlockedPlotIds")
                && state.getAsJsonArray("unlockedPlotIds") != null) {
            int previouslyUnlocked = Math.min(30, state.getAsJsonArray("unlockedPlotIds").size());
            unlockedCount = Math.min(30, 5 + Math.max(0, previouslyUnlocked - previousStartingPlots));
        }
        int count = hasFarmProgress ? 30 : unlockedCount;
        for (int id = 0; id < count; id++) ids.add(id);
        return ids;
    }
}
