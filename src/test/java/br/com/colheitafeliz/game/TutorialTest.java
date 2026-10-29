package br.com.colheitafeliz.game;
import br.com.colheitafeliz.domain.farm.TileState;
import br.com.colheitafeliz.testsupport.ManualClock;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TutorialTest {
    @Test void plowingAllStartingPlotsBeforeBuyingDoesNotStrandTheTutorial() {
        var clock = new ManualClock();
        var game = new GameState(clock::instant, clock, CropCatalogLoader.load(), bound -> 99);
        game.startNewFarm();
        game.selectTool(Tool.HOE);
        for (int row = 0; row < GameState.ROWS; row++) assertTrue(game.act(0, row).success());
        assertEquals(0, game.tutorial().stepIndex());
        assertTrue(game.buySeed(game.catalog().get("nabo")).success());
        assertEquals("plant", game.tutorial().current().action());
        exact("200", game.wallet().coins());
        exact("6", game.greenCoins());
        exact("0", game.progress().currentXp());
        game.selectSeed(game.catalog().get("nabo"));
        assertTrue(game.act(0, 0).success());
        assertEquals("water", game.tutorial().current().action());
    }

    @Test void emptyFarmTeachesRealActionsWithoutExtraRewards() {
        var clock = new ManualClock();
        var game = new GameState(clock::instant, clock, CropCatalogLoader.load(), bound -> 99);
        game.startNewFarm();
        assertEquals("buy", game.tutorial().current().action());
        assertEquals(5, game.land().snapshot().size());
        for (int row = 0; row < GameState.ROWS; row++)
            for (int col = 0; col < GameState.COLUMNS; col++)
                assertEquals(TileState.GRASS, game.farm().tile(col, row).state());
        assertTrue(game.inventory().seedLots().isEmpty());
        assertTrue(game.inventory().produceLots().isEmpty());
        assertEquals(0, game.barn().size());
        assertTrue(game.inventory().feedSnapshot().isEmpty());
        assertTrue(game.inventory().fertilizerSnapshot().isEmpty());
        var turnip = game.catalog().get("nabo");
        assertTrue(game.buySeed(turnip).success());
        exact("200", game.wallet().coins());
        exact("6", game.greenCoins());
        game.selectTool(Tool.HOE);
        assertTrue(game.act(0, 0).success());
        exact("200", game.wallet().coins());
        exact("6", game.greenCoins());
        game.selectSeed(turnip);
        assertTrue(game.act(0, 0).success());
        exact("5", game.progress().currentXp());
        exact("7", game.greenCoins());
        game.selectTool(Tool.WATER);
        assertTrue(game.act(0, 0).success());
        exact("8", game.progress().currentXp());
        assertEquals("harvest", game.tutorial().current().action());
        assertTrue(game.tutorial().current().hint().contains("Aguarde"));
        game.selectTool(Tool.HAND);
        assertFalse(game.act(0, 0).success());
        for (int i = 0; i < 3; i++) {
            clock.advanceSeconds(12_000);
            game.refresh();
            if (game.farm().tile(0, 0).state() == TileState.GROWING) {
                game.selectTool(Tool.WATER);
                assertTrue(game.act(0, 0).success());
            }
        }
        game.selectTool(Tool.HAND);
        assertTrue(game.act(0, 0).success());
        assertTrue(game.sellEverything().success());
        assertFalse(game.tutorial().active());
        exact("545", game.wallet().coins());
        exact("7", game.greenCoins());
        assertEquals(2, game.progress().level());
        exact("4", game.progress().currentXp());
        var restored = GameState.restore(clock::instant, clock, game.catalog(), game.snapshot(), bound -> 99);
        assertFalse(restored.tutorial().active());
        exact("545", restored.wallet().coins());
        exact("7", restored.greenCoins());
        exact("4", restored.progress().currentXp());
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"feed", "vip", "animal", "fertilizer", "plot"})
    void otherPurchasesPayTradeWithoutPretendingToBuyASeed(String purchase) {
        var clock = new ManualClock();
        var game = new GameState(clock::instant, clock, CropCatalogLoader.load(), bound -> 99);
        game.startNewFarm();
        game.progress().award(10000);
        game.wallet().credit(100000);
        game.wallet().credit(br.com.colheitafeliz.domain.economy.Currency.GREEN, new BigDecimal("1000"));
        if (purchase.equals("feed")) assertTrue(game.buyAnimal(game.animalType("galinha")).success());
        ActionResult result = switch (purchase) {
            case "feed" -> game.buyFeed("producer");
            case "vip" -> game.buyVip();
            case "animal" -> game.buyAnimal(game.animalType("galinha"));
            case "fertilizer" -> game.buyFertilizer("comum", br.com.colheitafeliz.domain.economy.Currency.GOLD);
            case "plot" -> game.buyPlot(1, 0);
            default -> throw new IllegalArgumentException(purchase);
        };
        assertTrue(result.success());
        assertEquals(java.util.List.of("trade"), game.dailyMissions().completedSnapshot());
        assertEquals(0, game.tutorial().stepIndex());
        assertTrue(game.buySeed(game.catalog().get("nabo")).success());
        assertEquals(1, game.tutorial().stepIndex());
    }

    private static void exact(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
