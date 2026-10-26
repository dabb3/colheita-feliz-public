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
        assertEquals(16, json.get("version").getAsInt());
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
        for (int version : new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 17, 99}) {
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

    @Test void malformedNewSaveIsNotAFreshGame() throws IOException {
        Path file = directory.resolve("invalid.json");
        Files.writeString(file, "{\"version\":16,\"ruleset\":\"economy-2026-10\",\"snapshot\":{}}");
        String before = Files.readString(file);
        var clock = new ManualClock();
        assertThrows(IOException.class, () -> GameSession.open(clock, CropCatalogLoader.load(), new JsonSaveRepository(file)));
        assertEquals(before, Files.readString(file));
    }
}
