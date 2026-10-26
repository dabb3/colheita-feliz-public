package br.com.colheitafeliz.game;

import br.com.colheitafeliz.domain.crop.CropType;
import br.com.colheitafeliz.domain.farm.TileState;
import br.com.colheitafeliz.testsupport.ManualClock;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GameStateTest {
    @Test void completeFarmCycleStoresProduceUntilSold() {
        ManualClock clock = new ManualClock();
        GameState game = new GameState(clock::instant, CropCatalogLoader.load(), bound -> 99);
        CropType corn = game.catalog().get("milho");
        assertEquals(0, java.math.BigDecimal.valueOf(500).compareTo(game.wallet().coins()));
        assertTrue(game.buySeed(corn).success());
        assertEquals(0, java.math.BigDecimal.valueOf(475).compareTo(game.wallet().coins()));
        game.selectSeed(corn);
        assertFalse(game.act(0, 0).success());
        game.selectTool(Tool.HOE);
        assertTrue(game.act(0, 0).success());
        game.selectSeed(corn);
        assertTrue(game.act(0, 0).success());
        assertEquals(0, game.inventory().seeds(corn));
        game.selectTool(Tool.WATER);
        assertTrue(game.act(0, 0).success());
        clock.advanceSeconds(900);
        assertTrue(game.act(0, 0).success());
        clock.advanceSeconds(900);
        assertTrue(game.act(0, 0).success());
        clock.advanceSeconds(900);
        game.refresh();
        assertEquals(TileState.READY, game.farm().tile(0, 0).state());
        game.selectTool(Tool.HAND);
        assertTrue(game.act(0, 0).success());
        assertEquals(1, game.inventory().produce(corn));
        // Watering three times may complete today's watering mission, so compare against the balance here.
        java.math.BigDecimal coins = game.wallet().coins();
        assertFalse(game.act(0, 0).success());
        assertTrue(game.sellProduce(corn).success());
        assertEquals(coins.add(new java.math.BigDecimal("28.8")), game.wallet().coins());
        assertEquals(0, game.inventory().produce(corn));
        assertFalse(game.sellAllProduce(corn).success());
        assertFalse(game.sellProduce(corn).success());
    }

    @Test void harvestLeavesADeadPlantThatOnlyTheHoeClears() {
        ManualClock clock = new ManualClock();
        GameState game = new GameState(clock::instant, clock, CropCatalogLoader.load(), bound -> 99);
        CropType turnip = game.catalog().get("nabo");
        game.buySeed(turnip);
        game.buySeed(turnip);
        game.selectTool(Tool.HOE);
        game.act(0, 0);
        game.selectSeed(turnip);
        game.act(0, 0);
        game.selectTool(Tool.WATER);
        game.act(0, 0);
        clock.advanceSeconds(900);
        game.selectTool(Tool.HAND);
        assertTrue(game.act(0, 0).success());
        assertEquals(TileState.DEAD, game.farm().tile(0, 0).state());
        game.selectSeed(turnip);
        assertFalse(game.act(0, 0).success());
        assertEquals("Retire a planta morta com a pá antes de plantar.", game.message());
        game.selectTool(Tool.HOE);
        java.math.BigDecimal xp = game.progress().currentXp();
        assertTrue(game.act(0, 0).success());
        assertEquals(xp.add(java.math.BigDecimal.valueOf(2)), game.progress().currentXp());
        assertEquals(TileState.PLOWED, game.farm().tile(0, 0).state());
        game.selectSeed(turnip);
        assertTrue(game.act(0, 0).success());
    }

    @Test void plowingGrassSometimesFindsATreasureChestThatGrowsWithLevel() {
        ManualClock clock = new ManualClock();
        GameState lucky = new GameState(clock::instant, clock, CropCatalogLoader.load(), bound -> 0);
        lucky.selectTool(Tool.HOE);
        assertTrue(lucky.act(0, 0).success());
        assertEquals(30, lucky.lastTreasureCoins());
        assertEquals(0, java.math.BigDecimal.valueOf(530).compareTo(lucky.wallet().coins()));
        assertTrue(lucky.message().contains("baú do tesouro"));
        lucky.progress().award(50);
        assertEquals(40, lucky.treasureCoins());

        GameState unlucky = new GameState(clock::instant, clock, CropCatalogLoader.load(), bound -> GameState.TREASURE_CHANCE);
        unlucky.selectTool(Tool.HOE);
        assertTrue(unlucky.act(0, 0).success());
        assertEquals(0, unlucky.lastTreasureCoins());
        assertEquals(0, java.math.BigDecimal.valueOf(500).compareTo(unlucky.wallet().coins()));
    }

    @Test void sellEverythingEmptiesTheDepositAtOnce() {
        ManualClock clock = new ManualClock();
        GameState game = new GameState(clock::instant, clock, CropCatalogLoader.load(), bound -> 99);
        assertFalse(game.sellEverything().success());
        game.inventory().addProduceLot(new br.com.colheitafeliz.domain.inventory.ItemLot("nabo", br.com.colheitafeliz.domain.economy.Currency.GOLD, java.math.BigDecimal.valueOf(game.catalog().get("nabo").seedPrice()), 2, false));
        game.inventory().addProduceLot(new br.com.colheitafeliz.domain.inventory.ItemLot("milho", br.com.colheitafeliz.domain.economy.Currency.GOLD, java.math.BigDecimal.valueOf(game.catalog().get("milho").seedPrice()), 3, false));
        assertEquals(0, new java.math.BigDecimal("109.3").compareTo(game.depositValue()));
        assertTrue(game.sellEverything().success());
        assertEquals(0, game.depositValue().signum());
        assertEquals(0, game.inventory().produce(game.catalog().get("milho")));
        assertTrue(game.wallet().coins().compareTo(new java.math.BigDecimal("609.3")) >= 0);
    }

    @Test void levelRewardsMatchTheConfiguredTable() {
        var rules = ProgressionLoader.load();
        assertEquals(0, rules.rewardFor(2).gold().signum());
        assertEquals(0, new java.math.BigDecimal("9000").compareTo(rules.rewardFor(5).gold()));
    }

    @Test void fertilizerAdvancesAQuarterOfTheCycleOncePerPlanting() {
        ManualClock clock = new ManualClock();
        GameState game = new GameState(clock::instant, clock, CropCatalogLoader.load(), bound -> 99);
        CropType corn = game.catalog().get("milho");
        assertFalse(game.buyFertilizer().success(), "adubo só no nível 2");
        game.progress().award(50);
        assertTrue(game.buyFertilizer().success());
        assertTrue(game.buyFertilizer().success());
        game.buySeed(corn);
        game.selectTool(Tool.HOE);
        game.act(0, 0);
        game.selectSeed(corn);
        game.act(0, 0);
        game.selectTool(Tool.FERTILIZER);
        assertFalse(game.act(0, 0).success());
        assertEquals("Regue antes de adubar.", game.message());
        game.selectTool(Tool.WATER);
        game.act(0, 0);
        game.selectTool(Tool.FERTILIZER);
        assertTrue(game.act(0, 0).success());
        assertEquals(1, game.inventory().fertilizer());
        assertEquals(corn.growthTime(1).toMillis() / 4, game.farm().tile(0, 0).crop().grownMillisAt(game.now()));
        assertFalse(game.act(0, 0).success(), "uma vez por plantio");
        assertEquals(1, game.inventory().fertilizer());
    }

    @Test void newGameStartsWithTheCursorThatDoesNotActOnTiles() {
        ManualClock clock = new ManualClock();
        GameState game = new GameState(clock::instant, clock, CropCatalogLoader.load(), bound -> 99);
        assertEquals(Tool.CURSOR, game.tool());
        assertFalse(game.act(0, 0).success());
        assertEquals(TileState.GRASS, game.farm().tile(0, 0).state());
    }

    @Test void alertsListFarmProblemsAndDisappearWhenSolved() {
        ManualClock clock = new ManualClock();
        GameState game = new GameState(clock::instant, clock, CropCatalogLoader.load(), bound -> 99);
        assertTrue(game.alerts().isEmpty());
        CropType turnip = game.catalog().get("nabo");
        game.buySeed(turnip);
        game.selectTool(Tool.HOE);
        game.act(0, 0);
        game.selectSeed(turnip);
        game.act(0, 0);
        assertEquals(java.util.List.of(new GameState.Alert("Terra seca", "Nabo")), game.alerts());
        game.selectTool(Tool.WATER);
        game.act(0, 0);
        assertTrue(game.alerts().isEmpty());
        game.farm().tile(0, 0).checkInsects(game.now(), true);
        assertEquals("Insetos", game.alerts().getFirst().title());
        game.selectTool(Tool.PESTICIDE);
        game.act(0, 0);
        assertTrue(game.alerts().isEmpty());
        assertEquals(0, java.math.BigDecimal.valueOf(5).compareTo(game.greenCoins()));
    }

    @Test void insectsPauseTheCropWithAnAlertUntilPesticide() {
        ManualClock clock = new ManualClock();
        GameState game = new GameState(clock::instant, clock, CropCatalogLoader.load(), bound -> 99);
        CropType corn = game.catalog().get("milho");
        game.buySeed(corn);
        game.selectTool(Tool.HOE);
        game.act(0, 0);
        game.selectSeed(corn);
        game.act(0, 0);
        game.selectTool(Tool.WATER);
        game.act(0, 0);
        game.farm().tile(0, 0).checkInsects(game.now(), true);
        clock.advanceSeconds(2700);
        game.refresh();
        assertEquals(TileState.GROWING, game.farm().tile(0, 0).state(), "insetos pausam o crescimento");
        assertTrue(game.alerts().contains(new GameState.Alert("Insetos", "Milho")));
        game.selectTool(Tool.PESTICIDE);
        assertTrue(game.act(0, 0).success());
        assertFalse(game.alerts().contains(new GameState.Alert("Insetos", "Milho")));
        game.selectTool(Tool.WATER);
        for (int i = 0; i < 3; i++) {
            assertTrue(game.act(0, 0).success());
            clock.advanceSeconds(900);
        }
        game.refresh();
        assertEquals(TileState.READY, game.farm().tile(0, 0).state());
        game.selectTool(Tool.HAND);
        assertTrue(game.act(0, 0).success());
        assertEquals(1, game.inventory().produce(corn));
    }

    @Test void plantingWithoutSeedIsRejectedAndLeavesSoilReady() {
        ManualClock clock = new ManualClock();
        GameState game = new GameState(clock::instant, CropCatalogLoader.load(), bound -> 99);
        game.selectTool(Tool.HOE);
        game.act(0, 0);
        game.selectSeed(game.catalog().get("nabo"));
        assertFalse(game.act(0, 0).success());
        assertEquals(TileState.PLOWED, game.farm().tile(0, 0).state());
        assertEquals(0, java.math.BigDecimal.valueOf(500).compareTo(game.wallet().coins()));
    }
}
