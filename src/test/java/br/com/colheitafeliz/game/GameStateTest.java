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
        assertEquals(100, game.wallet().coins());
        assertTrue(game.buySeed(corn).success());
        assertEquals(75, game.wallet().coins());
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
        assertEquals(3, game.inventory().produce(corn));
        // Watering three times may complete today's watering mission, so compare against the balance here.
        int coins = game.wallet().coins();
        assertFalse(game.act(0, 0).success());
        assertTrue(game.sellProduce(corn).success());
        assertEquals(coins + 25, game.wallet().coins());
        assertTrue(game.sellAllProduce(corn).success());
        assertEquals(coins + 75, game.wallet().coins());
        assertEquals(0, game.inventory().produce(corn));
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
        int xp = game.progress().currentXp();
        assertTrue(game.act(0, 0).success());
        assertEquals(xp + 2, game.progress().currentXp());
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
        assertEquals(130, lucky.wallet().coins());
        assertTrue(lucky.message().contains("baú do tesouro"));
        lucky.progress().award(50);
        assertEquals(40, lucky.treasureCoins());

        GameState unlucky = new GameState(clock::instant, clock, CropCatalogLoader.load(), bound -> GameState.TREASURE_CHANCE);
        unlucky.selectTool(Tool.HOE);
        assertTrue(unlucky.act(0, 0).success());
        assertEquals(0, unlucky.lastTreasureCoins());
        assertEquals(100, unlucky.wallet().coins());
    }

    @Test void sellEverythingEmptiesTheDepositAtOnce() {
        ManualClock clock = new ManualClock();
        GameState game = new GameState(clock::instant, clock, CropCatalogLoader.load(), bound -> 99);
        assertFalse(game.sellEverything().success());
        game.inventory().addProduce(game.catalog().get("nabo"), 2);
        game.inventory().addProduce(game.catalog().get("milho"), 3);
        assertEquals(2 * 20 + 3 * 25, game.depositValue());
        assertTrue(game.sellEverything().success());
        assertEquals(0, game.depositValue());
        assertEquals(0, game.inventory().produce(game.catalog().get("milho")));
        assertTrue(game.wallet().coins() >= 100 + 115);
    }

    @Test void levelUpGivesCoinsForEveryLevelReached() {
        ManualClock clock = new ManualClock();
        GameState game = new GameState(clock::instant, clock, CropCatalogLoader.load(), bound -> 99);
        assertEquals(50, GameState.levelUpCoins(2));
        assertEquals(125, GameState.levelUpCoins(5));
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
        assertEquals(corn.growthTime().toMillis() / 4, game.farm().tile(0, 0).crop().grownMillisAt(game.now()));
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
        assertEquals(0, game.greenCoins());
    }

    @Test void plantingWithoutSeedIsRejectedAndLeavesSoilReady() {
        ManualClock clock = new ManualClock();
        GameState game = new GameState(clock::instant, CropCatalogLoader.load(), bound -> 99);
        game.selectTool(Tool.HOE);
        game.act(0, 0);
        game.selectSeed(game.catalog().get("nabo"));
        assertFalse(game.act(0, 0).success());
        assertEquals(TileState.PLOWED, game.farm().tile(0, 0).state());
        assertEquals(100, game.wallet().coins());
    }
}
