package br.com.colheitafeliz.persistence;

import br.com.colheitafeliz.domain.crop.CropType;
import br.com.colheitafeliz.domain.farm.TileState;
import br.com.colheitafeliz.domain.farm.PestState;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import br.com.colheitafeliz.game.CropCatalogLoader;
import br.com.colheitafeliz.game.GameSession;
import br.com.colheitafeliz.game.GameState;
import br.com.colheitafeliz.game.Tool;
import br.com.colheitafeliz.testsupport.ManualClock;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SaveRoundTripTest {
    @TempDir Path directory;

    @Test void saveAndLoadKeepMoneyInventorySoilCropAndWater() throws IOException {
        ManualClock clock = new ManualClock();
        Path file = directory.resolve("save.json");
        JsonSaveRepository repository = new JsonSaveRepository(file);
        GameSession session = GameSession.open(clock, CropCatalogLoader.load(), repository, bound -> 99);
        GameState state = session.state();
        CropType tomato = state.catalog().get("tomate");
        CropType turnip = state.catalog().get("nabo");
        state.buySeed(tomato);
        state.buySeed(turnip);
        state.selectTool(Tool.HOE);
        state.act(0, 3);
        state.selectSeed(tomato);
        state.act(0, 3);
        state.selectTool(Tool.WATER);
        state.act(0, 3);
        session.save();
        assertTrue(Files.readString(file).contains("\"version\": " + GameSave.VERSION));
        assertTrue(Files.readString(file).contains("\"wateredAt\""));
        clock.advanceSeconds(450);
        GameSession reopened = GameSession.open(clock, CropCatalogLoader.load(), repository, bound -> 99);
        GameState restored = reopened.state();
        assertEquals(70, restored.wallet().coins());
        assertEquals(1, restored.inventory().seeds(turnip));
        assertEquals(0, restored.inventory().seeds(tomato));
        assertEquals(TileState.GROWING, restored.farm().tile(0, 3).state());
        assertEquals(2, restored.farm().tile(0, 3).crop().stageAt(restored.now()));
        assertEquals(Tool.WATER, restored.tool());
        assertEquals(tomato, restored.seed());
        reopened.save();
        assertEquals(70, GameSession.open(clock, CropCatalogLoader.load(), repository, bound -> 99).state().wallet().coins());
    }

    @Test void offlineGrowthContinuesAndHarvestedProduceSurvivesAnotherRestart() throws IOException {
        ManualClock clock = new ManualClock();
        JsonSaveRepository repository = new JsonSaveRepository(directory.resolve("save.json"));
        GameSession session = GameSession.open(clock, CropCatalogLoader.load(), repository, bound -> 99);
        GameState state = session.state();
        CropType corn = state.catalog().get("milho");
        state.buySeed(corn);
        state.selectTool(Tool.HOE);
        state.act(0, 0);
        state.selectSeed(corn);
        state.act(0, 0);
        state.selectTool(Tool.WATER);
        state.act(0, 0);
        session.save();
        clock.advanceSeconds(4500);
        GameSession reopened = GameSession.open(clock, CropCatalogLoader.load(), repository, bound -> 99);
        assertEquals(TileState.READY, reopened.state().farm().tile(0, 0).state());
        reopened.state().selectTool(Tool.HAND);
        reopened.state().act(0, 0);
        reopened.save();
        GameSession again = GameSession.open(clock, CropCatalogLoader.load(), repository, bound -> 99);
        assertEquals(3, again.state().inventory().produce(corn));
        assertEquals(75, again.state().wallet().coins());
        assertEquals(TileState.DEAD, again.state().farm().tile(0, 0).state());
        assertEquals(corn, again.state().farm().tile(0, 0).withered());
        assertTrue(again.state().sellProduce(corn).success());
        assertEquals(100, again.state().wallet().coins());
    }

    @Test void tutorialProgressSurvivesRestartAndOldSavesSkipIt() throws IOException {
        ManualClock clock = new ManualClock();
        Path file = directory.resolve("save.json");
        JsonSaveRepository repository = new JsonSaveRepository(file);
        GameSession session = GameSession.open(clock, CropCatalogLoader.load(), repository, bound -> 99);
        assertTrue(session.fresh());
        session.state().startNewFarm();
        session.state().selectTool(Tool.HAND);
        session.state().act(0, 0);
        session.save();
        GameSession reopened = GameSession.open(clock, CropCatalogLoader.load(), repository, bound -> 99);
        assertFalse(reopened.fresh());
        assertEquals(1, reopened.state().tutorial().stepIndex());
        assertEquals(TileState.READY, reopened.state().farm().tile(0, 1).state());

        JsonObject old = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        old.addProperty("version", 11);
        old.getAsJsonObject("snapshot").remove("tutorialStep");
        Files.writeString(file, old.toString());
        assertFalse(GameSession.open(clock, CropCatalogLoader.load(), repository, bound -> 99)
                .state().tutorial().active());
    }

    @Test void rejectsUnsupportedSaveVersion() throws IOException {
        Path file = directory.resolve("save.json");
        Files.writeString(file, "{\"version\":99}");
        assertThrows(IOException.class, () -> new JsonSaveRepository(file).load());
    }

    @Test void acceleratedClockCanBeSavedAndResumedWithRealOfflineTime() throws IOException {
        ManualClock clock = new ManualClock();
        JsonSaveRepository repository = new JsonSaveRepository(directory.resolve("save.json"));
        GameSession session = GameSession.open(clock, CropCatalogLoader.load(), repository, bound -> 99);
        CropType corn = session.state().catalog().get("milho");
        session.state().buySeed(corn);
        session.state().selectTool(Tool.HOE);
        session.state().act(0, 0);
        session.state().selectSeed(corn);
        session.state().act(0, 0);
        session.state().selectTool(Tool.WATER);
        session.state().act(0, 0);
        session.clock().setSpeed(60);
        clock.advanceSeconds(75);
        session.save();
        clock.advanceSeconds(20);
        GameSession reopened = GameSession.open(clock, CropCatalogLoader.load(), repository, bound -> 99);
        assertEquals(TileState.READY, reopened.state().farm().tile(0, 0).state());
    }

    @Test void versionTwoPersistsPestProgressAndAudioPreferences() throws IOException {
        ManualClock clock = new ManualClock();
        JsonSaveRepository repository = new JsonSaveRepository(directory.resolve("save.json"));
        GameSession session = GameSession.open(clock, CropCatalogLoader.load(), repository, bound -> 0);
        CropType turnip = session.state().catalog().get("nabo");
        session.state().buySeed(turnip);
        session.state().selectTool(Tool.HOE);
        session.state().act(0, 0);
        session.state().selectSeed(turnip);
        session.state().act(0, 0);
        session.state().selectTool(Tool.WATER);
        session.state().act(0, 0);
        clock.advanceSeconds(300);
        session.state().refresh();
        session.state().setMusicEnabled(false);
        session.state().setEffectsEnabled(false);
        session.save();
        clock.advanceSeconds(300);
        GameSession reopened = GameSession.open(clock, CropCatalogLoader.load(), repository, bound -> 99);
        assertEquals(PestState.WEEDS, reopened.state().farm().tile(0, 0).pest());
        assertEquals(TileState.GROWING, reopened.state().farm().tile(0, 0).state());
        assertEquals(8, reopened.state().progress().currentXp());
        assertFalse(reopened.state().musicEnabled());
        assertFalse(reopened.state().effectsEnabled());
        reopened.state().selectTool(Tool.WEEDER);
        assertTrue(reopened.state().act(0, 0).success());
        reopened.save();
        assertEquals(PestState.NONE, GameSession.open(clock, CropCatalogLoader.load(), repository,
                bound -> 99).state().farm().tile(0, 0).pest());
    }

    @Test void loadsPhaseTwoVersionOneSaveAndUpgradesOnNextWrite() throws IOException {
        ManualClock clock = new ManualClock();
        Path file = directory.resolve("save.json");
        JsonSaveRepository repository = new JsonSaveRepository(file);
        GameSession session = GameSession.open(clock, CropCatalogLoader.load(), repository, bound -> 99);
        CropType turnip = session.state().catalog().get("nabo");
        session.state().buySeed(turnip);
        session.state().selectTool(Tool.HOE);
        session.state().act(0, 1);
        session.save();
        JsonObject old = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        old.addProperty("version", 1);
        JsonObject snapshot = old.getAsJsonObject("snapshot");
        for (String field : new String[]{"level", "currentXp", "unlockedCropIds",
                "musicEnabled", "effectsEnabled"}) snapshot.remove(field);
        snapshot.getAsJsonArray("tiles").forEach(tile -> {
            JsonObject object = tile.getAsJsonObject();
            object.remove("pest");
            object.remove("pestAt");
            object.remove("nextPestCheckAt");
        });
        Files.writeString(file, old.toString());
        GameSession migrated = GameSession.open(clock, CropCatalogLoader.load(), repository, bound -> 99);
        assertEquals(90, migrated.state().wallet().coins());
        assertEquals(1, migrated.state().inventory().seeds(turnip));
        assertEquals(TileState.PLOWED, migrated.state().farm().tile(0, 1).state());
        assertEquals(1, migrated.state().progress().level());
        assertTrue(migrated.state().musicEnabled());
        assertTrue(migrated.state().effectsEnabled());
        migrated.save();
        assertEquals(GameSave.VERSION, JsonParser.parseString(Files.readString(file)).getAsJsonObject()
                .get("version").getAsInt());
    }

    @Test void versionNineGrowthIsKeptAndMigrationCountsAsFreshWatering() throws IOException {
        ManualClock clock = new ManualClock();
        Path file = directory.resolve("save.json");
        JsonSaveRepository repository = new JsonSaveRepository(file);
        GameSession session = GameSession.open(clock, CropCatalogLoader.load(), repository, bound -> 99);
        CropType tomato = session.state().catalog().get("tomate");
        session.state().buySeed(tomato);
        session.state().selectTool(Tool.HOE);
        session.state().act(0, 0);
        session.state().selectSeed(tomato);
        session.state().act(0, 0);
        session.state().selectTool(Tool.WATER);
        session.state().act(0, 0);
        session.save();
        JsonObject old = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        old.addProperty("version", 9);
        java.time.Instant gameTime = java.time.Instant.parse(old.get("gameTime").getAsString());
        JsonObject tile = old.getAsJsonObject("snapshot").getAsJsonArray("tiles").get(0).getAsJsonObject();
        tile.remove("growthAnchorAt");
        tile.remove("grownAtAnchorMillis");
        tile.addProperty("plantedAt", gameTime.minusSeconds(1300).toString());
        tile.addProperty("wateredAt", gameTime.minusSeconds(1200).toString());
        Files.writeString(file, old.toString());
        GameState migrated = GameSession.open(clock, CropCatalogLoader.load(), repository, bound -> 99).state();
        var crop = migrated.farm().tile(0, 0).crop();
        assertEquals(1_200_000, crop.grownMillisAt(migrated.now()));
        assertEquals(3, crop.stageAt(migrated.now()));
        assertFalse(crop.dryAt(migrated.now()));
    }

    @Test void unlockedCropRemainsAvailableAfterRestart() throws IOException {
        ManualClock clock = new ManualClock();
        JsonSaveRepository repository = new JsonSaveRepository(directory.resolve("save.json"));
        GameSession session = GameSession.open(clock, CropCatalogLoader.load(), repository, bound -> 99);
        CropType turnip = session.state().catalog().get("nabo");
        CropType potato = session.state().catalog().get("batata");
        assertFalse(session.state().unlocked(potato));
        for (int i = 0; i < 3; i++) session.state().buySeed(turnip);
        for (int row = 0; row < 3; row++) {
            session.state().selectTool(Tool.HOE);
            session.state().act(0, row);
            session.state().selectSeed(turnip);
            session.state().act(0, row);
            session.state().selectTool(Tool.WATER);
            session.state().act(0, row);
        }
        clock.advanceSeconds(900);
        session.state().refresh();
        session.state().selectTool(Tool.HAND);
        for (int row = 0; row < 3; row++) session.state().act(0, row);
        assertTrue(session.state().unlocked(potato));
        session.save();
        GameState restored = GameSession.open(clock, CropCatalogLoader.load(), repository,
                bound -> 99).state();
        assertEquals(2, restored.progress().level());
        assertTrue(restored.unlocked(potato));
        assertTrue(restored.buySeed(potato).success());
    }

    @Test void insectsSurviveSaveAndOldSavesStartWithout() throws IOException {
        ManualClock clock = new ManualClock();
        Path file = directory.resolve("save.json");
        JsonSaveRepository repository = new JsonSaveRepository(file);
        GameSession session = GameSession.open(clock, CropCatalogLoader.load(), repository, bound -> 99);
        CropType strawberry = session.state().catalog().get("morango");
        session.state().buySeed(strawberry);
        session.state().selectTool(Tool.HOE);
        session.state().act(0, 0);
        session.state().selectSeed(strawberry);
        session.state().act(0, 0);
        session.state().selectTool(Tool.WATER);
        session.state().act(0, 0);
        var tile = session.state().farm().tile(0, 0);
        tile.checkInsects(clock.instant(), true);
        tile.worsenInsects(clock.instant());
        session.save();
        assertEquals(2, GameSession.open(clock, CropCatalogLoader.load(), repository, bound -> 99)
                .state().farm().tile(0, 0).insects());
        JsonObject old = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        old.addProperty("version", 10);
        old.getAsJsonObject("snapshot").getAsJsonArray("tiles").forEach(t -> {
            t.getAsJsonObject().remove("insects");
            t.getAsJsonObject().remove("nextInsectCheckAt");
        });
        Files.writeString(file, old.toString());
        GameState migrated = GameSession.open(clock, CropCatalogLoader.load(), repository, bound -> 99).state();
        assertEquals(0, migrated.farm().tile(0, 0).insects());
        assertNotNull(migrated.farm().tile(0, 0).nextInsectCheckAt());
    }
}
