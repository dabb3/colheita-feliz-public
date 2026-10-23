package br.com.colheitafeliz.domain;

import br.com.colheitafeliz.domain.crop.CropCatalog;
import br.com.colheitafeliz.domain.crop.CropType;
import br.com.colheitafeliz.domain.farm.FarmTile;
import br.com.colheitafeliz.domain.farm.TileState;
import br.com.colheitafeliz.game.CropCatalogLoader;
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
        assertEquals(corn, tile.harvest(start.plusSeconds(3200)));
        assertEquals(TileState.DEAD, tile.state());
        assertEquals(corn, tile.withered());
        assertNull(tile.crop());
        assertThrows(IllegalStateException.class, () -> tile.plant(corn, start.plusSeconds(3300)));
        tile.clearDead();
        assertEquals(TileState.FALLOW, tile.state());
        assertNull(tile.withered());
        assertThrows(IllegalStateException.class, () -> tile.plant(corn, start.plusSeconds(3400)));
        tile.plow();
        assertEquals(TileState.PLOWED, tile.state());
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

    @Test void cannotHarvestBeforeMaturity() {
        FarmTile tile = new FarmTile();
        tile.plow();
        tile.plant(corn, start);
        tile.water(start);
        assertThrows(IllegalStateException.class, () -> tile.harvest(start.plusSeconds(79)));
    }
}
