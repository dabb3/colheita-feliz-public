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
        assertEquals(0, java.math.BigDecimal.valueOf(470).compareTo(restored.wallet().coins()));
        assertEquals(1, restored.inventory().seeds(turnip));
        assertEquals(0, restored.inventory().seeds(tomato));
        assertEquals(TileState.GROWING, restored.farm().tile(0, 3).state());
        assertEquals(2, restored.farm().tile(0, 3).crop().stageAt(restored.now()));
        assertEquals(Tool.WATER, restored.tool());
        assertEquals(tomato, restored.seed());
        reopened.save();
        assertEquals(0, java.math.BigDecimal.valueOf(470).compareTo(GameSession.open(clock, CropCatalogLoader.load(), repository, bound -> 99).state().wallet().coins()));
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
        assertEquals(0, java.math.BigDecimal.valueOf(475).compareTo(again.state().wallet().coins()));
        assertEquals(TileState.DEAD, again.state().farm().tile(0, 0).state());
        assertEquals(corn, again.state().farm().tile(0, 0).withered());
        assertTrue(again.state().sellProduce(corn).success());
        assertEquals(0, new java.math.BigDecimal("503.8").compareTo(again.state().wallet().coins()));
    }

    @Test void tutorialProgressSurvivesRestart() throws IOException {
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

    }

    @Test void hensFeedAndEggsSurviveRestartAndKeepProducingOffline() throws IOException {
        ManualClock clock = new ManualClock();
        JsonSaveRepository repository = new JsonSaveRepository(directory.resolve("save.json"));
        GameSession session = GameSession.open(clock, CropCatalogLoader.load(), repository, bound -> 99);
        GameState state = session.state();
        state.progress().award(50 + 75);
        var hen = state.animalType("galinha");
        state.wallet().credit(2 * hen.price() + 2 * hen.feedPrice());
        state.buyAnimal(hen);
        state.buyAnimal(hen);
        state.buyFeed(hen);
        state.buyFeed(hen);
        assertTrue(state.tendAnimal(0).success());
        state.inventory().addProduceLot(new br.com.colheitafeliz.domain.inventory.ItemLot("ovo", br.com.colheitafeliz.domain.economy.Currency.GOLD, java.math.BigDecimal.valueOf(state.animalType("galinha").price()), 2, false));
        session.save();
        clock.advanceSeconds(hen.productionSeconds());
        GameState restored = GameSession.open(clock, CropCatalogLoader.load(), repository, bound -> 99).state();
        assertEquals(2, restored.barn().size());
        assertEquals(0, restored.inventory().fertilizer());
        assertEquals(1, restored.inventory().feed("galinha"));
        assertEquals(2, restored.inventory().product("ovo"));
        assertEquals(br.com.colheitafeliz.domain.animal.Animal.Status.READY, restored.barn().get(0).statusAt(restored.now()));
        assertEquals(br.com.colheitafeliz.domain.animal.Animal.Status.HUNGRY, restored.barn().get(1).statusAt(restored.now()));
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

    @Test void persistsPestProgressAndAudioPreferences() throws IOException {
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
        assertEquals(0, java.math.BigDecimal.valueOf(8).compareTo(reopened.state().progress().currentXp()));
        assertFalse(reopened.state().musicEnabled());
        assertFalse(reopened.state().effectsEnabled());
        reopened.state().selectTool(Tool.WEEDER);
        assertTrue(reopened.state().act(0, 0).success());
        reopened.save();
        assertEquals(PestState.NONE, GameSession.open(clock, CropCatalogLoader.load(), repository,
                bound -> 99).state().farm().tile(0, 0).pest());
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

    @Test void insectsSurviveSave() throws IOException {
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
    }
    @Test void failedWriteReportsTheErrorInsteadOfSuccess() throws IOException {
        Path blocker = directory.resolve("not-a-folder");
        Files.writeString(blocker, "x");
        GameSession session = GameSession.open(new ManualClock(), CropCatalogLoader.load(),
                new JsonSaveRepository(blocker.resolve("save.json")), bound -> 99);
        assertFalse(session.trySave());
        assertTrue(session.state().message().startsWith("Erro ao salvar"), session.state().message());
    }

    @Test void successfulWriteIsConfirmed() throws IOException {
        Path file = directory.resolve("save.json");
        GameSession session = GameSession.open(new ManualClock(), CropCatalogLoader.load(),
                new JsonSaveRepository(file), bound -> 99);
        assertTrue(session.trySave());
        assertTrue(Files.exists(file));
    }
}
