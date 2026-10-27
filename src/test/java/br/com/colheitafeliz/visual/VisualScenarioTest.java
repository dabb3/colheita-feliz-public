package br.com.colheitafeliz.visual;

import br.com.colheitafeliz.game.CropCatalogLoader;
import br.com.colheitafeliz.game.GameState;
import br.com.colheitafeliz.game.Tool;
import br.com.colheitafeliz.testsupport.ManualClock;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VisualScenarioTest {
    @Test void initialColumnSupportsAllFiveReviewStages() {
        var catalog = CropCatalogLoader.load();
        var clock = new ManualClock();
        var state = new GameState(clock::instant, clock, catalog, bound -> 99);
        var turnip = catalog.get("nabo");
        state.wallet().credit(1500);
        long interval = turnip.growthTime(1).toSeconds() / (turnip.stages() - 1);
        for (int row = 0; row < 5; row++) {
            // Keep earlier rows moist so each row stays exactly one stage apart: the soil dries every third of
            // the cycle and a stage takes a quarter, so ticks of a twelfth hit both boundaries.
            for (int tick = 0; row > 0 && tick < 3; tick++) {
                clock.advanceSeconds(interval / 3);
                state.selectTool(Tool.WATER);
                for (int earlier = 0; earlier < row; earlier++) {
                    var crop = state.farm().tile(0, earlier).crop();
                    if (crop.dryAt(state.now()) && !crop.matureAt(state.now()))
                        assertTrue(state.act(0, earlier).success());
                }
            }
            assertTrue(state.buySeed(turnip).success());
            state.selectTool(Tool.HOE);
            assertTrue(state.act(0, row).success());
            assertTrue(state.selectSeed(turnip).success());
            assertTrue(state.act(0, row).success());
            state.selectTool(Tool.WATER);
            assertTrue(state.act(0, row).success());
        }
        state.refresh();
        for (int row = 0; row < 5; row++) {
            assertEquals(5 - row, state.farm().tile(0, row).crop().stageAt(state.now()));
        }
        assertFalse(state.land().isUnlocked(1, 0));
    }
}
