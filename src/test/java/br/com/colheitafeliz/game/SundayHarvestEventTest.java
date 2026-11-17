package br.com.colheitafeliz.game;

import br.com.colheitafeliz.domain.crop.CropCatalog;
import br.com.colheitafeliz.testsupport.ManualClock;
import br.com.colheitafeliz.testsupport.TestCrops;
import br.com.colheitafeliz.domain.farm.TileState;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SundayHarvestEventTest {
    private final CropCatalog catalog = CropCatalogLoader.load();

    @Test void boundariesUseSaoPauloAndStartOnNovemberTwentySecond() {
        var turnip = catalog.get("nabo");
        for (String at : new String[]{"2026-11-15T15:00:00Z", "2026-11-22T02:59:59Z", "2026-11-23T03:00:00Z"})
            assertEquals(1, SundayHarvestEvent.multiplier(turnip, Instant.parse(at)), at);
        for (String at : new String[]{"2026-11-22T03:00:00Z", "2026-11-23T02:59:59Z", "2027-01-03T03:00:00Z"})
            assertEquals(2, SundayHarvestEvent.multiplier(turnip, Instant.parse(at)), at);
    }

    @Test void everyCommonShopCropIsEligibleButSpecialAndVipCropsAreNot() {
        Instant sunday = Instant.parse("2026-11-22T15:00:00Z");
        for (var crop : catalog.all()) {
            boolean eligible = !crop.vipOnly() && crop.acquisitionSource() == br.com.colheitafeliz.domain.inventory.AcquisitionSource.SHOP;
            assertEquals(eligible ? 2 : 1, SundayHarvestEvent.multiplier(crop, sunday), crop.id());
        }
    }

    @Test void projectionIsDeterministicAcrossRestartAndAnnouncesNextSunday() {
        var future = SundayHarvestEvent.at(Instant.parse("2026-11-21T15:00:00Z"), catalog);
        assertEquals("UPCOMING", future.state());
        assertEquals("2026-11-22T03:00:00Z", future.startsAt());
        var active = SundayHarvestEvent.at(Instant.parse("2026-11-22T03:00:00Z"), catalog);
        assertEquals("ACTIVE", active.state());
        assertEquals("2026-11-23T03:00:00Z", active.endsAt());
        assertEquals("2026-11-29T03:00:00Z", active.nextStartsAt());
        assertEquals(active, SundayHarvestEvent.at(Instant.parse("2026-11-22T03:00:00Z"), CropCatalogLoader.load()));
        assertEquals("2026-11-29T03:00:00Z", SundayHarvestEvent.at(Instant.parse("2026-11-23T03:00:00Z"), catalog).startsAt());
    }

    @Test void readyOnSaturdayHarvestedOnSundayGetsDoubleXp() {
        assertHarvest("nabo", "2026-11-21T21:00:00Z", "2026-11-22T03:00:00Z", "30");
        assertHarvest("cenoura", "2026-11-21T21:00:00Z", "2026-11-22T03:00:00Z", "45");
    }

    @Test void readyOnSundayHarvestedOnMondayGetsNormalXp() {
        assertHarvest("nabo", "2026-11-22T21:00:00Z", "2026-11-23T03:00:00Z", "15");
        assertHarvest("cenoura", "2026-11-22T21:00:00Z", "2026-11-23T03:00:00Z", "22.5");
    }

    @Test void fractionalSeasonXpIsDoubledWithoutChangingBaseFormula() {
        var crop = catalog.get("morango");
        assertEquals(0, crop.harvestXp(3).compareTo(new BigDecimal("11.625")));
        assertEquals(0, crop.harvestXp(3).multiply(BigDecimal.valueOf(SundayHarvestEvent.multiplier(crop,
                Instant.parse("2026-11-22T15:00:00Z")))).compareTo(new BigDecimal("23.250")));
    }

    @Test void vipOwnerGetsOnlyDoubleFractionalXpOnTheThirdSeason() {
        var clock = new ManualClock();
        clock.advanceSeconds(Duration.between(clock.instant(), Instant.parse("2026-11-19T20:00:00Z")).toSeconds());
        var game = new GameState(clock::instant, clock, catalog, bound -> 99);
        while (game.progress().level() < 10) game.progress().award(game.progress().xpToNext().subtract(game.progress().currentXp()));
        game.wallet().credit(br.com.colheitafeliz.domain.economy.Currency.GREEN, new BigDecimal("100"));
        assertTrue(game.buyVip().success());
        var crop = catalog.get("morango");var tile = game.farm().tile(0, 0);
        tile.plow();tile.plant(crop, clock.instant(), TestCrops.origin(crop));
        for (int season = 1; season <= 3; season++) {
            var duration = tile.crop().currentDuration();Instant start = clock.instant();
            for (Duration t = Duration.ZERO; t.compareTo(duration) < 0; t = t.plus(tile.crop().moistDuration()))tile.water(start.plus(t));
            clock.advanceSeconds(duration.toSeconds());tile.refresh(clock.instant());
            if (season < 3)tile.harvest(clock.instant());
        }
        assertEquals(Instant.parse("2026-11-22T03:00:00Z"), clock.instant());
        game.selectTool(Tool.HAND);assertTrue(game.act(0, 0).success());
        assertEquals(0, game.progress().currentXp().compareTo(new BigDecimal("23.25")));
    }

    @Test void sundayDoesNotDoublePlantingOrWateringXp() {
        var clock = new ManualClock();clock.advanceSeconds(Duration.between(clock.instant(), Instant.parse("2026-11-22T03:00:00Z")).toSeconds());
        var game = new GameState(clock::instant, clock, catalog, bound -> 99);
        game.farm().tile(0, 0).plow();game.inventory().addSeedLot(TestCrops.origin(catalog.get("nabo")));
        game.selectTool(Tool.SEED);assertTrue(game.act(0, 0).success());
        game.selectTool(Tool.WATER);assertTrue(game.act(0, 0).success());
        assertEquals(0, game.progress().currentXp().compareTo(BigDecimal.valueOf(game.progress().rules().plantXp()+game.progress().rules().waterXp())));
    }

    private void assertHarvest(String id, String readyAt, String harvestAt, String expectedXp) {
        var clock = new ManualClock();
        var crop = catalog.get(id);
        Instant ready = Instant.parse(readyAt);
        clock.advanceSeconds(Duration.between(clock.instant(), ready.minusSeconds(crop.growthSeconds())).toSeconds());
        var game = new GameState(clock::instant, clock, catalog, bound -> 99);
        while (game.progress().level() < 10) game.progress().award(game.progress().xpToNext().subtract(game.progress().currentXp()));
        var tile = game.farm().tile(0, 0); tile.plow(); tile.plant(crop, clock.instant(), TestCrops.origin(crop));
        Instant start = clock.instant();
        for (Duration t = Duration.ZERO; t.compareTo(Duration.ofSeconds(crop.growthSeconds())) < 0; t = t.plus(tile.crop().moistDuration()))
            tile.water(start.plus(t));
        clock.advanceSeconds(crop.growthSeconds()); tile.refresh(clock.instant());
        assertEquals(TileState.READY, tile.state());
        clock.advanceSeconds(Duration.between(clock.instant(), Instant.parse(harvestAt)).toSeconds());
        var gold = game.wallet().coins(); var green = game.greenCoins();
        game.selectTool(Tool.HAND);
        assertTrue(game.act(0, 0).success());
        assertEquals(0, game.progress().currentXp().compareTo(new BigDecimal(expectedXp)));
        assertEquals(gold, game.wallet().coins()); assertEquals(green.add(BigDecimal.ONE), game.greenCoins(), "a missão de colheita continua premiando apenas 1 verde");
        assertFalse(game.act(0, 0).success());
        assertEquals(0, game.progress().currentXp().compareTo(new BigDecimal(expectedXp)));
    }
}


