package br.com.colheitafeliz.game;

import br.com.colheitafeliz.domain.crop.CropType;
import br.com.colheitafeliz.domain.farm.TileState;
import br.com.colheitafeliz.testsupport.ManualClock;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TutorialTest {
    @Test void newFarmStartsWithReadyTurnipsAndTheFirstStep() {
        ManualClock clock = new ManualClock();
        GameState game = new GameState(clock::instant, clock, CropCatalogLoader.load(), bound -> 99);
        assertFalse(game.tutorial().active());
        game.startNewFarm();
        assertTrue(game.tutorial().active());
        assertEquals("harvest", game.tutorial().current().action());
        game.refresh();
        assertEquals(TileState.READY, game.farm().tile(0, 0).state());
        assertEquals(TileState.READY, game.farm().tile(0, 1).state());
        assertEquals(TileState.GRASS, game.farm().tile(0, 2).state());
    }

    @Test void stepsAdvanceInOrderPayRewardsAndHoldBackDailyMissions() {
        ManualClock clock = new ManualClock();
        GameState game = new GameState(clock::instant, clock, CropCatalogLoader.load(), bound -> 99);
        game.startNewFarm();
        CropType turnip = game.catalog().get("nabo");

        assertTrue(game.buySeed(turnip).success());
        assertEquals(0, game.tutorial().stepIndex(), "comprar fora de ordem não avança");

        game.selectTool(Tool.HAND);
        assertTrue(game.act(0, 0).success());
        assertEquals(1, game.tutorial().stepIndex());
        assertTrue(game.message().contains("Tarefa concluída: +10 moedas"));

        assertTrue(game.sellEverything().success());
        assertEquals(2, game.tutorial().stepIndex());
        assertTrue(game.buySeed(turnip).success());
        assertEquals(3, game.tutorial().stepIndex());
        game.selectTool(Tool.HOE);
        assertTrue(game.act(0, 0).success(), "a pá na planta morta conta como arar");
        assertEquals(4, game.tutorial().stepIndex());
        game.selectSeed(turnip);
        assertTrue(game.act(0, 0).success());
        assertEquals(5, game.tutorial().stepIndex());
        for (DailyMissionBoard.Mission mission : game.dailyMissions().missions())
            assertEquals(0, game.dailyMissions().progress(mission), "missões não contam durante o tutorial");

        game.selectTool(Tool.WATER);
        assertTrue(game.act(0, 0).success());
        assertFalse(game.tutorial().active());
        assertTrue(game.message().contains("Primeiros passos concluídos"));
        int rewards = 0;
        for (Tutorial.Step step : Tutorial.STEPS) rewards += step.coins();
        // Cada colheita rende uma unidade, vendida pelo custo da semente com o acréscimo comum.
        java.math.BigDecimal harvested = turnip.seedPrice().getValue()
                .multiply(new java.math.BigDecimal("1.15")).setScale(1, java.math.RoundingMode.CEILING);
        java.math.BigDecimal levelGold = java.math.BigDecimal.ZERO;
        for (int level = 2; level <= game.progress().level(); level++)
            levelGold = levelGold.add(ProgressionLoader.load().rewardFor(level).gold());
        assertEquals(0, turnip.seedPrice().getValue().multiply(java.math.BigDecimal.valueOf(-2))
                .add(java.math.BigDecimal.valueOf(500 + rewards)).add(harvested).add(levelGold)
                .compareTo(game.wallet().coins()));
    }
}
