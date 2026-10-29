package br.com.colheitafeliz.game;

import br.com.colheitafeliz.domain.animal.Animal;
import br.com.colheitafeliz.domain.animal.AnimalType;
import br.com.colheitafeliz.testsupport.ManualClock;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AnimalTest {
    static GameState rich(ManualClock clock) {
        var game = new GameState(clock::instant, clock, CropCatalogLoader.load(), bound -> 99);
        while (game.progress().level() < 60) game.progress().award(game.progress().xpToNext());
        game.wallet().credit(10_000_000);
        return game;
    }

    @Test void purchaseIncludesFoodAndHenIsReadyAfterEightHoursOffline() {
        var clock = new ManualClock(); var game = rich(clock);
        assertTrue(game.buyAnimal(game.animalType("galinha")).success());
        assertEquals(Animal.Status.PRODUCING, game.barn().get(0).statusAt(game.now()));
        assertTrue(game.inventory().feedSnapshot().isEmpty());
        clock.advanceSeconds(8 * 3600);
        assertEquals(Animal.Status.READY, game.barn().get(0).statusAt(game.now()));
    }

    @Test void beeStopsAtInitialFoodAndOnlyNeedsFourMoreHours() {
        var clock = new ManualClock(); var game = rich(clock);
        var bee = game.animalType("abelha");
        assertTrue(game.buyAnimal(bee).success());
        assertTrue(game.buyFeed(bee).success());
        assertFalse(game.tendAnimal(0).success());
        clock.advanceSeconds(48 * 3600);
        assertEquals(Animal.Status.HUNGRY, game.barn().get(0).statusAt(game.now()));
        assertTrue(game.tendAnimal(0).success());
        clock.advanceSeconds(4 * 3600 - 1);
        assertEquals(Animal.Status.PRODUCING, game.barn().get(0).statusAt(game.now()));
        clock.advanceSeconds(1);
        assertEquals(Animal.Status.READY, game.barn().get(0).statusAt(game.now()));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"galinha", "abelha", "pato", "pavao", "ovelha", "vaca"})
    void everyProducerCanBeFedCollectedAndItsProductSold(String id) {
        var clock = new ManualClock(); var game = rich(clock);
        var type = game.animalType(id);
        assertTrue(game.buyAnimal(type).success());
        while (game.barn().get(0).statusAt(game.now()) != Animal.Status.READY) {
            if (game.barn().get(0).statusAt(game.now()) == Animal.Status.HUNGRY) {
                assertTrue(game.buyFeed("producer").success());
                assertTrue(game.tendAnimal(id).success());
            }
            clock.advanceSeconds(8 * 3600);
        }
        assertTrue(game.tendAnimal(id).success());
        assertEquals(0, game.barn().size());
        assertEquals(1, game.inventory().product(type.productId()));
        assertFalse(game.tendAnimal(id).success());
        var before = game.wallet().coins();
        assertTrue(game.sellProduct(type, false).success());
        assertTrue(game.wallet().coins().compareTo(before) > 0);
        assertEquals(0, game.inventory().product(type.productId()));
        assertFalse(game.sellProduct(type, false).success());
    }

    @Test void fifteenTypesRespectTheirOwnLimitAndLevel() {
        var clock = new ManualClock(); var game = rich(clock);
        assertEquals(15, game.animalTypes().size());
        assertThrows(IllegalArgumentException.class, () -> game.animalType("cachorro"));
        for (var type : game.animalTypes()) {
            assertTrue(game.buyAnimal(type).success(), type.id());
            assertFalse(game.buyAnimal(type).success(), type.id());
        }
        assertEquals(15, game.barn().size());
    }

    @Test void greenHenHasOneEggOneXpAwardAndOneSaleWithOriginalCost() {
        var clock = new ManualClock(); var game = rich(clock);
        var hen = game.animalType("galinha");
        game.wallet().credit(br.com.colheitafeliz.domain.economy.Currency.GREEN, new java.math.BigDecimal("9"));
        assertTrue(game.buyAnimal(hen, br.com.colheitafeliz.domain.economy.Currency.GREEN).success());
        assertTrue(game.buyFeed("producer").success());
        var before = game.progress().currentXp();
        assertFalse(game.tendAnimal(0).success());
        assertEquals(1, game.inventory().feed("producer"));
        clock.advanceSeconds(48 * 3600);
        assertTrue(game.tendAnimal(0).success());
        assertEquals(0, game.barn().size());
        assertEquals(before.add(new java.math.BigDecimal("100")), game.progress().currentXp());
        assertFalse(game.tendAnimal(0).success());
        var lot = game.inventory().produceLots().getFirst();
        assertEquals(1, lot.quantity());
        assertEquals(0, new java.math.BigDecimal("9").compareTo(lot.unitCost()));
        var green = game.greenCoins();
        assertTrue(game.sellProduct(hen, false).success());
        assertEquals(0, new java.math.BigDecimal("11.4").compareTo(game.greenCoins().subtract(green)));
        assertFalse(game.sellProduct(hen, false).success());
        assertTrue(game.buyAnimal(hen).success());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"pombo,12,24,180", "coelho,16,32,240", "porco,20,40,360", "burro,24,48,400"})
    void growingAnimalsPauseWithHungerAndSellOnlyAsAdults(String id, int babyHours, int youngHours, int xp) {
        var clock = new ManualClock(); var game = rich(clock);
        var type = game.animalType(id);
        assertTrue(game.buyAnimal(type).success());
        assertEquals(Animal.Stage.BABY, game.barn().get(0).stageAt(game.now()));
        assertFalse(game.sellAnimal(0).success());
        clock.advanceSeconds(48 * 3600);
        assertEquals(12 * 3600_000L, game.barn().get(0).grownMillisAt(game.now()));
        long progressed = 12;
        while (progressed < babyHours + youngHours) {
            assertTrue(game.buyFeed("growth").success());
            assertTrue(game.tendAnimal(0).success());
            long next = Math.min(12, babyHours + youngHours - progressed);
            clock.advanceSeconds(next * 3600); progressed += next;
            if (progressed < babyHours + youngHours) assertFalse(game.sellAnimal(0).success());
        }
        assertEquals(Animal.Stage.ADULT, game.barn().get(0).stageAt(game.now()));
        var gold = game.wallet().coins(); var before = game.progress().currentXp();
        assertTrue(game.sellAnimal(0).success());
        assertEquals(0, game.barn().size());
        assertEquals(before.add(java.math.BigDecimal.valueOf(xp)), game.progress().currentXp());
        assertTrue(game.wallet().coins().compareTo(gold) > 0);
        assertFalse(game.sellAnimal(0).success());
        assertTrue(game.buyAnimal(type).success());
    }

    @Test void fiveProtectorsEatBothFeedsButExpireAfterThirtyRealDays() {
        var real = new ManualClock(); var simulation = new ManualClock();
        var game = new GameState(simulation::instant, real, CropCatalogLoader.load(), bound -> 99);
        while (game.progress().level() < 60) game.progress().award(game.progress().xpToNext());
        game.wallet().credit(10_000_000);
        var protectors = game.animalTypes().stream().filter(t -> t.kind() == AnimalType.Kind.PROTECTOR).toList();
        assertEquals(5, protectors.size());
        for (var type : protectors) {
            assertTrue(game.buyAnimal(type).success()); assertFalse(game.buyAnimal(type).success());
        }
        assertEquals(5, game.activeProtectors());
        var expires = game.barn().get(0).expiresAt();
        simulation.advanceSeconds(6 * 3600);
        assertEquals(0, game.activeProtectors());
        assertTrue(game.buyFeed("producer").success());
        assertTrue(game.tendAnimal(0).success());
        assertEquals(1, game.activeProtectors());
        assertEquals(expires, game.barn().get(0).expiresAt());
        assertTrue(game.buyFeed("growth").success());
        assertTrue(game.tendAnimal(1).success());
        assertFalse(game.sellAnimal(0).success());
        simulation.advanceSeconds(60 * 86400L);
        game.refresh(); assertEquals(5, game.barn().size(), "validade não usa tempo acelerado");
        real.advanceSeconds(30 * 86400L - 1);
        game.refresh(); assertEquals(5, game.barn().size());
        real.advanceSeconds(1); game.refresh(); assertEquals(0, game.barn().size());
    }

    @Test void fullDepositRefusesCollectionWithoutRemovingProducerOrAwardingXp() {
        var clock = new ManualClock(); var game = rich(clock);
        assertTrue(game.buyAnimal(game.animalType("galinha")).success());
        game.inventory().addProduceLot(new br.com.colheitafeliz.domain.inventory.ItemLot("ovo",
                br.com.colheitafeliz.domain.economy.Currency.GOLD, new java.math.BigDecimal("45"), Integer.MAX_VALUE, false));
        clock.advanceSeconds(8 * 3600);
        var before = game.progress().currentXp();
        assertFalse(game.tendAnimal(0).success());
        assertEquals(1, game.barn().size());
        assertEquals(Integer.MAX_VALUE, game.inventory().product("ovo"));
        assertEquals(before, game.progress().currentXp());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"pombo,12,36", "coelho,16,48", "porco,20,60", "burro,24,72"})
    void growingStageBoundariesCountOnlyFedSeconds(String id, int babyHours, int totalHours) {
        var type = AnimalCatalogLoader.load().stream().filter(t -> t.id().equals(id)).findFirst().orElseThrow();
        var start = java.time.Instant.parse("2026-01-01T00:00:00Z");
        var origin = new br.com.colheitafeliz.domain.inventory.ItemLot(id, br.com.colheitafeliz.domain.economy.Currency.GREEN,
                type.prices().get(br.com.colheitafeliz.domain.economy.Currency.GREEN), 1, false);
        long[] seconds = {babyHours * 3600L - 1, babyHours * 3600L, totalHours * 3600L - 1, totalHours * 3600L};
        Animal.Stage[] want = {Animal.Stage.BABY, Animal.Stage.YOUNG, Animal.Stage.YOUNG, Animal.Stage.ADULT};
        for (int i = 0; i < seconds.length; i++) {
            Animal animal = Animal.acquire(type, origin, start);
            for (long fed = 12 * 3600; fed < seconds[i]; fed += 12 * 3600) animal = animal.feed(start.plusSeconds(fed));
            assertEquals(want[i], animal.stageAt(start.plusSeconds(seconds[i])));
        }
    }

    @Test void nightChangesNoDomainProgressAndMissingOriginOrImpossibleProgressIsRejected() {
        var type = AnimalCatalogLoader.load().getFirst();
        var start = java.time.Instant.parse("2026-01-01T22:00:00Z");
        var origin = new br.com.colheitafeliz.domain.inventory.ItemLot("galinha", br.com.colheitafeliz.domain.economy.Currency.GOLD,
                new java.math.BigDecimal("8000"), 1, false);
        assertEquals(Animal.Status.READY, Animal.acquire(type, origin, start).statusAt(start.plusSeconds(8 * 3600)));
        assertThrows(NullPointerException.class, () -> Animal.acquire(type, null, start));
        assertThrows(IllegalArgumentException.class, () -> new Animal(type, origin, start, start, -1, start.plusSeconds(8 * 3600)));
        assertThrows(IllegalArgumentException.class, () -> new Animal(type, origin, start, start, 28_800_001, start.plusSeconds(8 * 3600)));
        assertThrows(IllegalArgumentException.class, () -> new Animal(type, origin, start, start, 0, start.minusSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> new Animal(type, origin, start, start, 0, start.plusSeconds(9 * 3600)));
    }

    @Test void repeatedUiTargetCannotCollectTheNextAnimalAfterRemoval() {
        var clock = new ManualClock(); var game = rich(clock);
        assertTrue(game.buyAnimal(game.animalType("galinha")).success());
        assertTrue(game.buyAnimal(game.animalType("abelha")).success());
        clock.advanceSeconds(8 * 3600);
        assertTrue(game.tendAnimal("galinha").success());
        var bee = game.barn().get(0);
        assertTrue(game.buyFeed("producer").success());
        assertFalse(game.tendAnimal("galinha").success());
        assertSame(bee, game.barn().get(0));
        assertEquals(1, game.inventory().feed("producer"));
        assertEquals(1, game.inventory().product("ovo"));
        assertEquals(0, game.inventory().product("mel"));
        assertTrue(game.tendAnimal("abelha").success());
    }
}
