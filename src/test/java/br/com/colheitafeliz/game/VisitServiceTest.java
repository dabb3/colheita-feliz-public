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

    @Test void visitorPestsPauseOnlyTheSelectedPlantAndOwnerToolsRemoveThemWithoutRewards() {
        var tile = plant("morango", false);
        tile.water(clock.instant());
        var before = visitor.snapshot();
        assertTrue(visits.spreadInsects(owner, visitor, 0, 0, tile.harvestRevision()).result().success());
        assertTrue(visits.spreadWeeds(owner, visitor, 0, 0, tile.harvestRevision()).result().success());
        assertEquals(1, tile.insects());
        assertEquals(br.com.colheitafeliz.domain.farm.PestState.WEEDS, tile.pest());
        assertFalse(visits.spreadInsects(owner, visitor, 0, 0, tile.harvestRevision()).result().success());
        assertFalse(visits.spreadWeeds(owner, visitor, 0, 0, tile.harvestRevision()).result().success());
        long grown = tile.crop().grownMillisAt(clock.instant());
        clock.advanceSeconds(60);
        assertEquals(grown, tile.crop().grownMillisAt(clock.instant()));
        owner.selectTool(Tool.WEEDER); assertTrue(owner.act(0, 0).success());
        assertTrue(tile.crop().paused());
        owner.selectTool(Tool.PESTICIDE); assertTrue(owner.act(0, 0).success());
        assertFalse(tile.crop().paused());
        assertEquals(before, visitor.snapshot());
        assertEquals(TileState.GRASS, owner.farm().tile(1, 0).state());
    }

    @Test void visitorCleaningUsesOwnerPestLogicAndRewardsOnlyTheVisitor() {
        var helper = GameState.newOnline(clock, CropCatalogLoader.load(), bound -> bound - 1);
        var tile = plant("morango", false);
        tile.water(clock.instant());
        assertTrue(visits.spreadInsects(owner, visitor, 0, 0, tile.harvestRevision()).result().success());
        assertTrue(visits.spreadWeeds(owner, visitor, 0, 0, tile.harvestRevision()).result().success());
        tile.observePestArrivals(clock.instant());
        var ownerXp = owner.progress().currentXp();
        var ownerInventory = owner.inventory().fertilizerSnapshot();
        var xp = helper.progress().currentXp();
        var nature = helper.dailyMissions().missions().stream().filter(m -> m.id().equals("nature")).findFirst().orElseThrow();
        var weeds = visits.cleanPest(owner, helper, 0, 0, tile.harvestRevision(), false, 2);
        assertTrue(weeds.success(), weeds.message());
        assertEquals(br.com.colheitafeliz.domain.farm.PestState.NONE, tile.pest());
        assertNull(tile.weedsSince());
        assertNotNull(tile.insectsSince());
        assertTrue(tile.crop().paused());
        assertEquals(0, xp.add(BigDecimal.valueOf(2)).compareTo(helper.progress().currentXp()));
        assertEquals(1, helper.dailyMissions().progress(nature));
        var replay = visits.cleanPest(owner, helper, 0, 0, tile.harvestRevision(), false, 2);
        assertFalse(replay.success());
        assertEquals(1, helper.dailyMissions().progress(nature));
        var green = helper.greenCoins();
        var insects = visits.cleanPest(owner, helper, 0, 0, tile.harvestRevision(), true, 0);
        assertTrue(insects.success());
        assertTrue(insects.message().contains("sem XP"), insects.message());
        assertEquals(0, xp.add(BigDecimal.valueOf(2)).compareTo(helper.progress().currentXp()));
        assertNull(tile.insectsSince());
        assertFalse(tile.crop().paused());
        assertTrue(helper.dailyMissions().completed(nature));
        assertEquals(0, green.compareTo(helper.greenCoins()));
        assertEquals(ownerXp, owner.progress().currentXp());
        assertEquals(ownerInventory, owner.inventory().fertilizerSnapshot());
        assertTrue(owner.dailyMissions().completedSnapshot().isEmpty());
    }

    private br.com.colheitafeliz.domain.animal.Animal hungry(br.com.colheitafeliz.domain.animal.AnimalType.Kind kind, long grownMillis) {
        var type = owner.animalTypes().stream().filter(t -> t.kind() == kind).findFirst().orElseThrow();
        var price = type.prices().entrySet().iterator().next();
        var lot = new ItemLot(type.id(), price.getKey(), price.getValue(), 1, false);
        var animal = new br.com.colheitafeliz.domain.animal.Animal(type, lot, clock.instant(), clock.instant(),
                grownMillis < 0 ? type.requiredMillis() : grownMillis, clock.instant());
        owner.barn().add(animal);
        return animal;
    }

    @Test void visitorFeedsHungryFriendAnimalWithOwnFeedOnceAndOnlyVisitorIsRewarded() {
        var helper = GameState.newOnline(clock, CropCatalogLoader.load(), bound -> bound - 1);
        var animal = hungry(br.com.colheitafeliz.domain.animal.AnimalType.Kind.PRODUCER, 0);
        var animals = helper.dailyMissions().missions().getLast();
        assertEquals("animals", animals.id());
        var ownerXp = owner.progress().currentXp();
        var ownerFeed = owner.inventory().feedSnapshot();
        var xp = helper.progress().currentXp();
        assertEquals("Sem ração. Compre na Loja, aba Animais.", visits.feedAnimal(owner, helper, animal.type().id(), "producer", 2).message());
        helper.inventory().addFeed("growth", 1);
        assertFalse(visits.feedAnimal(owner, helper, animal.type().id(), "growth", 2).success());
        assertEquals(1, helper.inventory().feed("growth"));
        helper.inventory().addFeed("producer", 2);
        var fed = visits.feedAnimal(owner, helper, animal.type().id(), "producer", 2);
        assertTrue(fed.success(), fed.message());
        assertFalse(owner.barn().get(0).hungryAt(clock.instant()));
        assertEquals(clock.instant().plus(animal.type().foodDuration()), owner.barn().get(0).fedUntil());
        assertEquals(1, helper.inventory().feed("producer"));
        assertEquals(0, xp.add(BigDecimal.TWO).compareTo(helper.progress().currentXp()));
        assertTrue(helper.dailyMissions().completed(animals));
        var again = visits.feedAnimal(owner, helper, animal.type().id(), "producer", 2);
        assertFalse(again.success());
        assertEquals(1, helper.inventory().feed("producer"));
        assertEquals(0, xp.add(BigDecimal.TWO).compareTo(helper.progress().currentXp()));
        assertEquals(ownerXp, owner.progress().currentXp());
        assertEquals(ownerFeed, owner.inventory().feedSnapshot());
        assertTrue(owner.dailyMissions().completedSnapshot().isEmpty());
    }

    @Test void visitorFeedingFollowsOwnerAnimalRules() {
        var helper = GameState.newOnline(clock, CropCatalogLoader.load(), bound -> bound - 1);
        helper.inventory().addFeed("growth", 5);
        helper.inventory().addFeed("producer", 5);
        var adult = hungry(br.com.colheitafeliz.domain.animal.AnimalType.Kind.GROWING, -1);
        assertFalse(visits.feedAnimal(owner, helper, adult.type().id(), "growth", 2).success());
        var ready = hungry(br.com.colheitafeliz.domain.animal.AnimalType.Kind.PRODUCER, -1);
        assertFalse(visits.feedAnimal(owner, helper, ready.type().id(), "producer", 2).success());
        assertFalse(visits.feedAnimal(owner, helper, "inexistente", "producer", 2).success());
        assertEquals(5, helper.inventory().feed("growth"));
        assertEquals(5, helper.inventory().feed("producer"));
        var guard = hungry(br.com.colheitafeliz.domain.animal.AnimalType.Kind.PROTECTOR, 0);
        var result = visits.feedAnimal(owner, helper, guard.type().id(), "producer", 0);
        assertTrue(result.success(), result.message());
        assertTrue(result.message().contains("sem XP"), result.message());
        assertEquals(4, helper.inventory().feed("producer"));
        assertEquals(1, owner.activeProtectors());
    }

    @Test void visitorPestsRejectEmptyUnwateredMatureAndStaleTargets() {
        assertFalse(visits.spreadInsects(owner, visitor, 0, 0, 0).result().success());
        var tile = plant("nabo", false);
        assertFalse(visits.spreadWeeds(owner, visitor, 0, 0, tile.harvestRevision()).result().success());
        tile.water(clock.instant());
        assertFalse(visits.spreadInsects(owner, visitor, 0, 0, tile.harvestRevision() + 1).result().success());
        assertFalse(visits.spreadWeeds(owner, owner, 0, 0, tile.harvestRevision()).result().success());
        var ready = TestCrops.growUntilReady(tile, clock.instant());
        clock.advanceSeconds(Duration.between(clock.instant(), ready).toSeconds());
        assertFalse(visits.spreadInsects(owner, visitor, 0, 0, tile.harvestRevision()).result().success());
        assertFalse(visits.spreadWeeds(owner, visitor, 0, 0, tile.harvestRevision()).result().success());
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
