package br.com.colheitafeliz.game;

import br.com.colheitafeliz.domain.farm.FarmTile;
import br.com.colheitafeliz.domain.farm.TileState;
import br.com.colheitafeliz.domain.economy.Currency;
import br.com.colheitafeliz.domain.inventory.ItemLot;
import br.com.colheitafeliz.testsupport.ManualClock;
import br.com.colheitafeliz.testsupport.TestCrops;
import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VisitServiceTest {
    private final ManualClock clock = new ManualClock();
    private final GameState owner = state();
    private final GameState visitor = state();
    private final VisitService visits = new VisitService();
    private GameState state() { return new GameState(clock::instant, clock, CropCatalogLoader.load(), bound -> bound - 1); }
    private FarmTile plant(String id, boolean ready) {
        var tile = owner.farm().tile(0, 0);
        tile.plow();
        tile.plant(owner.catalog().get(id), clock.instant(), new ItemLot(id, Currency.GREEN, new BigDecimal("7.3"), 1, true));
        if (ready) {
            var now = TestCrops.growUntilReady(tile, clock.instant());
            clock.advanceSeconds(Duration.between(clock.instant(), now).toSeconds());
        }
        return tile;
    }
    private void level(GameState state, int level) {
        while (state.progress().level() < level) state.progress().award(state.progress().xpToNext());
    }
    @Test void theftTransfersOriginWithoutRewardsAndLeavesOnlyTwoStrawberrySeasons() {
        var tile = plant("morango", true);
        var origin = tile.crop().origin();
        var ownerBefore = owner.snapshot();
        var visitorBefore = visitor.snapshot();
        long revision = tile.harvestRevision();
        assertTrue(visits.steal(owner, visitor, 0, 0, revision).success());
        assertEquals(java.util.List.of(origin), visitor.inventory().produceLots());
        assertEquals(0, owner.inventory().product("morango"));
        assertEquals(2, tile.crop().season());
        assertFalse(tile.crop().watered());
        assertFalse(tile.fertilized());
        assertEquals(revision + 1, tile.harvestRevision());
        assertFalse(visits.steal(owner, visitor, 0, 0, revision).success());
        for (int i = 0; i < 2; i++) {
            var ready = TestCrops.growUntilReady(tile, clock.instant());
            clock.advanceSeconds(Duration.between(clock.instant(), ready).toSeconds());
            owner.inventory().addProduceLot(tile.harvest(clock.instant()));
        }
        assertEquals(2, owner.inventory().product("morango"));
        assertEquals(TileState.DEAD, tile.state());
        assertEquals(ownerBefore.currentXp(), owner.progress().currentXp());
        assertEquals(visitorBefore.currentXp(), visitor.progress().currentXp());
        assertEquals(ownerBefore.coins(), owner.wallet().coins());
        assertEquals(visitorBefore.greenCoins(), visitor.greenCoins());
        assertEquals(ownerBefore.dailyMissionProgress(), owner.dailyMissions().progressSnapshot());
        assertEquals(visitorBefore.dailyMissionProgress(), visitor.dailyMissions().progressSnapshot());
    }
    @Test void lastSeasonTheftAndOwnerHarvestInterleavingsConsumeAtMostOneUnit() {
        var tile = plant("nabo", true);
        long revision = tile.harvestRevision();
        assertTrue(visits.steal(owner, visitor, 0, 0, revision).success());
        assertEquals(TileState.DEAD, tile.state());
        owner.selectTool(Tool.HAND);
        assertFalse(owner.act(0, 0).success());
        tile.clearDead();
        assertEquals(revision + 1, tile.harvestRevision());
        tile.plant(owner.catalog().get("nabo"), clock.instant());
        var now = TestCrops.growUntilReady(tile, clock.instant());
        clock.advanceSeconds(Duration.between(clock.instant(), now).toSeconds());
        long next = tile.harvestRevision();
        assertTrue(owner.act(0, 0).success());
        assertFalse(visits.steal(owner, visitor, 0, 0, next).success());
        assertFalse(visits.steal(owner, visitor, 0, 0, revision).success());
        assertEquals(1, owner.inventory().product("nabo"));
        assertEquals(1, visitor.inventory().product("nabo"));
    }
    @Test void growingOwnLockedInvalidAndFullDestinationRefuseWithoutConsuming() {
        var tile = plant("nabo", false);
        long revision = tile.harvestRevision();
        assertFalse(visits.steal(owner, visitor, 0, 0, revision).success());
        assertFalse(visits.steal(owner, owner, 0, 0, revision).success());
        assertFalse(visits.steal(owner, visitor, 1, 0, revision).success());
        assertFalse(visits.steal(owner, visitor, -1, 0, revision).success());
        var now = TestCrops.growUntilReady(tile, clock.instant());
        clock.advanceSeconds(Duration.between(clock.instant(), now).toSeconds());
        visitor.inventory().addProduceLot(new ItemLot("nabo", Currency.GOLD, BigDecimal.ONE, Integer.MAX_VALUE, false));
        assertFalse(visits.steal(owner, visitor, 0, 0, revision).success());
        assertEquals(TileState.READY, tile.state());
        assertEquals(revision, tile.harvestRevision());
        assertEquals(Integer.MAX_VALUE, visitor.inventory().product("nabo"));
    }
    @Test void loveSpendsOnlyVisitorStockWithNoRewardsAndSharesTheOwnerLimitBothOrders() {
        var tile = plant("morango", false);
        tile.water(clock.instant());
        level(visitor, 15);
        visitor.inventory().addFertilizer("amor", 2);
        var before = visitor.snapshot();
        var ownerBefore = owner.snapshot();
        assertTrue(visits.fertilizeVisit(owner, visitor, 0, 0, "amor", tile.harvestRevision()).success());
        assertTrue(tile.fertilized());
        assertEquals(Duration.ofHours(1).toMillis(), tile.crop().grownMillisAt(clock.instant()));
        assertEquals(1, visitor.inventory().fertilizer("amor"));
        assertEquals(0, owner.inventory().fertilizer("amor"));
        assertEquals(ownerBefore.currentXp(), owner.progress().currentXp());
        assertEquals(ownerBefore.coins(), owner.wallet().coins());
        assertEquals(ownerBefore.greenCoins(), owner.greenCoins());
        assertEquals(ownerBefore.dailyMissionProgress(), owner.dailyMissions().progressSnapshot());
        assertTrue(owner.inventory().produceLots().isEmpty());
        owner.inventory().addFertilizer("comum", 1);
        owner.selectFertilizer("comum"); owner.selectTool(Tool.FERTILIZER);
        assertFalse(owner.act(0, 0).success());
        assertEquals(1, owner.inventory().fertilizer("comum"));
        var ready = TestCrops.growUntilReady(tile, clock.instant());
        clock.advanceSeconds(Duration.between(clock.instant(), ready).toSeconds());
        tile.harvest(clock.instant());
        tile.water(clock.instant());
        assertTrue(owner.act(0, 0).success());
        assertFalse(visits.fertilizeVisit(owner, visitor, 0, 0, "amor", tile.harvestRevision()).success());
        assertEquals(1, visitor.inventory().fertilizer("amor"));
        assertEquals(before.currentXp(), visitor.progress().currentXp());
        assertEquals(before.coins(), visitor.wallet().coins());
        assertEquals(before.greenCoins(), visitor.greenCoins());
        assertEquals(before.dailyMissionProgress(), visitor.dailyMissions().progressSnapshot());
        assertTrue(visitor.inventory().produceLots().isEmpty());
    }
    @Test void invalidLoveNeverDebitsEitherPlayer() {
        var tile = plant("morango", false);
        visitor.inventory().addFertilizer("amor", 1);
        long revision = tile.harvestRevision();
        assertFalse(visits.fertilizeVisit(owner, visitor, 0, 0, "amor", revision).success());
        level(visitor, 15);
        assertFalse(visits.fertilizeVisit(owner, visitor, 0, 0, "amor", revision).success());
        tile.water(clock.instant());
        assertFalse(visits.fertilizeVisit(owner, owner, 0, 0, "amor", revision).success());
        assertFalse(visits.fertilizeVisit(owner, visitor, 0, 0, "comum", revision).success());
        assertFalse(visits.fertilizeVisit(owner, visitor, 0, 0, "unknown", revision).success());
        assertFalse(visits.fertilizeVisit(owner, visitor, 0, 0, "amor", revision - 1).success());
        tile.checkPest(clock.instant(), true);
        assertFalse(visits.fertilizeVisit(owner, visitor, 0, 0, "amor", revision).success());
        tile.removePest(clock.instant()); tile.checkInsects(clock.instant(), true);
        assertFalse(visits.fertilizeVisit(owner, visitor, 0, 0, "amor", revision).success());
        tile.removeInsects(clock.instant());
        clock.advanceSeconds(tile.crop().moistDuration().toSeconds());
        assertFalse(visits.fertilizeVisit(owner, visitor, 0, 0, "amor", revision).success());
        assertEquals(1, visitor.inventory().fertilizer("amor"));
        assertFalse(tile.fertilized());
        tile.water(clock.instant()); visitor.inventory().useFertilizer("amor");
        assertFalse(visits.fertilizeVisit(owner, visitor, 0, 0, "amor", revision).success());
    }

    @Test void exhaustedRevisionAndFullOwnerInventoryNeverSpendSeedOrSeason() {
        var tile = plant("nabo", true);
        var crop = tile.crop();
        tile.restore(TileState.READY, crop, br.com.colheitafeliz.domain.farm.PestState.NONE, null, 0, null, null, false, Long.MAX_VALUE);
        assertFalse(visits.steal(owner, visitor, 0, 0, Long.MAX_VALUE).success());
        owner.selectTool(Tool.HAND);
        assertFalse(owner.act(0, 0).success());
        assertThrows(ArithmeticException.class, () -> tile.harvest(clock.instant()));
        assertEquals(crop, tile.crop());
        assertEquals(TileState.READY, tile.state());
        assertEquals(Long.MAX_VALUE, tile.harvestRevision());
        assertTrue(visitor.inventory().produceLots().isEmpty());
        tile.restore(TileState.PLOWED, null, br.com.colheitafeliz.domain.farm.PestState.NONE, null, 0, null, null, false, Long.MAX_VALUE);
        owner.inventory().addSeedLot(TestCrops.origin(owner.catalog().get("nabo")));
        owner.selectSeed(owner.catalog().get("nabo"));
        assertFalse(owner.act(0, 0).success());
        assertThrows(ArithmeticException.class, () -> tile.plant(owner.catalog().get("nabo"), clock.instant()));
        assertEquals(1, owner.inventory().seeds(owner.catalog().get("nabo")));
        assertEquals(TileState.PLOWED, tile.state());
        assertEquals(Long.MAX_VALUE, tile.harvestRevision());
        tile.restore(TileState.READY, crop, br.com.colheitafeliz.domain.farm.PestState.NONE, null, 0, null, null, false, 50);
        owner.inventory().addProduceLot(new ItemLot("nabo", Currency.GREEN, BigDecimal.ONE, Integer.MAX_VALUE, false));
        owner.selectTool(Tool.HAND);
        assertFalse(owner.act(0, 0).success());
        assertEquals(50, tile.harvestRevision());
        assertEquals(crop, tile.crop());
    }
}
