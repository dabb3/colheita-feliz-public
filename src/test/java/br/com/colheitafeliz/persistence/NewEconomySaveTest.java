package br.com.colheitafeliz.persistence;

import br.com.colheitafeliz.domain.economy.Currency;
import br.com.colheitafeliz.domain.inventory.ItemLot;
import br.com.colheitafeliz.game.CropCatalogLoader;
import br.com.colheitafeliz.game.GameSession;
import br.com.colheitafeliz.testsupport.ManualClock;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class NewEconomySaveTest {
    @TempDir Path directory;

    @Test void newEconomyRoundTripIsExact() throws IOException {
        ManualClock clock = new ManualClock();
        Path file = directory.resolve("new.json");
        var repository = new JsonSaveRepository(file);
        GameSession session = GameSession.open(clock, CropCatalogLoader.load(), repository, bound -> 99);
        session.state().wallet().credit(Currency.GOLD, new BigDecimal("0.1"));
        session.state().wallet().credit(Currency.GREEN, new BigDecimal("95"));
        assertTrue(session.state().buyVip().success());
        session.state().progress().award(new BigDecimal("14.4375"));
        ItemLot lot = new ItemLot("nabo", Currency.GOLD, new BigDecimal("300"), 2, false);
        session.state().inventory().addProduceLot(lot);
        session.save();
        var json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        assertEquals(17, json.get("version").getAsInt());
        assertEquals("economy-2026-10", json.get("ruleset").getAsString());
        var restored = GameSession.open(clock, CropCatalogLoader.load(), repository, bound -> 99).state();
        assertEquals(0, new BigDecimal("500.1").compareTo(restored.wallet().coins()));
        assertEquals(0, BigDecimal.ZERO.compareTo(restored.greenCoins()));
        assertEquals(0, new BigDecimal("14.4375").compareTo(restored.progress().currentXp()));
        assertEquals(java.util.List.of(lot), restored.inventory().produceLots());
        assertEquals(session.state().vip(), restored.vip());
    }

    @Test void plantedOriginSurvivesSaveWateringAndHarvest() throws IOException {
        ManualClock clock = new ManualClock();
        var catalog = CropCatalogLoader.load();
        var repository = new JsonSaveRepository(directory.resolve("crop.json"));
        var session = GameSession.open(clock, catalog, repository, bound -> 99);
        ItemLot origin = new ItemLot("nabo", Currency.GREEN, new BigDecimal("9"), 1, true);
        var state = session.state();
        state.inventory().addSeedLot(origin);
        state.selectTool(br.com.colheitafeliz.game.Tool.HOE);
        assertTrue(state.act(0, 0).success());
        state.selectSeed(catalog.get("nabo"));
        assertTrue(state.act(0, 0).success());
        state.selectTool(br.com.colheitafeliz.game.Tool.WATER);
        assertTrue(state.act(0, 0).success());
        session.save();
        clock.advanceSeconds(60_000);
        state = GameSession.open(clock, catalog, repository, bound -> 99).state();
        state.selectTool(br.com.colheitafeliz.game.Tool.HAND);
        assertTrue(state.act(0, 0).success());
        assertEquals(java.util.List.of(origin), state.inventory().produceLots());
        assertTrue(state.depositTotals().isEmpty());
        state.wallet().credit(Currency.GREEN, new BigDecimal("95"));
        assertTrue(state.buyVip().success());
        assertEquals(0, new BigDecimal("11.3").compareTo(state.depositTotals().get(Currency.GREEN)));
    }

    @Test void collectedAnimalBaseSurvivesSave() throws IOException {
        var clock = new ManualClock();
        var catalog = CropCatalogLoader.load();
        var repository = new JsonSaveRepository(directory.resolve("animal.json"));
        var session = GameSession.open(clock, catalog, repository, bound -> 99);
        var state = session.state();
        state.progress().award(125);
        var hen = state.animalType("galinha");
        assertTrue(state.buyAnimal(hen).success());
        assertTrue(state.buyFeed(hen).success());
        assertTrue(state.tendAnimal(0).success());
        clock.advanceSeconds(hen.productionSeconds());
        assertTrue(state.tendAnimal(0).success());
        session.save();
        var restored = GameSession.open(clock, catalog, repository, bound -> 99).state();
        assertEquals(0, new BigDecimal("45").compareTo(restored.inventory().produceLots().getFirst().unitCost()));
        assertEquals(Currency.GOLD, restored.inventory().produceLots().getFirst().currency());
        assertEquals(0, new BigDecimal("155.3").compareTo(restored.depositValue()));
    }

    @Test void legacyAndFutureFilesAreNeverImportedOrOverwritten() throws IOException {
        var clock = new ManualClock();
        var session = GameSession.open(clock, CropCatalogLoader.load(),
                new JsonSaveRepository(directory.resolve("fresh.json")), bound -> 99);
        for (int version : new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 18, 99}) {
            Path file = directory.resolve("legacy-" + version + ".json");
            String bytes = "{\"version\":" + version + ",\"snapshot\":{\"coins\":99999}}";
            Files.writeString(file, bytes);
            var repository = new JsonSaveRepository(file);
            assertThrows(IOException.class, repository::load);
            assertThrows(IOException.class, () -> repository.save(session.state().snapshot(), clock.instant(), clock.instant()));
            assertEquals(bytes, Files.readString(file));
        }
    }

    @Test void saveRefusesFileReplacedAfterOpen() throws IOException {
        var clock = new ManualClock();
        Path file = directory.resolve("replaced.json");
        var catalog = CropCatalogLoader.load();
        var session = GameSession.open(clock, catalog, new JsonSaveRepository(file), bound -> 99);
        assertTrue(session.trySave());
        for (String replacement : new String[]{"{\"version\":14}", "not-json"}) {
            Files.writeString(file, replacement);
            assertFalse(session.trySave());
            assertTrue(session.state().message().startsWith("Erro ao salvar"));
            assertEquals(replacement, Files.readString(file));
            assertThrows(IOException.class, () -> new JsonSaveRepository(file).save(
                    session.state().snapshot(), clock.instant(), clock.instant()));
            assertEquals(replacement, Files.readString(file));
        }
    }

    @Test void firstEconomySaveIsRefusedAndPreservedAfterTheCatalogChange() throws IOException {
        Path file = directory.resolve("economy-v15.json");
        var clock = new ManualClock();
        var session = GameSession.open(clock, CropCatalogLoader.load(), new JsonSaveRepository(file), bound -> 99);
        session.save();
        var json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        json.addProperty("version", 15);
        Files.writeString(file, json.toString());
        String before = Files.readString(file);
        IOException refused = assertThrows(IOException.class, () -> new JsonSaveRepository(file).load());
        assertTrue(refused.getMessage().startsWith("Incompatible save"), refused.getMessage());
        assertEquals(before, Files.readString(file));
    }

    @Test void defaultPathChangesWithTheFormatSoTheFirstEconomySaveDoesNotBlockTheGame() {
        // A main da etapa 1 grava v15 em game-rules-v1.json; o v16 abre num arquivo novo e deixa aquele intacto.
        assertNotEquals("save/game-rules-v1.json", GameSave.DEFAULT_PATH);
        assertEquals("save/game-rules-v2.json", GameSave.DEFAULT_PATH);
    }

    @Test void malformedNewSaveIsNotAFreshGame() throws IOException {
        Path file = directory.resolve("invalid.json");
        Files.writeString(file, "{\"version\":17,\"ruleset\":\"economy-2026-10\",\"snapshot\":{}}");
        String before = Files.readString(file);
        var clock = new ManualClock();
        assertThrows(IOException.class, () -> GameSession.open(clock, CropCatalogLoader.load(), new JsonSaveRepository(file)));
        assertEquals(before, Files.readString(file));
    }

    @Test void stage2ReaderRefusesAndPreservesTheV17SaveWithFertilizerStock() throws IOException {
        var clock = new ManualClock();
        Path file = directory.resolve("stock.json");
        var repository = new JsonSaveRepository(file);
        var session = GameSession.open(clock, CropCatalogLoader.load(), repository, bound -> 99);
        var state = session.state();
        while (state.progress().level() < 10)
            state.progress().award(state.progress().xpToNext().subtract(state.progress().currentXp()));
        assertTrue(state.buyFertilizer("comum", Currency.GOLD).success());
        session.save();
        String bytes = Files.readString(file);
        int stock = state.inventory().fertilizer("comum");
        assertEquals(1, stock);
        var stage2 = new br.com.colheitafeliz.persistence.stage2.Stage2JsonSaveRepository(file);
        assertThrows(IOException.class, stage2::load);
        IOException refused = assertThrows(IOException.class,
                () -> stage2.save(state.snapshot(), clock.instant(), clock.instant()));
        assertTrue(refused.getMessage().startsWith("Incompatible save"), refused.getMessage());
        assertEquals(bytes, Files.readString(file));
        var restored = GameSession.open(clock, CropCatalogLoader.load(), repository, bound -> 99).state();
        assertEquals(stock, restored.inventory().fertilizer("comum"));
    }

    private Path stage2Save(String name, int fertilizer) throws IOException {
        Path file = directory.resolve(name);
        GameSession.open(new ManualClock(), CropCatalogLoader.load(), new JsonSaveRepository(file), bound -> 99).save();
        var json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        json.addProperty("version", 16);
        var snapshot = json.getAsJsonObject("snapshot");
        snapshot.remove("fertilizers");
        snapshot.remove("selectedFertilizerId");
        snapshot.addProperty("fertilizer", fertilizer);
        Files.writeString(file, json.toString());
        return file;
    }

    @Test void stage2SaveV16WithoutFertilizerLoadsAndIsUpgradedOnTheNextSave() throws IOException {
        Path file = stage2Save("stage2.json", 0);
        assertTrue(new br.com.colheitafeliz.persistence.stage2.Stage2JsonSaveRepository(file).load().isPresent());
        var session = GameSession.open(new ManualClock(), CropCatalogLoader.load(), new JsonSaveRepository(file), bound -> 99);
        assertTrue(session.trySave());
        assertEquals(17, JsonParser.parseString(Files.readString(file)).getAsJsonObject().get("version").getAsInt());
    }

    @Test void stage2SaveV16WithFertilizerStockIsRefusedAndPreserved() throws IOException {
        Path file = stage2Save("stage2-stock.json", 3);
        String before = Files.readString(file);
        var clock = new ManualClock();
        var repository = new JsonSaveRepository(file);
        IOException refused = assertThrows(IOException.class, repository::load);
        assertTrue(refused.getMessage().startsWith("Incompatible save"), refused.getMessage());
        assertThrows(IOException.class, () -> GameSession.open(clock, CropCatalogLoader.load(), repository, bound -> 99));
        assertEquals(before, Files.readString(file));
    }
}
