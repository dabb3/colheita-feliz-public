package br.com.colheitafeliz.server;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LatencyBenchmarkIntegrationTest {
    @Test void syntheticFixturesSupportSuccessfulTileAndSocialActions() {
        for (String action : List.of("PLOW", "PLANT", "WATER", "WEED", "PESTICIDE", "FERTILIZE", "HARVEST")) {
            var game = LatencyBenchmark.fixture(action);
            var fixed = java.time.Clock.fixed(game.now(), java.time.ZoneOffset.UTC);
            var restored = br.com.colheitafeliz.game.GameState.restore(fixed::instant, fixed,
                    br.com.colheitafeliz.game.CropCatalogLoader.load(), game.snapshot(), bound -> bound - 1);
            restored.selectTool(switch (action) {
                case "PLOW" -> br.com.colheitafeliz.game.Tool.HOE;
                case "PLANT" -> br.com.colheitafeliz.game.Tool.SEED;
                case "WATER" -> br.com.colheitafeliz.game.Tool.WATER;
                case "WEED" -> br.com.colheitafeliz.game.Tool.WEEDER;
                case "PESTICIDE" -> br.com.colheitafeliz.game.Tool.PESTICIDE;
                case "FERTILIZE" -> br.com.colheitafeliz.game.Tool.FERTILIZER;
                default -> br.com.colheitafeliz.game.Tool.HAND;
            });
            if (action.equals("FERTILIZE")) restored.selectFertilizer("comum");
            var result = restored.act(0, 0);
            assertTrue(result.success(), action + ": " + result.message());
        }
        var visits = new br.com.colheitafeliz.game.VisitService();
        assertTrue(visits.steal(LatencyBenchmark.fixture("STEAL"), LatencyBenchmark.fixture("STEAL"), 0, 0, 1).success());
        assertTrue(visits.fertilizeVisit(LatencyBenchmark.fixture("LOVE"), LatencyBenchmark.fixture("LOVE"), 0, 0, "amor", 1).success());
    }

    @Test void syntheticAnimalFixturesSupportFeedingCollectingAndSale() {
        for (String label : List.of("TEND_ANIMAL_FEED", "TEND_ANIMAL_COLLECT", "SELL_ANIMAL")) {
            var game = LatencyBenchmark.fixture(label);
            var result = label.equals("SELL_ANIMAL") ? game.sellAnimal(0) : game.tendAnimal(0);
            assertTrue(result.success(), label + ": " + result.message());
        }
    }
    @Test void syntheticShopFixturesSupportPurchasesAndSales() {
        for (String action : List.of("BUY_SEED", "BUY_ANIMAL", "BUY_FEED", "BUY_FERTILIZER", "BUY_VIP", "BUY_PLOT", "SELL_ITEM", "SELL_ITEM_ALL", "SELL_ALL")) {
            var game = LatencyBenchmark.fixture(action);
            var result = switch (action) {
                case "BUY_SEED" -> game.buySeed(game.catalog().get("nabo"));
                case "BUY_ANIMAL" -> game.buyAnimal(game.animalTypes().stream()
                        .filter(type -> type.kind() == br.com.colheitafeliz.domain.animal.AnimalType.Kind.PRODUCER).findFirst().orElseThrow());
                case "BUY_FEED" -> game.buyFeed("producer");
                case "BUY_FERTILIZER" -> game.buyFertilizer("comum", br.com.colheitafeliz.domain.economy.Currency.GOLD);
                case "BUY_VIP" -> game.buyVip();
                case "BUY_PLOT" -> game.buyPlot(game.land().next().column(), game.land().next().row());
                case "SELL_ITEM" -> game.sellProduce(game.catalog().get("nabo"));
                case "SELL_ITEM_ALL" -> game.sellAllProduce(game.catalog().get("nabo"));
                default -> game.sellEverything();
            };
            assertTrue(result.success(), action + ": " + result.message());
        }
    }
    @Tag("integration")
    @Test void successfulScenariosHaveThreeSamplesAndLeaveNoFixtures() {
        String url = System.getenv("TEST_DATABASE_URL");
        assertNotNull(url, "Requires disposable localhost colheita_test PostgreSQL");
        assertTrue(url.matches("jdbc:postgresql://(127\\.0\\.0\\.1|localhost):[0-9]+/colheita_test(\\?.*)?"));
        String schema = "test_" + UUID.randomUUID().toString().replace("-", "");
        UUID sentinel = UUID.randomUUID();
        try (var store = new JdbcOnlineStore(url, "postgres", "", schema)) {
            store.initialize(sentinel);
            List<LatencyBenchmark.Measurement> measurements = LatencyBenchmark.run(store);
            assertEquals(24, measurements.size());
            for (var measurement : measurements) {
                assertEquals(3, measurement.samplesMs().size(), measurement.label());
                assertTrue(measurement.samplesMs().stream().allMatch(value -> Double.isFinite(value) && value >= 0));
            }
            store.transaction(sentinel, tx -> {
                assertEquals(1, tx.query("SELECT count(*) FROM " + schema + ".players", row -> row.getInt(1)).getFirst());
                for (String table : List.of("farms", "receipts", "friendships", "invitations"))
                    assertEquals(0, tx.query("SELECT count(*) FROM " + schema + "." + table, row -> row.getInt(1)).getFirst());
                tx.execute("DROP SCHEMA " + schema + " CASCADE");
                return null;
            });
        }
    }
}



