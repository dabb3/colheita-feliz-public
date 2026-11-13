package br.com.colheitafeliz.server;

import br.com.colheitafeliz.domain.animal.Animal;
import br.com.colheitafeliz.domain.animal.AnimalType;
import br.com.colheitafeliz.domain.crop.Crop;
import br.com.colheitafeliz.domain.economy.Currency;
import br.com.colheitafeliz.domain.farm.PestState;
import br.com.colheitafeliz.domain.farm.TileState;
import br.com.colheitafeliz.domain.inventory.ItemLot;
import br.com.colheitafeliz.game.CropCatalogLoader;
import br.com.colheitafeliz.game.GameState;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AwayReportsTest {
    private final ProtectorBiteIntegrationTest.TestClock clock = new ProtectorBiteIntegrationTest.TestClock();
    private final GameState game = new GameState(clock::instant, clock, CropCatalogLoader.load(), n -> 99);

    private Instant ago(long seconds) { return clock.instant().minusSeconds(seconds); }
    private void matured(int column, Instant at) {
        var type = game.catalog().get("cenoura");
        var origin = type.plantingOrigin(new ItemLot(type.id(), Currency.GOLD, type.seedPrices().get(Currency.GOLD), 1, false));
        var crop = new Crop(type, at, origin).water(at).advance(at, type.growthTime(1).toMillis());
        var tile = game.farm().tile(column, 0);
        tile.restore(TileState.READY, crop, PestState.NONE, null, 0, null, null, false, tile.harvestRevision() + 1);
    }

    @Test void nothingToShowReturnsNull() {
        assertNull(AwayReports.build(game, AwayLedger.empty(ago(4 * 3600)), clock.instant()));
    }

    @Test void harvestsOnlyCountCropsMaturedInWindow() {
        matured(0, ago(3600)); matured(1, ago(5 * 3600));
        var report = AwayReports.build(game, AwayLedger.empty(ago(3 * 3600)), clock.instant());
        var harvests = report.getAsJsonArray("harvests");
        assertEquals(1, harvests.size());
        var item = harvests.get(0).getAsJsonObject();
        assertEquals("cenoura", item.get("cropId").getAsString());
        assertEquals(1, item.get("quantity").getAsInt());
        assertEquals(3 * 3600, report.get("awaySeconds").getAsLong());
    }

    @Test void pendingAutomationOnlyWhenStillExpired() {
        game.wallet().credit(Currency.GREEN, new BigDecimal("1000"));
        game.buyVip(); game.buyAutomation("water");
        clock.advance(30L * 86400 + 3600);
        var report = AwayReports.build(game, AwayLedger.empty(ago(4 * 3600)), clock.instant());
        var pending = report.getAsJsonArray("pending");
        assertTrue(pending.asList().stream().anyMatch(p -> p.getAsJsonObject().get("kind").getAsString().equals("AUTOMATION")
                && p.getAsJsonObject().get("serviceId").getAsString().equals("water")
                && p.getAsJsonObject().get("name").getAsString().equals("Rega automática")));
        game.buyVip(); game.buyAutomation("water");
        var renewed = AwayReports.build(game, AwayLedger.empty(ago(4 * 3600)), clock.instant());
        assertTrue(renewed == null || renewed.getAsJsonArray("pending").asList().stream()
                .noneMatch(p -> p.getAsJsonObject().get("kind").getAsString().equals("AUTOMATION")));
    }

    @Test void feedPendingUsesBothFeedStocks() {
        var dog = game.animalTypes().stream().filter(t -> t.kind() == AnimalType.Kind.PROTECTOR).findFirst().orElseThrow();
        var at = ago(8 * 3600);
        game.barn().add(Animal.acquire(dog, new ItemLot(dog.id(), Currency.GOLD, dog.prices().get(Currency.GOLD), 1, false), at, at));
        game.inventory().addFeed("growth", 2); game.inventory().addFeed("producer", 2);
        var feed = AwayReports.build(game, AwayLedger.empty(ago(3 * 3600)), clock.instant()).getAsJsonArray("pending").get(0).getAsJsonObject();
        assertEquals("FEED", feed.get("kind").getAsString());
        assertEquals(dog.id(), feed.get("animalId").getAsString());
        assertEquals(dog.name(), feed.get("name").getAsString());
        assertEquals(4, feed.get("stock").getAsInt());
    }

    @Test void eventsSplitIntoTheftsAndDefenses() {
        var dog = game.animalTypes().stream().filter(t -> t.kind() == AnimalType.Kind.PROTECTOR).findFirst().orElseThrow();
        var ledger = AwayLedger.empty(ago(4 * 3600))
                .withEvent(new AwayLedger.Event("THEFT", ago(3600).toString(), "Bob", "cenoura", 1, null))
                .withEvent(new AwayLedger.Event("DEFENDED", ago(1800).toString(), "Carol", null, 0, dog.id()));
        var report = AwayReports.build(game, ledger, clock.instant());
        var theft = report.getAsJsonArray("thefts").get(0).getAsJsonObject();
        assertEquals(List.of("Bob", "cenoura", "Cenoura", 1), List.of(theft.get("actor").getAsString(), theft.get("itemId").getAsString(),
                theft.get("name").getAsString(), theft.get("quantity").getAsInt()));
        var defense = report.getAsJsonArray("defenses").get(0).getAsJsonObject();
        assertEquals("Carol", defense.get("actor").getAsString());
        assertEquals(dog.name(), defense.get("protectorName").getAsString());
    }
}
