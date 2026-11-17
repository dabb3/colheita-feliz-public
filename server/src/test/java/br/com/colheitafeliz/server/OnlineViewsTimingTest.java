package br.com.colheitafeliz.server;

import br.com.colheitafeliz.domain.crop.Crop;
import br.com.colheitafeliz.game.CropCatalogLoader;
import br.com.colheitafeliz.game.GameState;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OnlineViewsTimingTest {
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test void eventProjectionUsesServerClockAndExcludesLoginRewardAndVipCrops() {
        var clock = Clock.fixed(Instant.parse("2026-11-22T03:00:00Z"), ZoneOffset.UTC);
        var game = new GameState(clock::instant, clock, CropCatalogLoader.load(), bound -> 99);
        var event = OnlineViews.farm(OWNER, "Fixture", game, 0, true).getAsJsonObject("sundayHarvestEvent");
        assertEquals("ACTIVE", event.get("state").getAsString());
        assertEquals("2026-11-23T03:00:00Z", event.get("endsAt").getAsString());
        assertEquals(2, event.get("multiplier").getAsInt());
        for (var crop : game.catalog().all()) {
            boolean included = event.getAsJsonArray("cropIds").contains(new com.google.gson.JsonPrimitive(crop.id()));
            assertEquals(!crop.vipOnly() && crop.acquisitionSource() == br.com.colheitafeliz.domain.inventory.AcquisitionSource.SHOP, included);
        }
    }

    @Test void farmProjectionSeparatesWallClockEtaFromNominalGrowthAcrossCropStates() {
        var clock = new TestClock();
        var game = new GameState(clock::instant, clock, CropCatalogLoader.load(), bound -> bound - 1);
        game.startNewFarm();
        var tile = game.farm().tile(0, 0);
        var crop = game.catalog().get("nabo");
        long duration = crop.growthTime(1).toSeconds();
        long moist = Math.max(Crop.MIN_MOIST_SECONDS, duration / 3);
        tile.plow();
        tile.plant(crop, clock.instant());

        var unwatered = tileView(game);
        assertFalse(unwatered.get("watered").getAsBoolean());
        assertEquals(duration, unwatered.get("remainingSeconds").getAsLong());
        assertEquals(duration, unwatered.get("nominalRemainingSeconds").getAsLong());

        tile.water(clock.instant());
        var wet = tileView(game);
        assertTrue(wet.get("watered").getAsBoolean());
        assertFalse(wet.get("dry").getAsBoolean());
        assertEquals(moist + 2 * (duration - moist), wet.get("remainingSeconds").getAsLong());
        assertEquals(duration, wet.get("nominalRemainingSeconds").getAsLong());

        clock.advanceSeconds(120);
        game.refresh();
        var growing = tileView(game);
        assertEquals(wet.get("remainingSeconds").getAsLong() - 120, growing.get("remainingSeconds").getAsLong());
        assertEquals(wet.get("nominalRemainingSeconds").getAsLong() - 120, growing.get("nominalRemainingSeconds").getAsLong());

        clock.advanceSeconds(moist - 120);
        game.refresh();
        var dry = tileView(game);
        assertTrue(dry.get("dry").getAsBoolean());
        assertEquals(dry.get("nominalRemainingSeconds").getAsLong() * 2, dry.get("remainingSeconds").getAsLong());

        tile.checkPest(clock.instant(), true);
        var paused = tileView(game);
        assertTrue(paused.get("paused").getAsBoolean());
        clock.advanceSeconds(300);
        game.refresh();
        var stillPaused = tileView(game);
        assertEquals(paused.get("remainingSeconds"), stillPaused.get("remainingSeconds"));
        assertEquals(paused.get("nominalRemainingSeconds"), stillPaused.get("nominalRemainingSeconds"));

        var readyClock = new TestClock();
        var readyGame = new GameState(readyClock::instant, readyClock, CropCatalogLoader.load(), bound -> bound - 1);
        readyGame.startNewFarm();
        var readyTile = readyGame.farm().tile(0, 0);
        readyTile.plow();
        readyTile.plant(readyGame.catalog().get("nabo"), readyClock.instant());
        readyTile.water(readyClock.instant());
        for (int season = 1; season < 3; season++) {
            readyClock.advanceSeconds(moist);
            readyTile.water(readyClock.instant());
        }
        readyClock.advanceSeconds(moist);
        readyGame.refresh();
        var ready = tileView(readyGame);
        assertEquals("READY", ready.get("state").getAsString());
        assertEquals(0, ready.get("remainingSeconds").getAsInt());
        assertEquals(0, ready.get("nominalRemainingSeconds").getAsInt());
    }

    private static com.google.gson.JsonObject tileView(GameState game) {
        return OnlineViews.farm(OWNER, "Alice", game, 1, true).getAsJsonArray("tiles").get(0).getAsJsonObject();
    }

    private static final class TestClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T12:00:00Z");
        void advanceSeconds(long seconds) { now = now.plusSeconds(seconds); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
