package br.com.colheitafeliz.domain;

import br.com.colheitafeliz.domain.crop.CropCatalog;
import br.com.colheitafeliz.domain.crop.CropType;
import br.com.colheitafeliz.domain.farm.FarmTile;
import br.com.colheitafeliz.domain.farm.PestState;
import br.com.colheitafeliz.domain.farm.TileState;
import br.com.colheitafeliz.domain.inventory.ItemLot;
import br.com.colheitafeliz.game.CropCatalogLoader;
import br.com.colheitafeliz.testsupport.TestCrops;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CropAndTileTest {
    private final CropCatalog catalog = CropCatalogLoader.load();
    private final CropType corn = catalog.get("milho");
    private final Instant start = Instant.parse("2026-01-01T12:00:00Z");

    @Test void plantingNeedsPlowedSoilAndWateringStartsGrowth() {
        FarmTile tile = new FarmTile();
        assertThrows(IllegalStateException.class, () -> tile.plant(corn, start));
        tile.plow();
        tile.plant(corn, start);
        assertEquals(TileState.GROWING, tile.state());
        assertEquals(1, tile.crop().stageAt(start.plusSeconds(500)));
        tile.refresh(start.plusSeconds(500));
        assertEquals(TileState.GROWING, tile.state());
        tile.water(start.plusSeconds(500));
        assertThrows(IllegalStateException.class, () -> tile.water(start.plusSeconds(501)));
        // Milho: 20 h; a terra fica úmida por 1/3 (6 h 40 min) e cada estágio leva 5 h.
        assertEquals(1, tile.crop().stageAt(start.plusSeconds(500)));
        assertEquals(2, tile.crop().stageAt(start.plusSeconds(18_500)));
        tile.water(start.plusSeconds(24_500));
        assertEquals(3, tile.crop().stageAt(start.plusSeconds(36_500)));
        tile.water(start.plusSeconds(48_500));
        assertEquals(4, tile.crop().stageAt(start.plusSeconds(54_500)));
        assertEquals(5, tile.crop().stageAt(start.plusSeconds(72_500)));
        tile.refresh(start.plusSeconds(72_500));
        assertEquals(TileState.READY, tile.state());
        assertEquals(corn.id(), tile.harvest(start.plusSeconds(72_500)).itemId());
        assertEquals(TileState.DEAD, tile.state());
        assertEquals(corn, tile.withered());
        assertNull(tile.crop());
        assertThrows(IllegalStateException.class, () -> tile.plant(corn, start.plusSeconds(72_600)));
        tile.clearDead();
        assertEquals(TileState.PLOWED, tile.state());
        assertNull(tile.withered());
    }

    @Test void soilDriesAfterAThirdOfTheCycleAndDrySoilGrowsAtHalfSpeed() {
        FarmTile tile = new FarmTile();
        tile.plow();
        tile.plant(corn, start);
        tile.water(start);
        assertEquals(java.time.Duration.ofSeconds(24_000), tile.crop().moistDuration());
        assertFalse(tile.crop().dryAt(start.plusSeconds(23_999)));
        assertTrue(tile.crop().dryAt(start.plusSeconds(24_000)));
        assertEquals(java.time.Duration.ofSeconds(120_000), tile.crop().remainingAt(start));
        assertEquals(48_000_000, tile.crop().grownMillisAt(start.plusSeconds(72_000)));
        assertFalse(tile.crop().matureAt(start.plusSeconds(119_999)));
        assertTrue(tile.crop().matureAt(start.plusSeconds(120_000)));
    }

    @Test void wateringDrySoilRestoresFullSpeedWithoutLosingGrowth() {
        FarmTile tile = new FarmTile();
        tile.plow();
        tile.plant(corn, start);
        tile.water(start);
        assertThrows(IllegalStateException.class, () -> tile.water(start.plusSeconds(20_000)));
        tile.water(start.plusSeconds(50_000));
        assertEquals(37_000_000, tile.crop().grownMillisAt(start.plusSeconds(50_000)));
        assertEquals(java.time.Duration.ofSeconds(24_000 + 11_000 * 2), tile.crop().remainingAt(start.plusSeconds(50_000)));
        assertEquals(61_000_000, tile.crop().grownMillisAt(start.plusSeconds(74_000)));
    }

    @Test void shortCropsStayMoistForAtLeastFiveMinutes() {
        FarmTile tile = new FarmTile();
        tile.plow();
        // Nenhuma cultura da regra é tão curta; a garantia vale para qualquer catálogo.
        tile.plant(new CropType("teste", "Teste", 600, 0, 1, 5, "nabo", 1, false,
                java.util.Map.of(br.com.colheitafeliz.domain.economy.Currency.GOLD, BigDecimal.ONE)), start);
        tile.water(start);
        assertEquals(java.time.Duration.ofSeconds(300), tile.crop().moistDuration());
    }

    @Test void strawberrySeasonsShortenAfterTheFirstAndHalveTheirXp() {
        CropType strawberry = TestCrops.strawberry();
        assertEquals(Duration.ofHours(31), strawberry.growthTime(1));
        assertEquals(Duration.ofHours(12), strawberry.growthTime(2));
        assertEquals(Duration.ofHours(12), strawberry.growthTime(3));
        assertEquals(0, new BigDecimal("46.5").compareTo(strawberry.harvestXp(1)));
        assertEquals(0, new BigDecimal("23.25").compareTo(strawberry.harvestXp(2)));
        assertEquals(0, new BigDecimal("11.625").compareTo(strawberry.harvestXp(3)));
        assertThrows(IllegalArgumentException.class, () -> strawberry.growthTime(4));
    }

    @Test void eachSeasonYieldsOneUnitOfTheOriginAndTheLastOneKillsThePlant() {
        CropType strawberry = TestCrops.strawberry();
        ItemLot origin = TestCrops.origin(strawberry);
        FarmTile tile = new FarmTile();
        tile.plow();
        tile.plant(strawberry, start, origin);
        Instant t = start;
        for (int season = 1; season <= 3; season++) {
            assertEquals(season, tile.crop().season());
            assertEquals(strawberry.growthTime(season), tile.crop().currentDuration());
            t = TestCrops.growUntilReady(tile, t);
            assertEquals(origin, tile.harvest(t));
        }
        assertEquals(TileState.DEAD, tile.state());
        assertEquals(strawberry, tile.withered());
        assertNull(tile.crop());
    }

    @Test void intermediateHarvestRestartsAtStageOneUnwateredAndWithShorterMoisture() {
        CropType strawberry = TestCrops.strawberry();
        FarmTile tile = new FarmTile();
        tile.plow();
        tile.plant(strawberry, start, TestCrops.origin(strawberry));
        tile.water(start);
        assertEquals(Duration.ofHours(10).plusMinutes(20), tile.crop().moistDuration());
        tile.fertilize(start, .25);
        tile.checkInsects(start.plusSeconds(60), true);
        tile.removeInsects(start.plusSeconds(60));
        Instant ready = TestCrops.growUntilReady(tile, start.plusSeconds(60));
        tile.harvest(ready);
        assertEquals(TileState.GROWING, tile.state());
        assertEquals(2, tile.crop().season());
        assertEquals(1, tile.crop().stageAt(ready));
        assertFalse(tile.crop().watered());
        assertFalse(tile.fertilized());
        assertEquals(PestState.NONE, tile.pest());
        assertEquals(0, tile.insects());
        assertNull(tile.nextPestCheckAt());
        assertNull(tile.nextInsectCheckAt());
        assertEquals(Duration.ofHours(4), tile.crop().moistDuration());
        assertEquals(0, tile.crop().grownMillisAt(ready.plusSeconds(3600)));
    }

    @Test void repeatedHarvestCannotSkipSeason() {
        CropType strawberry = TestCrops.strawberry();
        FarmTile tile = new FarmTile();
        tile.plow();
        tile.plant(strawberry, start, TestCrops.origin(strawberry));
        Instant ready = TestCrops.growUntilReady(tile, start);
        tile.harvest(ready);
        assertThrows(IllegalStateException.class, () -> tile.harvest(ready));
        assertEquals(2, tile.crop().season());
    }

    @Test void bothPestsShareOnePause() {
        CropType strawberry = TestCrops.strawberry();
        FarmTile tile = new FarmTile();
        tile.plow();
        tile.plant(strawberry, start, TestCrops.origin(strawberry));
        tile.water(start);
        Instant weeds = start.plusSeconds(3600);
        tile.checkPest(weeds, true);
        long grown = tile.crop().grownMillisAt(weeds);
        assertEquals(3_600_000, grown);
        tile.checkInsects(weeds.plusSeconds(600), true);
        assertEquals(grown, tile.crop().grownMillisAt(weeds.plusSeconds(7200)));
        tile.removePest(weeds.plusSeconds(1800));
        assertEquals(grown, tile.crop().grownMillisAt(weeds.plusSeconds(7200)));
        tile.removeInsects(weeds.plusSeconds(7200));
        assertEquals(grown + 600_000, tile.crop().grownMillisAt(weeds.plusSeconds(7800)));
    }

    @Test void insectsAlonePauseGrowthAndKeepTheProgressExactly() {
        CropType strawberry = TestCrops.strawberry();
        FarmTile tile = new FarmTile();
        tile.plow();
        tile.plant(strawberry, start, TestCrops.origin(strawberry));
        tile.water(start);
        Instant insects = start.plusSeconds(3600);
        tile.checkInsects(insects, true);
        tile.worsenInsects(insects.plusSeconds(100));
        assertEquals(3_600_000, tile.crop().grownMillisAt(insects.plusSeconds(5000)));
        tile.removeInsects(insects.plusSeconds(5000));
        assertEquals(4_600_000, tile.crop().grownMillisAt(insects.plusSeconds(6000)));
    }

    @Test void insectsNeverReduceYield() {
        CropType strawberry = TestCrops.strawberry();
        FarmTile tile = new FarmTile();
        tile.plow();
        tile.plant(strawberry, start, TestCrops.origin(strawberry));
        tile.water(start);
        tile.checkInsects(start.plusSeconds(60), true);
        tile.worsenInsects(start.plusSeconds(120));
        tile.worsenInsects(start.plusSeconds(180));
        assertEquals(FarmTile.MAX_INSECTS, tile.insects());
        tile.removeInsects(start.plusSeconds(240));
        Instant ready = TestCrops.growUntilReady(tile, start.plusSeconds(240));
        assertEquals(1, tile.harvest(ready).quantity());
    }

    @Test void cannotHarvestBeforeMaturity() {
        FarmTile tile = new FarmTile();
        tile.plow();
        tile.plant(corn, start);
        tile.water(start);
        assertThrows(IllegalStateException.class, () -> tile.harvest(start.plusSeconds(79)));
    }
}
