package br.com.colheitafeliz.persistence;

import br.com.colheitafeliz.domain.economy.Currency;
import br.com.colheitafeliz.domain.inventory.ItemLot;
import br.com.colheitafeliz.game.CropCatalogLoader;
import br.com.colheitafeliz.game.GameSession;
import br.com.colheitafeliz.testsupport.ManualClock;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class CropSaleCorruptionTest {
    @TempDir Path directory;
    private final ManualClock clock = new ManualClock();

    private String validSave(Path path, String id) throws IOException {
        var session = GameSession.open(clock, CropCatalogLoader.load(), new JsonSaveRepository(path), bound -> 99);
        var crop = session.state().catalog().get(id);
        var price = crop.seedPrice();
        var origin = new ItemLot(id, price.getKey(), price.getValue(), 1, crop.vipOnly());
        session.state().inventory().addProduceLot(crop.plantingOrigin(origin).harvested(1));
        session.save();
        return Files.readString(path);
    }

    private record Corruption(String name, Consumer<JsonObject> mutate) {}

    @Test void inconsistentOrIncompleteV1NeverLoadsAndNeverOverwritesTheInput() throws IOException {
        Path path = directory.resolve("corrupt.json");
        String valid = validSave(path, "coco");
        var validSnapshot = new com.google.gson.Gson().fromJson(valid, GameSave.class).snapshot();
        List<Corruption> cases = List.of(
                new Corruption("seasonCount", lot -> terms(lot).addProperty("seasons", 1)),
                new Corruption("seasonCountMissing", lot -> terms(lot).remove("seasons")),
                new Corruption("indexZero", lot -> terms(lot).addProperty("seasonIndex", 0)),
                new Corruption("indexTooLarge", lot -> terms(lot).addProperty("seasonIndex", 7)),
                new Corruption("fractionalIndex", lot -> terms(lot).addProperty("seasonIndex", new BigDecimal("1.9"))),
                new Corruption("overflowIndex", lot -> terms(lot).addProperty("seasonIndex", 4294967297L)),
                new Corruption("fractionalVersion", lot -> terms(lot).addProperty("economyVersion", new BigDecimal("1.9"))),
                new Corruption("unknownVersion", lot -> terms(lot).addProperty("economyVersion", 2)),
                new Corruption("missingVersion", lot -> terms(lot).remove("economyVersion")),
                new Corruption("revenueMismatch", lot -> terms(lot).addProperty("cycleRevenueTenths", 331)),
                new Corruption("fractionalRevenue", lot -> terms(lot).addProperty("cycleRevenueTenths", new BigDecimal("330.9"))),
                new Corruption("negativeRevenue", lot -> terms(lot).addProperty("cycleRevenueTenths", -330)),
                new Corruption("missingRevenue", lot -> terms(lot).remove("cycleRevenueTenths")),
                new Corruption("missingBonus", lot -> terms(lot).remove("cycleVipBonusTenths")),
                new Corruption("extraBonus", lot -> terms(lot).addProperty("cycleVipBonusTenths", 1)),
                new Corruption("wrongCurrency", lot -> lot.addProperty("currency", "GOLD")),
                new Corruption("wrongCost", lot -> lot.addProperty("unitCost", 21)),
                new Corruption("vipFalse", lot -> lot.addProperty("vipOnly", false)),
                new Corruption("vipMissing", lot -> lot.remove("vipOnly")),
                new Corruption("wrongCrop", lot -> lot.addProperty("itemId", "amendoim")),
                new Corruption("missingTerms", lot -> lot.remove("cropSale")),
                new Corruption("nullTerms", lot -> lot.add("cropSale", com.google.gson.JsonNull.INSTANCE)),
                new Corruption("wrongMarker", lot -> lot.addProperty("economyVersion", 0)),
                new Corruption("fractionalQuantity", lot -> lot.addProperty("quantity", new BigDecimal("1.9"))),
                new Corruption("overflowQuantity", lot -> lot.addProperty("quantity", 4294967297L)),
                new Corruption("stringInteger", lot -> terms(lot).addProperty("seasons", "6")));
        for (var corruption : cases) {
            var json = JsonParser.parseString(valid).getAsJsonObject();
            corruption.mutate().accept(json.getAsJsonObject("snapshot").getAsJsonArray("produce").get(0).getAsJsonObject());
            Files.writeString(path, json.toString());
            byte[] before = Files.readAllBytes(path);
            assertThrows(IOException.class, () -> GameSession.open(clock, CropCatalogLoader.load(), new JsonSaveRepository(path), bound -> 99), corruption.name());
            var repository = new JsonSaveRepository(path);
            assertThrows(IOException.class, repository::load, corruption.name());
            assertThrows(IOException.class, () -> repository.save(validSnapshot, clock.instant(), clock.instant()), corruption.name());
            assertArrayEquals(before, Files.readAllBytes(path), corruption.name());
        }
    }

    @Test void commonBonusMustMatchCostButLegacyLotsRemainValid() throws IOException {
        Path path = directory.resolve("common.json");
        String valid = validSave(path, "tomate");
        var json = JsonParser.parseString(valid).getAsJsonObject();
        var lot = json.getAsJsonObject("snapshot").getAsJsonArray("produce").get(0).getAsJsonObject();
        terms(lot).addProperty("cycleVipBonusTenths", 0);
        Files.writeString(path, json.toString());
        assertThrows(IOException.class, () -> GameSession.open(clock, CropCatalogLoader.load(), new JsonSaveRepository(path), bound -> 99));
        lot.remove("cropSale"); lot.remove("economyVersion");
        json.addProperty("ruleset", GameSave.PREVIOUS_RULESET);
        Files.writeString(path, json.toString());
        var state = GameSession.open(clock, CropCatalogLoader.load(), new JsonSaveRepository(path), bound -> 99).state();
        assertEquals(new BigDecimal("1092.5"), state.depositTotals().get(Currency.GOLD));
        assertEquals(0, state.inventory().produceLots().getFirst().economyVersion());
    }

    private static JsonObject terms(JsonObject lot) { return lot.getAsJsonObject("cropSale"); }

    @Test void completeDraftV1WithoutOuterMarkerRemainsReadable() throws IOException {
        Path path = directory.resolve("draft.json");
        var json = JsonParser.parseString(validSave(path, "tomate")).getAsJsonObject();
        json.getAsJsonObject("snapshot").getAsJsonArray("produce").get(0).getAsJsonObject().remove("economyVersion");
        Files.writeString(path, json.toString());
        var session = GameSession.open(clock, CropCatalogLoader.load(), new JsonSaveRepository(path), bound -> 99);
        assertEquals(1, session.state().inventory().produceLots().getFirst().economyVersion());
        assertEquals(new BigDecimal("679.0"), session.state().depositTotals().get(Currency.GOLD));
        session.save();
        var written = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        assertEquals(1, written.getAsJsonObject("snapshot").getAsJsonArray("produce").get(0).getAsJsonObject().get("economyVersion").getAsInt());
    }
}
