package br.com.colheitafeliz.domain;

import br.com.colheitafeliz.domain.crop.CropType;
import br.com.colheitafeliz.domain.farm.FarmTile;
import br.com.colheitafeliz.domain.farm.PestState;
import br.com.colheitafeliz.domain.farm.TileState;
import br.com.colheitafeliz.domain.player.PlayerProgress;
import br.com.colheitafeliz.game.CropCatalogLoader;
import br.com.colheitafeliz.game.GameState;
import br.com.colheitafeliz.game.ProgressionLoader;
import br.com.colheitafeliz.game.Tool;
import br.com.colheitafeliz.testsupport.ManualClock;
import br.com.colheitafeliz.testsupport.TestCrops;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProgressAndPestTest {
    @Test void xpLevelsUpWithCarryAndUnlocksCrops() {
        PlayerProgress progress = new PlayerProgress(ProgressionLoader.load());
        assertEquals(0, java.math.BigDecimal.valueOf(50).compareTo(progress.xpToNext()));
        assertEquals(0, progress.award(49));
        assertEquals(0, java.math.BigDecimal.valueOf(49).compareTo(progress.currentXp()));
        assertEquals(1, progress.award(1));
        assertEquals(2, progress.level());
        assertEquals(0, java.math.BigDecimal.valueOf(0).compareTo(progress.currentXp()));
        assertEquals(0, java.math.BigDecimal.valueOf(75).compareTo(progress.xpToNext()));
        assertEquals(1, progress.award(80));
        assertEquals(3, progress.level());
        assertEquals(0, java.math.BigDecimal.valueOf(5).compareTo(progress.currentXp()));
    }

    @Test void lockedCropCannotBeBoughtUntilLevelTwo() {
        ManualClock clock = new ManualClock();
        CropType turnip = TestCrops.turnip();
        var catalog = new br.com.colheitafeliz.domain.crop.CropCatalog(CropCatalogLoader.load().all().stream()
                .map(type -> type.id().equals(turnip.id()) ? turnip : type).toList());
        GameState game = new GameState(clock::instant, catalog, bound -> 99);
        CropType potato = game.catalog().get("batata");
        assertFalse(game.unlocked(potato));
        assertFalse(game.buySeed(potato).success());
        assertFalse(game.selectSeed(potato).success());
        game.wallet().credit(1000);
        for (int i = 0; i < 3; i++) assertTrue(game.buySeed(turnip).success());
        for (int row = 0; row < 3; row++) {
            game.selectTool(Tool.HOE);
            game.act(0, row);
            game.selectSeed(turnip);
            game.act(0, row);
        }
        // 10 h de crescimento: regar a cada terço mantém a terra úmida até a maturação.
        for (int third = 0; third < 3; third++) {
            game.selectTool(Tool.WATER);
            for (int row = 0; row < 3; row++) assertTrue(game.act(0, row).success());
            clock.advanceSeconds(12_000);
        }
        game.refresh();
        game.selectTool(Tool.HAND);
        for (int row = 0; row < 3; row++) assertTrue(game.act(0, row).success());
        assertEquals(2, game.progress().level());
        assertTrue(game.unlocked(potato));
        assertEquals(0, game.inventory().seeds(potato));
        assertEquals(0, game.inventory().seeds(game.catalog().get("arroz")));
        assertEquals(0, game.inventory().seeds(game.catalog().get("girassol")));
        assertTrue(game.buySeed(potato).success());
    }

    @Test void wateringMoistSoilIsRefusedAndDrySoilCanBeWateredAgain() {
        ManualClock clock = new ManualClock();
        GameState game = new GameState(clock::instant, CropCatalogLoader.load(), bound -> 99);
        CropType turnip = game.catalog().get("nabo");
        game.buySeed(turnip);
        game.selectTool(Tool.HOE);
        game.act(0, 0);
        game.selectSeed(turnip);
        game.act(0, 0);
        game.selectTool(Tool.WATER);
        assertTrue(game.act(0, 0).success());
        clock.advanceSeconds(120);
        assertFalse(game.act(0, 0).success());
        assertEquals("A terra ainda está úmida.", game.message());
        clock.advanceSeconds(180);
        java.math.BigDecimal xp = game.progress().currentXp();
        assertTrue(game.act(0, 0).success());
        assertEquals(xp.add(java.math.BigDecimal.valueOf(3)), game.progress().currentXp());
        clock.advanceSeconds(300);
        game.refresh();
        assertEquals(TileState.READY, game.farm().tile(0, 0).state());
    }

    @Test void harvestDepositsOneUnitAndTheFormulaXp() {
        ManualClock clock = new ManualClock();
        GameState game = new GameState(clock::instant, CropCatalogLoader.load(), bound -> 99);
        CropType carrot = game.catalog().get("cenoura");
        game.buySeed(carrot);
        game.selectTool(Tool.HOE);
        game.act(0, 0);
        game.selectSeed(carrot);
        game.act(0, 0);
        game.selectTool(Tool.WATER);
        game.act(0, 0);
        clock.advanceSeconds(2000);
        game.refresh();
        java.math.BigDecimal xp = game.progress().currentXp();
        game.selectTool(Tool.HAND);
        assertTrue(game.act(0, 0).success());
        assertEquals(1, game.inventory().produce(carrot));
        assertEquals(0, xp.add(carrot.harvestXp(1)).compareTo(game.progress().currentXp()));
    }

    @Test void weedsPauseGrowthUntilRemovalWithInjectedRandom() {
        ManualClock clock = new ManualClock();
        GameState game = new GameState(clock::instant, CropCatalogLoader.load(), bound -> 0);
        CropType turnip = game.catalog().get("nabo");
        game.buySeed(turnip);
        game.selectTool(Tool.HOE);
        game.act(0, 0);
        game.selectSeed(turnip);
        game.act(0, 0);
        game.selectTool(Tool.WATER);
        game.act(0, 0);
        clock.advanceSeconds(300);
        assertTrue(game.refresh());
        assertEquals(PestState.WEEDS, game.farm().tile(0, 0).pest());
        assertEquals(1, game.farm().tile(0, 0).insects());
        int pausedStage = game.farm().tile(0, 0).crop().stageAt(game.now());
        clock.advanceSeconds(100);
        game.refresh();
        assertEquals(pausedStage, game.farm().tile(0, 0).crop().stageAt(game.now()));
        assertEquals(TileState.GROWING, game.farm().tile(0, 0).state());
        game.selectTool(Tool.WEEDER);
        assertTrue(game.act(0, 0).success());
        assertEquals(PestState.NONE, game.farm().tile(0, 0).pest());
        assertTrue(game.farm().tile(0, 0).crop().paused(), "os insetos ainda pausam");
        game.selectTool(Tool.PESTICIDE);
        assertTrue(game.act(0, 0).success());
        assertFalse(game.farm().tile(0, 0).crop().paused());
        game.selectTool(Tool.WATER);
        assertTrue(game.act(0, 0).success());
        clock.advanceSeconds(300);
        game.refresh();
        assertEquals(TileState.READY, game.farm().tile(0, 0).state());
    }

    @Test void insectsMultiplyAndPauseGrowthUntilPesticide() {
        ManualClock clock = new ManualClock();
        GameState game = new GameState(clock::instant, CropCatalogLoader.load(), bound -> 0);
        CropType strawberry = game.catalog().get("morango");
        game.buySeed(strawberry);
        game.selectTool(Tool.HOE);
        game.act(0, 0);
        game.selectSeed(strawberry);
        game.act(0, 0);
        game.selectTool(Tool.WATER);
        game.act(0, 0);
        FarmTile tile = game.farm().tile(0, 0);
        game.selectTool(Tool.PESTICIDE);
        assertFalse(game.act(0, 0).success());
        clock.advanceSeconds(600);
        assertTrue(game.refresh());
        assertTrue(game.lastOutbreak().insects());
        assertFalse(game.lastOutbreak().weeds());
        assertEquals(1, tile.insects());
        assertTrue(tile.crop().paused());
        long grown = tile.crop().grownMillisAt(clock.instant());
        clock.advanceSeconds(240);
        game.refresh();
        assertEquals(2, tile.insects());
        assertEquals(grown, tile.crop().grownMillisAt(clock.instant()));
        assertEquals(PestState.NONE, tile.pest());
        java.math.BigDecimal xp = game.progress().currentXp();
        assertTrue(game.act(0, 0).success());
        assertEquals(0, tile.insects());
        assertFalse(tile.crop().paused());
        assertEquals(xp.add(java.math.BigDecimal.valueOf(5)), game.progress().currentXp());
    }

    @Test void harvestAfterInsectsDepositsOneUnitWithoutLoss() {
        ManualClock clock = new ManualClock();
        GameState game = new GameState(clock::instant, CropCatalogLoader.load(), bound -> 99);
        CropType strawberry = game.catalog().get("morango");
        game.buySeed(strawberry);
        game.selectTool(Tool.HOE);
        game.act(0, 0);
        game.selectSeed(strawberry);
        game.act(0, 0);
        game.selectTool(Tool.WATER);
        game.act(0, 0);
        FarmTile tile = game.farm().tile(0, 0);
        tile.checkInsects(clock.instant(), true);
        tile.worsenInsects(clock.instant());
        game.selectTool(Tool.PESTICIDE);
        assertTrue(game.act(0, 0).success());
        game.selectTool(Tool.WATER);
        for (int i = 0; i < 2; i++) {
            clock.advanceSeconds(800);
            assertTrue(game.act(0, 0).success());
        }
        clock.advanceSeconds(800);
        game.refresh();
        assertEquals(TileState.READY, tile.state());
        game.selectTool(Tool.HAND);
        java.math.BigDecimal xp = game.progress().currentXp();
        assertTrue(game.act(0, 0).success());
        assertEquals(1, game.inventory().produce(strawberry));
        assertEquals(1, game.lastHarvestAmount());
        assertFalse(game.message().contains("comeram"));
        assertEquals(0, xp.add(strawberry.harvestXp(1)).compareTo(game.progress().currentXp()));
    }

    @Test void herbicideOnlyRemovesWeedsAndPesticideOnlyInsects() {
        ManualClock clock = new ManualClock();
        GameState game = new GameState(clock::instant, CropCatalogLoader.load(), bound -> 99);
        CropType turnip = game.catalog().get("nabo");
        game.buySeed(turnip);
        game.selectTool(Tool.HOE);
        game.act(0, 0);
        game.selectSeed(turnip);
        game.act(0, 0);
        game.selectTool(Tool.WATER);
        game.act(0, 0);
        FarmTile tile = game.farm().tile(0, 0);
        tile.checkInsects(clock.instant(), true);
        game.selectTool(Tool.WEEDER);
        assertFalse(game.act(0, 0).success());
        assertEquals(1, tile.insects());
        game.selectTool(Tool.PESTICIDE);
        assertTrue(game.act(0, 0).success());
        assertEquals(0, tile.insects());
    }
}
