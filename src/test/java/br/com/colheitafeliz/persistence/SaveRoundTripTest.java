package br.com.colheitafeliz.persistence;

import br.com.colheitafeliz.domain.crop.CropCatalog;
import br.com.colheitafeliz.domain.crop.CropType;
import br.com.colheitafeliz.domain.farm.FarmTile;
import br.com.colheitafeliz.domain.farm.TileState;
import br.com.colheitafeliz.testsupport.TestCrops;
import java.time.Instant;
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
    @Test void stolenSeasonRevisionProtectionAndVisitorOriginSurviveRoundTrip() throws IOException {
        var clock = new ManualClock();
        var catalog = CropCatalogLoader.load();
        var repository = new JsonSaveRepository(directory.resolve("social.json"));
        var session = GameSession.open(clock, catalog, repository, bound -> bound - 1);
        var owner = session.state();
        var visitorRepository = new JsonSaveRepository(directory.resolve("visitor.json"));
        var visitorSession = GameSession.open(clock, catalog, visitorRepository, bound -> bound - 1);
        var tile = owner.farm().tile(0, 0);
        tile.plow();
        var origin = new br.com.colheitafeliz.domain.inventory.ItemLot("morango", br.com.colheitafeliz.domain.economy.Currency.GREEN,
                new java.math.BigDecimal("7.3"), 1, true);
        tile.plant(catalog.get("morango"), owner.now(), origin);
        var ready = TestCrops.growUntilReady(tile, owner.now());
        clock.advanceSeconds(java.time.Duration.between(clock.instant(), ready).toSeconds());
        long revision = tile.harvestRevision();
        assertTrue(new br.com.colheitafeliz.game.VisitService().steal(owner, visitorSession.state(), 0, 0, revision).success());
        while (owner.progress().level() < 10) owner.progress().award(owner.progress().xpToNext());
        owner.wallet().credit(100_000);
        assertTrue(owner.buyAnimal(owner.animalType("cachorro-robo")).success());
        var protectedIds = owner.protectedPlots();
        session.save(); visitorSession.save();
        var restored = GameSession.open(clock, catalog, repository, bound -> bound - 1).state();
        assertEquals(protectedIds, restored.protectedPlots());
        assertEquals(revision + 1, restored.farm().tile(0, 0).harvestRevision());
        assertEquals(2, restored.farm().tile(0, 0).crop().season());
        assertFalse(restored.farm().tile(0, 0).crop().watered());
        var restoredVisitor = GameSession.open(clock, catalog, visitorRepository, bound -> bound - 1).state();
        assertEquals(java.util.List.of(origin), restoredVisitor.inventory().produceLots());
        assertFalse(new br.com.colheitafeliz.game.VisitService().steal(restored, restoredVisitor, 0, 0, revision).success());
    }

    @Test void stageFiveUpgradeAddsOnlySocialMetadataAndOldReaderRefusesDowngradeWithoutOverwriting() throws IOException {
        var clock = new ManualClock();
        var catalog = CropCatalogLoader.load();
        Path file = directory.resolve("v19.json");
        var session = GameSession.open(clock, catalog, new JsonSaveRepository(file), bound -> bound - 1);
        session.state().wallet().credit(br.com.colheitafeliz.domain.economy.Currency.GOLD, new java.math.BigDecimal("0.1"));
        session.state().progress().award(new java.math.BigDecimal("14.4375"));
        var tile = session.state().farm().tile(0, 0); tile.plow(); tile.plant(catalog.get("nabo"), session.state().now());
        session.save();
        var json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        json.addProperty("version", 19);
        json.getAsJsonObject("snapshot").remove("protection");
        for (var savedTile : json.getAsJsonObject("snapshot").getAsJsonArray("tiles")) savedTile.getAsJsonObject().remove("harvestRevision");
        Files.writeString(file, json.toString());
        String oldBytes = Files.readString(file);
        var reopened = GameSession.open(clock, catalog, new JsonSaveRepository(file), bound -> bound - 1);
        assertEquals(oldBytes, Files.readString(file), "carregar v19 não escreve");
        assertEquals(0, reopened.state().farm().tile(0, 0).harvestRevision());
        var upgraded = reopened.state().snapshot();
        var original = session.state().snapshot();
        assertEquals(original.coins(), upgraded.coins());
        assertEquals(original.greenCoins(), upgraded.greenCoins());
        assertEquals(original.currentXp(), upgraded.currentXp());
        assertEquals(original.level(), upgraded.level());
        assertEquals(original.seeds(), upgraded.seeds());
        assertEquals(original.produce(), upgraded.produce());
        assertEquals(original.animals(), upgraded.animals());
        assertEquals(original.dailyMissionProgress(), upgraded.dailyMissionProgress());
        assertEquals(original.completedDailyMissions(), upgraded.completedDailyMissions());
        assertEquals(original.tutorialStep(), upgraded.tutorialStep());
        assertEquals(original.tiles().getFirst().origin(), upgraded.tiles().getFirst().origin());
        reopened.save();
        assertEquals(20, JsonParser.parseString(Files.readString(file)).getAsJsonObject().get("version").getAsInt());
        byte[] newBytes = Files.readAllBytes(file);
        var oldReader = new br.com.colheitafeliz.persistence.stage5.Stage5JsonSaveRepository(file);
        assertThrows(IOException.class, oldReader::load);
        assertThrows(IOException.class, () -> oldReader.save(upgraded, clock.instant(), clock.instant()));
        assertArrayEquals(newBytes, Files.readAllBytes(file));
    }

    @Test void currentSaveMissingSocialMetadataIsRefusedWithoutOverwriting() throws IOException {
        var clock = new ManualClock();
        var catalog = CropCatalogLoader.load();
        for (String missing : java.util.List.of("protection", "harvestRevision")) {
            Path file = directory.resolve("missing-" + missing + ".json");
            var session = GameSession.open(clock, catalog, new JsonSaveRepository(file), bound -> bound - 1);
            session.save();
            var json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            if (missing.equals("protection")) json.getAsJsonObject("snapshot").remove(missing);
            else json.getAsJsonObject("snapshot").getAsJsonArray("tiles").get(0).getAsJsonObject().remove(missing);
            Files.writeString(file, json.toString());
            String before = Files.readString(file);
            assertThrows(IOException.class, () -> new JsonSaveRepository(file).load());
            assertFalse(session.trySave());
            assertEquals(before, Files.readString(file));
        }
    }
    @Test void malformedProtectionIdsAreRefusedAndPreservedInsteadOfSilentlyReselected() throws IOException {
        var clock = new ManualClock();
        var catalog = CropCatalogLoader.load();
        int caseIndex = 0;
        for (String unlocked : java.util.List.of("[-1]", "[30]", "[0,1,2,3,4,5]")) {
            Path file = directory.resolve("invalid-protection-" + caseIndex++ + ".json");
            var session = GameSession.open(clock, catalog, new JsonSaveRepository(file), bound -> bound - 1);
            session.save();
            var json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            json.getAsJsonObject("snapshot").getAsJsonObject("protection").add("unlockedIds", JsonParser.parseString(unlocked));
            Files.writeString(file, json.toString());
            String before = Files.readString(file);
            assertThrows(IOException.class, () -> new JsonSaveRepository(file).load());
            assertThrows(IOException.class, () -> GameSession.open(clock, catalog, new JsonSaveRepository(file), bound -> bound - 1));
            assertFalse(session.trySave());
            assertEquals(before, Files.readString(file));
        }
    }
    @Test void negativeRevisionIsRefusedBeforeRepositoryCanOverwriteTheFile() throws IOException {
        var clock = new ManualClock();
        var catalog = CropCatalogLoader.load();
        Path file = directory.resolve("negative-revision.json");
        var session = GameSession.open(clock, catalog, new JsonSaveRepository(file), bound -> bound - 1);
        session.save();
        var json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        json.getAsJsonObject("snapshot").getAsJsonArray("tiles").get(0).getAsJsonObject().addProperty("harvestRevision", -1);
        Files.writeString(file, json.toString());
        String before = Files.readString(file);
        assertThrows(IOException.class, () -> new JsonSaveRepository(file).load());
        assertThrows(IOException.class, () -> new JsonSaveRepository(file).save(session.state().snapshot(), clock.instant(), clock.instant()));
        assertEquals(before, Files.readString(file));
    }
    @Test void paidUtcDaySurvivesRestartRollbackAndSkippedDays() throws IOException {
        var clock = new ManualClock();
        var repository = new JsonSaveRepository(directory.resolve("daily.json"));
        var catalog = CropCatalogLoader.load();
        var session = GameSession.open(clock, catalog, repository, bound -> 99);
        var state = session.state();
        state.startNewFarm();
        assertTrue(state.buySeed(catalog.get("nabo")).success());
        state.selectTool(Tool.HOE);
        assertTrue(state.act(0, 0).success());
        state.selectSeed(catalog.get("nabo"));
        assertTrue(state.act(0, 0).success());
        session.save();
        session = GameSession.open(clock, catalog, repository, bound -> 99);
        assertEquals(0, new java.math.BigDecimal("7").compareTo(session.state().greenCoins()));
        clock.advanceSeconds(3 * 86400);
        session.state().refresh();
        assertEquals(0, new java.math.BigDecimal("7").compareTo(session.state().greenCoins()));
        assertTrue(session.state().buyFeed("growth").success());
        session.save();
        var highDay = session.state().dailyMissions().day();
        clock.advanceSeconds(-3 * 86400);
        session = GameSession.open(clock, catalog, repository, bound -> 99);
        assertEquals(highDay, session.state().dailyMissions().day());
        session.state().wallet().credit(1000);
        assertTrue(session.state().buyFeed("growth").success());
        assertEquals(0, new java.math.BigDecimal("8").compareTo(session.state().greenCoins()));
        session.save();
        clock.advanceSeconds(4 * 86400);
        session = GameSession.open(clock, catalog, repository, bound -> 99);
        assertTrue(session.state().buyFeed("growth").success());
        assertEquals(0, new java.math.BigDecimal("9").compareTo(session.state().greenCoins()));
    }

    @TempDir Path directory;

    @Test void saveAndLoadKeepMoneyInventorySoilCropAndWater() throws IOException {
        ManualClock clock = new ManualClock();
        Path file = directory.resolve("save.json");
        JsonSaveRepository repository = new JsonSaveRepository(file);
        GameSession session = GameSession.open(clock, CropCatalogLoader.load(), repository, bound -> 99);
        GameState state = session.state();
        CropType tomato = state.catalog().get("cenoura");
        CropType turnip = state.catalog().get("nabo");
        state.wallet().credit(1000);
        assertTrue(state.buySeed(tomato).success());
        assertTrue(state.buySeed(turnip).success());
        state.selectTool(Tool.HOE);
        state.act(0, 3);
        state.selectSeed(tomato);
        state.act(0, 3);
        state.selectTool(Tool.WATER);
        state.act(0, 3);
        session.save();
        assertTrue(Files.readString(file).contains("\"version\": " + GameSave.VERSION));
        assertTrue(Files.readString(file).contains("\"wateredAt\""));
        assertFalse(Files.readString(file).contains("unlockedCropIds"), "a liberação vem do nível, não do save");
        clock.advanceSeconds(13_500);
        GameSession reopened = GameSession.open(clock, CropCatalogLoader.load(), repository, bound -> 99);
        GameState restored = reopened.state();
        assertEquals(0, java.math.BigDecimal.valueOf(850).compareTo(restored.wallet().coins()));
        assertEquals(1, restored.inventory().seeds(turnip));
        assertEquals(0, restored.inventory().seeds(tomato));
        assertEquals(TileState.GROWING, restored.farm().tile(0, 3).state());
        assertEquals(2, restored.farm().tile(0, 3).crop().stageAt(restored.now()));
        assertEquals(Tool.WATER, restored.tool());
        assertEquals(tomato, restored.seed());
        reopened.save();
        assertEquals(0, java.math.BigDecimal.valueOf(850).compareTo(GameSession.open(clock, CropCatalogLoader.load(), repository, bound -> 99).state().wallet().coins()));
    }

    @Test void secondSeasonSurvivesSaveWithOriginWateringAndProgress() throws IOException {
        ManualClock clock = new ManualClock();
        CropType strawberry = TestCrops.strawberry();
        CropCatalog catalog = new CropCatalog(CropCatalogLoader.load().all().stream()
                .map(type -> type.id().equals(strawberry.id()) ? strawberry : type).toList());
        JsonSaveRepository repository = new JsonSaveRepository(directory.resolve("season.json"));
        GameSession session = GameSession.open(clock, catalog, repository, bound -> 99);
        FarmTile tile = session.state().farm().tile(0, 3);
        tile.plow();
        tile.plant(strawberry, clock.instant(), TestCrops.origin(strawberry));
        Instant ready = TestCrops.growUntilReady(tile, clock.instant());
        tile.harvest(ready);
        tile.water(ready);
        clock.advanceSeconds(java.time.Duration.between(clock.instant(), ready).toSeconds() + 3600);
        long grown = tile.crop().grownMillisAt(clock.instant());
        session.save();

        FarmTile restored = GameSession.open(clock, catalog, repository, bound -> 99).state().farm().tile(0, 3);
        assertEquals(TileState.GROWING, restored.state());
        assertEquals(2, restored.crop().season());
        assertEquals(TestCrops.origin(strawberry), restored.crop().origin());
        assertEquals(ready, restored.crop().wateredAt());
        assertEquals(grown, restored.crop().grownMillisAt(clock.instant()));
        assertEquals(java.time.Duration.ofHours(4), restored.crop().moistDuration());
    }

    @Test void fertilizerStockPerTypeTheSeasonMarkAndTheSelectionSurviveSave() throws IOException {
        ManualClock clock = new ManualClock();
        JsonSaveRepository repository = new JsonSaveRepository(directory.resolve("fertilizer.json"));
        GameSession session = GameSession.open(clock, CropCatalogLoader.load(), repository, bound -> 99);
        GameState state = session.state();
        state.inventory().addFertilizer("comum", 2);
        state.inventory().addFertilizer("super", 1);
        FarmTile tile = state.farm().tile(0, 0);
        tile.plow();
        tile.plant(state.catalog().get("nabo"), clock.instant());
        tile.water(clock.instant());
        assertTrue(state.selectFertilizer("comum").success());
        state.selectTool(Tool.FERTILIZER);
        assertTrue(state.act(0, 0).success());
        assertTrue(state.selectFertilizer("super").success());
        session.save();
        assertFalse(Files.readString(directory.resolve("fertilizer.json")).contains("\"fertilizer\":"), "sem o contador único antigo");

        GameState restored = GameSession.open(clock, CropCatalogLoader.load(), repository, bound -> 99).state();
        assertEquals(1, restored.inventory().fertilizer("comum"));
        assertEquals(1, restored.inventory().fertilizer("super"));
        assertEquals("super", restored.selectedFertilizer().id());
        assertTrue(restored.farm().tile(0, 0).fertilized());
        restored.selectTool(Tool.FERTILIZER);
        assertFalse(restored.act(0, 0).success(), "reabrir não libera segunda aplicação na mesma temporada");
        assertEquals(1, restored.inventory().fertilizer("super"));
    }

    @Test void stageTwoSaveWithoutFertilizerFieldsOpensWithEmptyStock() throws IOException {
        ManualClock clock = new ManualClock();
        Path file = directory.resolve("stage-two.json");
        GameSession.open(clock, CropCatalogLoader.load(), new JsonSaveRepository(file), bound -> 99).save();
        // Remove fertilizers e selectedFertilizerId e põe o contador único antigo fertilizer (a versão segue v20): ele é ignorado, sem conversão.
        JsonObject save = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        JsonObject snapshot = save.getAsJsonObject("snapshot");
        snapshot.remove("fertilizers");
        snapshot.remove("selectedFertilizerId");
        snapshot.addProperty("fertilizer", 3);
        Files.writeString(file, save.toString());

        GameState restored = GameSession.open(clock, CropCatalogLoader.load(), new JsonSaveRepository(file), bound -> 99).state();
        for (var type : restored.fertilizers()) assertEquals(0, restored.inventory().fertilizer(type.id()), type.id());
        assertEquals("comum", restored.selectedFertilizer().id());
    }

    @Test void offlineGrowthContinuesAndHarvestedProduceSurvivesAnotherRestart() throws IOException {
        ManualClock clock = new ManualClock();
        JsonSaveRepository repository = new JsonSaveRepository(directory.resolve("save.json"));
        GameSession session = GameSession.open(clock, CropCatalogLoader.load(), repository, bound -> 99);
        GameState state = session.state();
        CropType corn = state.catalog().get("nabo");
        state.buySeed(corn);
        state.selectTool(Tool.HOE);
        state.act(0, 0);
        state.selectSeed(corn);
        state.act(0, 0);
        state.selectTool(Tool.WATER);
        state.act(0, 0);
        session.save();
        clock.advanceSeconds(60_000);
        GameSession reopened = GameSession.open(clock, CropCatalogLoader.load(), repository, bound -> 99);
        assertEquals(TileState.READY, reopened.state().farm().tile(0, 0).state());
        reopened.state().selectTool(Tool.HAND);
        reopened.state().act(0, 0);
        reopened.save();
        GameSession again = GameSession.open(clock, CropCatalogLoader.load(), repository, bound -> 99);
        assertEquals(1, again.state().inventory().produce(corn));
        assertEquals(0, java.math.BigDecimal.valueOf(200).compareTo(again.state().wallet().coins()));
        assertEquals(TileState.DEAD, again.state().farm().tile(0, 0).state());
        assertEquals(corn, again.state().farm().tile(0, 0).withered());
        assertTrue(again.state().sellProduce(corn).success());
        assertEquals(0, java.math.BigDecimal.valueOf(545).compareTo(again.state().wallet().coins()));
    }

    @Test void tutorialProgressSurvivesRestart() throws IOException {
        ManualClock clock = new ManualClock();
        Path file = directory.resolve("save.json");
        JsonSaveRepository repository = new JsonSaveRepository(file);
        GameSession session = GameSession.open(clock, CropCatalogLoader.load(), repository, bound -> 99);
        assertTrue(session.fresh());
        session.state().startNewFarm();
        assertTrue(session.state().buySeed(session.state().catalog().get("nabo")).success());
        session.save();
        GameSession reopened = GameSession.open(clock, CropCatalogLoader.load(), repository, bound -> 99);
        assertFalse(reopened.fresh());
        assertEquals(1, reopened.state().tutorial().stepIndex());
        assertEquals(TileState.GRASS, reopened.state().farm().tile(0, 1).state());

    }

    @Test void animalProgressOriginReadyProductAndHungryProtectorSurviveRestart() throws IOException {
        ManualClock clock = new ManualClock();
        var repository = new JsonSaveRepository(directory.resolve("animals.json"));
        var session = GameSession.open(clock, CropCatalogLoader.load(), repository, bound -> bound - 1);
        var state = session.state();
        while (state.progress().level() < 60) state.progress().award(state.progress().xpToNext());
        state.wallet().credit(1_000_000);
        state.wallet().credit(br.com.colheitafeliz.domain.economy.Currency.GREEN, new java.math.BigDecimal("100"));
        assertTrue(state.buyAnimal(state.animalType("pombo"), br.com.colheitafeliz.domain.economy.Currency.GREEN).success());
        assertTrue(state.buyAnimal(state.animalType("galinha")).success());
        assertTrue(state.buyAnimal(state.animalType("cachorro-robo")).success());
        clock.advanceSeconds(5 * 3600);
        session.save();
        clock.advanceSeconds(43 * 3600);
        var restored = GameSession.open(clock, CropCatalogLoader.load(), repository, bound -> bound - 1).state();
        assertEquals(12 * 3600_000L, restored.barn().get(0).grownMillisAt(restored.now()));
        assertEquals(br.com.colheitafeliz.domain.animal.Animal.Stage.YOUNG, restored.barn().get(0).stageAt(restored.now()));
        assertEquals(br.com.colheitafeliz.domain.economy.Currency.GREEN, restored.barn().get(0).origin().currency());
        assertEquals(br.com.colheitafeliz.domain.animal.Animal.Status.READY, restored.barn().get(1).statusAt(restored.now()));
        assertEquals(br.com.colheitafeliz.domain.animal.Animal.Status.HUNGRY, restored.barn().get(2).statusAt(restored.now()));
        assertEquals(0, restored.activeProtectors());
        assertTrue(restored.tendAnimal(1).success());
        assertEquals(1, restored.inventory().product("ovo"));
    }

    @Test void legacyAnimalsAndFeedAreRefusedWithoutLosingExistingLots() throws IOException {
        var clock = new ManualClock();
        for (String legacyField : java.util.List.of("animals", "feed")) {
            Path file = directory.resolve(legacyField + ".json");
            var repo = new JsonSaveRepository(file);
            var session = GameSession.open(clock, CropCatalogLoader.load(), repo, bound -> 99);
            session.save();
            var json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            json.addProperty("version", 17);
            var snapshot = json.getAsJsonObject("snapshot");
            snapshot.add(legacyField, JsonParser.parseString(legacyField.equals("animals")
                    ? "[{\"typeId\":\"galinha\",\"fedAt\":null}]" : "{\"galinha\":1}"));
            Files.writeString(file, json.toString());
            String original = Files.readString(file);
            assertThrows(IOException.class, repo::load);
            assertThrows(IOException.class, () -> repo.save(session.state().snapshot(), clock.instant(), clock.instant()));
            assertEquals(original, Files.readString(file));
        }
    }

    @Test void legacyEmptyBarnPreservesSavedProductCostInsteadOfRepricingIt() throws IOException {
        var clock = new ManualClock();
        Path file = directory.resolve("lots.json");
        var repo = new JsonSaveRepository(file);
        var session = GameSession.open(clock, CropCatalogLoader.load(), repo, bound -> 99);
        var lot = new br.com.colheitafeliz.domain.inventory.ItemLot("ovo", br.com.colheitafeliz.domain.economy.Currency.GOLD,
                new java.math.BigDecimal("45"), 3, false);
        session.state().inventory().addProduceLot(lot);
        session.save();
        var json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        json.addProperty("version", 17);
        Files.writeString(file, json.toString());
        String before = Files.readString(file);
        assertThrows(IOException.class, repo::load);
        assertThrows(IOException.class, () -> session.save());
        assertEquals(before, Files.readString(file));

    }

    @Test void stageThreeReaderRefusesSave18AndNeverOverwritesIt() throws IOException {
        var clock = new ManualClock(); var file = directory.resolve("v18.json");
        var repository = new JsonSaveRepository(file);
        var session = GameSession.open(clock, CropCatalogLoader.load(), repository, bound -> 99);
        session.save(); String original = Files.readString(file);
        var oldReader = new br.com.colheitafeliz.persistence.stage3.Stage3JsonSaveRepository(file);
        assertThrows(IOException.class, oldReader::load);
        assertThrows(IOException.class, () -> oldReader.save(session.state().snapshot(), clock.instant(), clock.instant()));
        assertEquals(original, Files.readString(file));
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
        CropType corn = session.state().catalog().get("nabo");
        session.state().buySeed(corn);
        session.state().selectTool(Tool.HOE);
        session.state().act(0, 0);
        session.state().selectSeed(corn);
        session.state().act(0, 0);
        session.state().selectTool(Tool.WATER);
        session.state().act(0, 0);
        session.clock().setSpeed(60);
        clock.advanceSeconds(1_000);
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
        clock.advanceSeconds(12_000);
        session.state().act(0, 0);
        clock.advanceSeconds(6_000);
        session.state().refresh();
        session.state().setMusicEnabled(false);
        session.state().setEffectsEnabled(false);
        session.save();
        clock.advanceSeconds(18_000);
        GameSession reopened = GameSession.open(clock, CropCatalogLoader.load(), repository, bound -> 99);
        assertEquals(PestState.WEEDS, reopened.state().farm().tile(0, 0).pest());
        assertEquals(TileState.GROWING, reopened.state().farm().tile(0, 0).state());
        assertEquals(0, java.math.BigDecimal.valueOf(11).compareTo(reopened.state().progress().currentXp()));
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
        CropType potato = session.state().catalog().get("milho");
        assertFalse(session.state().unlocked(potato));
        session.state().wallet().credit(1000);
        for (int i = 0; i < 3; i++) assertTrue(session.state().buySeed(turnip).success());
        for (int row = 0; row < 3; row++) {
            session.state().selectTool(Tool.HOE);
            session.state().act(0, row);
            session.state().selectSeed(turnip);
            session.state().act(0, row);
        }
        for (int third = 0; third < 3; third++) {
            session.state().selectTool(Tool.WATER);
            for (int row = 0; row < 3; row++) assertTrue(session.state().act(0, row).success());
            clock.advanceSeconds(12_000);
        }
        session.state().refresh();
        session.state().selectTool(Tool.HAND);
        for (int row = 0; row < 3; row++) session.state().act(0, row);
        assertTrue(session.state().unlocked(potato));
        session.save();
        GameState restored = GameSession.open(clock, CropCatalogLoader.load(), repository,
                bound -> 99).state();
        assertEquals(3, restored.progress().level());
        assertTrue(restored.unlocked(potato));
        assertTrue(restored.buySeed(potato).success());
    }

    @Test void insectsSurviveSave() throws IOException {
        ManualClock clock = new ManualClock();
        Path file = directory.resolve("save.json");
        JsonSaveRepository repository = new JsonSaveRepository(file);
        GameSession session = GameSession.open(clock, CropCatalogLoader.load(), repository, bound -> 99);
        CropType turnip = session.state().catalog().get("nabo");
        session.state().buySeed(turnip);
        session.state().selectTool(Tool.HOE);
        session.state().act(0, 0);
        session.state().selectSeed(turnip);
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
