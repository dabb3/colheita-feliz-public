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

    @Test void catalogHasTenDistinctCulturesWithSprites() {
        assertEquals(10, catalog.all().size());
        assertEquals(5, corn.stages());
        assertEquals("milho", corn.spriteStem());
        assertEquals(25, corn.seedPrice());
    }

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
        assertEquals(1, tile.crop().stageAt(start.plusSeconds(500)));
        assertEquals(2, tile.crop().stageAt(start.plusSeconds(1175)));
        tile.water(start.plusSeconds(1400));
        assertEquals(3, tile.crop().stageAt(start.plusSeconds(1850)));
        tile.water(start.plusSeconds(2300));
        assertEquals(4, tile.crop().stageAt(start.plusSeconds(2525)));
        assertEquals(5, tile.crop().stageAt(start.plusSeconds(3200)));
        tile.refresh(start.plusSeconds(3200));
        assertEquals(TileState.READY, tile.state());
        assertEquals(corn.id(), tile.harvest(start.plusSeconds(3200)).itemId());
        assertEquals(TileState.DEAD, tile.state());
        assertEquals(corn, tile.withered());
        assertNull(tile.crop());
        assertThrows(IllegalStateException.class, () -> tile.plant(corn, start.plusSeconds(3300)));
        tile.clearDead();
        assertEquals(TileState.PLOWED, tile.state());
        assertNull(tile.withered());
    }

    @Test void soilDriesAfterAThirdOfTheCycleAndDrySoilGrowsAtHalfSpeed() {
        FarmTile tile = new FarmTile();
        tile.plow();
        tile.plant(corn, start);
        tile.water(start);
        assertEquals(java.time.Duration.ofSeconds(900), tile.crop().moistDuration());
        assertFalse(tile.crop().dryAt(start.plusSeconds(899)));
        assertTrue(tile.crop().dryAt(start.plusSeconds(900)));
        assertEquals(java.time.Duration.ofSeconds(4500), tile.crop().remainingAt(start));
        assertEquals(1_800_000, tile.crop().grownMillisAt(start.plusSeconds(2700)));
        assertFalse(tile.crop().matureAt(start.plusSeconds(4499)));
        assertTrue(tile.crop().matureAt(start.plusSeconds(4500)));
    }

    @Test void wateringDrySoilRestoresFullSpeedWithoutLosingGrowth() {
        FarmTile tile = new FarmTile();
        tile.plow();
        tile.plant(corn, start);
        tile.water(start);
        assertThrows(IllegalStateException.class, () -> tile.water(start.plusSeconds(600)));
        tile.water(start.plusSeconds(1500));
        assertEquals(1_200_000, tile.crop().grownMillisAt(start.plusSeconds(1500)));
        assertEquals(java.time.Duration.ofSeconds(900 + 600 * 2), tile.crop().remainingAt(start.plusSeconds(1500)));
        assertEquals(2_100_000, tile.crop().grownMillisAt(start.plusSeconds(2400)));
    }

    @Test void shortCropsStayMoistForAtLeastFiveMinutes() {
        FarmTile tile = new FarmTile();
        tile.plow();
        tile.plant(catalog.get("nabo"), start);
        tile.water(start);
        assertEquals(java.time.Duration.ofSeconds(300), tile.crop().moistDuration());
    }

    @Test void catalogFollowsTheBalancedEconomy() {
        for (CropType type : catalog.all()) {
            long minutes = type.growthSeconds() / 60;
            double profitPerHour = (type.harvestYield() * type.salePrice() - type.seedPrice()) * 60.0 / minutes;
            double floor = switch (type.requiredLevel()) { case 1 -> 60; case 2 -> 80; case 3 -> 90; default -> 100; };
            assertTrue(profitPerHour >= floor, type.id() + " rende " + profitPerHour + "/h");
        }
        assertEquals(3, corn.harvestYield());
        assertEquals(150, catalog.get("melancia").harvestXp());
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
