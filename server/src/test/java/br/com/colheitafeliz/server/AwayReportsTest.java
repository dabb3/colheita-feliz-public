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
        var ledger = AwayLedger.empty(ago(4 * 3600))
                .withEvent(new AwayLedger.Event("THEFT", ago(3600).toString(), "Bob", "cenoura", 1, null))
                .withEvent(new AwayLedger.Event("DEFENDED", ago(1800).toString(), "Carol", null, 0, null));
        var report = AwayReports.build(game, ledger, clock.instant());
        var theft = report.getAsJsonArray("thefts").get(0).getAsJsonObject();
        assertEquals(List.of("Bob", "cenoura", "Cenoura", 1), List.of(theft.get("actor").getAsString(), theft.get("itemId").getAsString(),
                theft.get("name").getAsString(), theft.get("quantity").getAsInt()));
        var defense = report.getAsJsonArray("defenses").get(0).getAsJsonObject();
        assertEquals("Carol", defense.get("actor").getAsString());
        assertFalse(defense.has("protectorName"), "plot protection is collective, so no animal is credited");
    }

    @Test void unknownCatalogIdsNeverBreakTheFarm() {
        var ledger = AwayLedger.empty(ago(4 * 3600))
                .withEvent(new AwayLedger.Event("THEFT", ago(3600).toString(), "Bob", "fruta-removida", 1, null))
                .withEvent(new AwayLedger.Event("DEFENDED", ago(1800).toString(), "Carol", null, 0, "protetor-removido"));
        var report = assertDoesNotThrow(() -> AwayReports.build(game, ledger, clock.instant()));
        assertEquals(0, report.getAsJsonArray("thefts").size());
        assertEquals(1, report.getAsJsonArray("defenses").size());
    }

    private AnimalType producer() { return game.animalTypes().stream().filter(t -> t.kind() == AnimalType.Kind.PRODUCER).findFirst().orElseThrow(); }
    /** A producer whose product became ready one hour ago and is still waiting in the barn. */
    private AnimalType readyProducer() {
        var type = producer(); var at = ago(type.requiredMillis() / 1000 + 3600);
        game.barn().add(Animal.acquire(type, new ItemLot(type.id(), Currency.GOLD, type.prices().get(Currency.GOLD), 1, false), at, at));
        return type;
    }
    private static int quantity(com.google.gson.JsonObject report, String list) {
        return report.getAsJsonArray(list).asList().stream().mapToInt(item -> item.getAsJsonObject().get("quantity").getAsInt()).sum();
    }

    @Test void readyProducerIsNotReportedAsCollected() {
        var type = readyProducer();
        var report = AwayReports.build(game, AwayLedger.empty(ago(3 * 3600)), clock.instant());
        assertEquals(0, quantity(report, "products"));
        assertEquals(1, quantity(report, "readyProducts"));
        assertEquals(type.productId(), report.getAsJsonArray("readyProducts").get(0).getAsJsonObject().get("productId").getAsString());
    }

    @Test void collectedProductsGoToDepositList() {
        var ledger = AwayLedger.empty(ago(3 * 3600)).plus(0, 0, 0, java.util.Map.of(producer().productId(), 2));
        var report = AwayReports.build(game, ledger, clock.instant());
        assertEquals(2, quantity(report, "products"));
        assertEquals(0, quantity(report, "readyProducts"));
    }

    @Test void collectedAndReadyStayApart() {
        readyProducer();
        var ledger = AwayLedger.empty(ago(3 * 3600)).plus(0, 0, 0, java.util.Map.of(producer().productId(), 2));
        var report = AwayReports.build(game, ledger, clock.instant());
        assertEquals(2, quantity(report, "products"));
        assertEquals(1, quantity(report, "readyProducts"));
    }

    private void expiredAutomation(long secondsAgo) {
        game.wallet().credit(Currency.GREEN, new BigDecimal("1000"));
        game.buyVip(); game.buyAutomation("water");
        clock.advance(30L * 86400 + secondsAgo);
    }
    private static boolean hasAutomation(com.google.gson.JsonObject report) {
        return report.getAsJsonArray("pending").asList().stream().anyMatch(p -> p.getAsJsonObject().get("kind").getAsString().equals("AUTOMATION"));
    }

    @Test void pendingOpenedBeforeThisAbsenceStillShowsWithNews() {
        expiredAutomation(10 * 3600);
        matured(0, ago(3600));
        var report = AwayReports.build(game, AwayLedger.empty(ago(3 * 3600)), clock.instant());
        assertTrue(hasAutomation(report), "an automation that expired before the last visit is still open");
    }

    @Test void oldPendingAloneDoesNotOpenAReport() {
        expiredAutomation(10 * 3600);
        assertNull(AwayReports.build(game, AwayLedger.empty(ago(3 * 3600)), clock.instant()));
    }

    @Test void pendingOpenedInThisAbsenceOpensAReport() {
        expiredAutomation(3600);
        var report = AwayReports.build(game, AwayLedger.empty(ago(3 * 3600)), clock.instant());
        assertNotNull(report); assertTrue(hasAutomation(report));
    }

    @Test void oldHungryProtectorStillShowsWithNews() {
        var dog = game.animalTypes().stream().filter(t -> t.kind() == AnimalType.Kind.PROTECTOR).findFirst().orElseThrow();
        var at = ago(30 * 3600);
        game.barn().add(Animal.acquire(dog, new ItemLot(dog.id(), Currency.GOLD, dog.prices().get(Currency.GOLD), 1, false), at, at));
        matured(0, ago(3600));
        var report = AwayReports.build(game, AwayLedger.empty(ago(3 * 3600)), clock.instant());
        assertTrue(report.getAsJsonArray("pending").asList().stream().anyMatch(p -> p.getAsJsonObject().get("kind").getAsString().equals("FEED")));
    }

    @Test void mergeAddsHistoryAndRereadsTheFarm() {
        var older = com.google.gson.JsonParser.parseString("""
                {"awaySeconds":10800,"harvests":[{"cropId":"cenoura","name":"Cenoura","stem":"cenoura","quantity":2}],
                 "products":[{"productId":"ovo","name":"Ovo","animalId":"galinha","quantity":1}],"readyProducts":[],
                 "thefts":[{"at":"2026-11-13T01:00:00Z","actor":"Bob","itemId":"cenoura","name":"Cenoura","stem":"cenoura","quantity":1}],
                 "defenses":[],"care":{"waterings":3,"pestsRemoved":1,"animalCares":0},
                 "pending":[{"kind":"VIP","expiredAt":"2026-11-13T00:00:00Z"}],"windows":[],"id":"old"}""").getAsJsonObject();
        var newer = com.google.gson.JsonParser.parseString("""
                {"awaySeconds":14400,"harvests":[],"products":[{"productId":"ovo","name":"Ovo","animalId":"galinha","quantity":2}],
                 "readyProducts":[],"thefts":[],"defenses":[{"at":"2026-11-13T09:00:00Z","actor":"Carol"}],
                 "care":{"waterings":2,"pestsRemoved":0,"animalCares":5},"pending":[],"windows":[]}""").getAsJsonObject();
        var merged = AwayReports.merge(older, newer, game);
        assertEquals(25200, merged.get("awaySeconds").getAsLong());
        assertEquals(3, quantity(merged, "products"));
        assertEquals(0, quantity(merged, "harvests"), "ready crops are read from the farm, not added up");
        assertEquals(1, merged.getAsJsonArray("thefts").size()); assertEquals(1, merged.getAsJsonArray("defenses").size());
        assertEquals(5, merged.getAsJsonObject("care").get("waterings").getAsInt());
        assertEquals(5, merged.getAsJsonObject("care").get("animalCares").getAsInt());
        assertEquals(0, merged.getAsJsonArray("pending").size(), "pendings reflect the farm now");
        assertFalse(merged.has("id"));
    }

    @Test void productReadyInAnOlderReportDisappearsOnceCollected() {
        readyProducer(); // ready an hour ago, inside the older report's window
        var older = AwayReports.build(game, AwayLedger.empty(ago(3 * 3600)), clock.instant());
        assertEquals(1, quantity(older, "readyProducts"));
        game.barn().collect(0, clock.instant());
        clock.advance(4 * 3600);
        var newer = AwayReports.build(game, AwayLedger.empty(ago(3 * 3600)).plus(1, 0, 0, java.util.Map.of()), clock.instant());
        var merged = AwayReports.merge(older, newer, game);
        assertEquals(0, quantity(merged, "readyProducts"));
    }

    @Test void productReadyInAnOlderReportStaysWhileStillInTheBarn() {
        readyProducer();
        var older = AwayReports.build(game, AwayLedger.empty(ago(3 * 3600)), clock.instant());
        clock.advance(4 * 3600);
        var newer = AwayReports.build(game, AwayLedger.empty(ago(3 * 3600)).plus(1, 0, 0, java.util.Map.of()), clock.instant());
        var merged = AwayReports.merge(older, newer, game);
        assertEquals(1, quantity(merged, "readyProducts"));
    }

    @Test void cropReadyBetweenTheTwoAbsencesIsNotCredited() {
        var older = AwayReports.build(game, AwayLedger.empty(ago(10 * 3600)).plus(1, 0, 0, java.util.Map.of()), ago(7 * 3600));
        matured(0, ago(5 * 3600)); // the owner was around then
        matured(1, ago(3600));
        var newer = AwayReports.build(game, AwayLedger.empty(ago(3 * 3600)), clock.instant());
        var merged = AwayReports.merge(older, newer, game);
        assertEquals(1, quantity(merged, "harvests"));
    }

    @Test void mergeKeepsTheNewestTwentyVisitEventsOverall() {
        var base = clock.instant();
        var older = new com.google.gson.JsonObject[1]; var newer = new com.google.gson.JsonObject[1];
        for (int part = 0; part < 2; part++) {
            var ledger = AwayLedger.empty(base.minusSeconds(100_000));
            for (int i = 0; i < 8; i++) {
                // 16 events per report, thefts and defenses alternating: 32 in all, so only the merge cap applies.
                long minute = part * 100 + i * 2;
                ledger = ledger.withEvent(new AwayLedger.Event("THEFT", base.minusSeconds(90_000 - minute * 60).toString(), "Bob", "cenoura", 1, null))
                        .withEvent(new AwayLedger.Event("DEFENDED", base.minusSeconds(90_000 - (minute + 1) * 60).toString(), "Carol", null, 0, null));
            }
            var report = AwayReports.build(game, ledger, base);
            if (part == 0) older[0] = report; else newer[0] = report;
        }
        var merged = AwayReports.merge(older[0], newer[0], game);
        var all = new java.util.ArrayList<Instant>();
        for (String kind : List.of("thefts", "defenses"))
            merged.getAsJsonArray(kind).forEach(e -> all.add(Instant.parse(e.getAsJsonObject().get("at").getAsString())));
        assertEquals(AwayLedger.MAX_EVENTS, all.size());
        var everything = new java.util.ArrayList<Instant>();
        for (var report : List.of(older[0], newer[0])) for (String kind : List.of("thefts", "defenses"))
            report.getAsJsonArray(kind).forEach(e -> everything.add(Instant.parse(e.getAsJsonObject().get("at").getAsString())));
        everything.sort(null); all.sort(null);
        assertEquals(everything.subList(everything.size() - AwayLedger.MAX_EVENTS, everything.size()), all);
    }
}
