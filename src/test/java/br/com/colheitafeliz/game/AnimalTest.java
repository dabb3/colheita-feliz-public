package br.com.colheitafeliz.game;

import br.com.colheitafeliz.domain.animal.Animal;
import br.com.colheitafeliz.domain.animal.AnimalType;
import br.com.colheitafeliz.testsupport.ManualClock;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AnimalTest {
    private static GameState levelThree(ManualClock clock) {
        GameState game = new GameState(clock::instant, clock, CropCatalogLoader.load(), bound -> 99);
        game.progress().award(50 + 75);
        return game;
    }

    @Test void animalProductsUseTemporaryProductBaseAcrossTwoCycles() {
        var clock = new ManualClock();
        var game = levelThree(clock);
        var hen = game.animalType("galinha");
        game.buyAnimal(hen);
        for (int cycle = 0; cycle < 2; cycle++) {
            assertTrue(game.buyFeed(hen).success());
            assertTrue(game.tendAnimal(0).success());
            clock.advanceSeconds(hen.productionSeconds());
            assertTrue(game.tendAnimal(0).success());
        }
        assertEquals(1, game.barn().size());
        assertEquals(2, game.inventory().produceLots().size());
        for (var lot : game.inventory().produceLots()) {
            assertEquals(0, new java.math.BigDecimal("45").compareTo(lot.unitCost()));
            assertEquals(br.com.colheitafeliz.domain.economy.Currency.GOLD, lot.currency());
            assertEquals(3, lot.quantity());
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"galinha,false,155.3", "galinha,true,168.8", "vaca,false,828.0", "vaca,true,900.0"})
    void collectionSaleUsesExactProductBase(String id, boolean vip, String expected) {
        var clock = new ManualClock();
        var game = levelThree(clock);
        game.progress().award(1000);
        game.wallet().credit(10000);
        if (vip) {
            game.wallet().credit(br.com.colheitafeliz.domain.economy.Currency.GREEN, new java.math.BigDecimal("100"));
            assertTrue(game.buyVip().success());
        }
        var type = game.animalType(id);
        assertTrue(game.buyAnimal(type).success());
        assertTrue(game.buyFeed(type).success());
        assertTrue(game.tendAnimal(0).success());
        clock.advanceSeconds(type.productionSeconds());
        assertTrue(game.tendAnimal(0).success());
        assertEquals(0, new java.math.BigDecimal(expected).compareTo(game.depositValue()));
    }

    @Test void henCycleIsFeedWaitCollectAndStartsHungry() {
        AnimalType hen = AnimalCatalogLoader.load().getFirst();
        ManualClock clock = new ManualClock();
        Animal animal = Animal.hungry(hen);
        assertEquals(Animal.Status.HUNGRY, animal.statusAt(clock.instant()));
        animal = animal.feed(clock.instant());
        assertThrows(IllegalStateException.class, () -> Animal.hungry(hen).collect(clock.instant()));
        assertEquals(Animal.Status.PRODUCING, animal.statusAt(clock.instant().plusSeconds(hen.productionSeconds() - 1)));
        assertEquals(Animal.Status.READY, animal.statusAt(clock.instant().plus(hen.productionTime())));
        Animal fed = animal;
        assertThrows(IllegalStateException.class, () -> fed.feed(clock.instant()));
        assertEquals(Animal.Status.HUNGRY, animal.collect(clock.instant().plus(hen.productionTime())).statusAt(clock.instant()));
    }

    @Test void everyAnimalUnlocksAtItsLevelAndFitsItsPen() {
        ManualClock clock = new ManualClock();
        GameState game = new GameState(clock::instant, clock, CropCatalogLoader.load(), bound -> 99);
        assertEquals(java.util.List.of("galinha", "pato", "ovelha", "vaca"),
                game.animalTypes().stream().map(AnimalType::id).toList());
        game.wallet().credit(100_000);
        for (AnimalType type : game.animalTypes()) {
            while (game.progress().level() < type.requiredLevel() - 1) game.progress().award(game.progress().xpToNext());
            assertFalse(game.buyAnimal(type).success(), type.id() + " antes do nível");
            game.progress().award(game.progress().xpToNext());
            for (int i = 0; i < type.max(); i++) assertTrue(game.buyAnimal(type).success(), type.id());
            assertFalse(game.buyAnimal(type).success(), type.id() + " além do limite");
        }
        int total = game.animalTypes().stream().mapToInt(AnimalType::max).sum();
        assertEquals(total, game.barn().size());
    }

    @Test void collectMissionAppearsOnlyWithAnimalsAndPaysAfterTwoCollections() {
        ManualClock clock = new ManualClock();
        GameState game = levelThree(clock);
        assertEquals(3, game.dailyMissions().missions().size());
        AnimalType hen = game.animalType("galinha");
        game.wallet().credit(2 * hen.price() + 2 * hen.feedPrice());
        game.buyAnimal(hen);
        game.buyAnimal(hen);
        assertTrue(game.dailyMissions().missions().contains(DailyMissionBoard.COLLECT));
        game.buyFeed(hen);
        game.buyFeed(hen);
        game.tendAnimal(0);
        game.tendAnimal(1);
        clock.advanceSeconds(hen.productionSeconds());
        game.tendAnimal(0);
        assertFalse(game.dailyMissions().completed(DailyMissionBoard.COLLECT));
        java.math.BigDecimal coins = game.wallet().coins();
        assertTrue(game.tendAnimal(1).success());
        assertTrue(game.dailyMissions().completed(DailyMissionBoard.COLLECT));
        assertEquals(coins.add(java.math.BigDecimal.valueOf(DailyMissionBoard.COLLECT.coins())), game.wallet().coins());
    }

    @Test void everyAnimalPaysMoreThanItsFeed() {
        for (AnimalType type : AnimalCatalogLoader.load())
            assertTrue(type.productYield() * type.productPrice() > 3 * type.feedPrice(), type.id());
    }

    @Test void buyingRespectsLevelCoinsAndPenSize() {
        ManualClock clock = new ManualClock();
        GameState game = new GameState(clock::instant, clock, CropCatalogLoader.load(), bound -> 99);
        AnimalType hen = game.animalType("galinha");
        assertFalse(game.buyAnimal(hen).success());
        assertTrue(game.message().contains("nível 3"));
        game.progress().award(50 + 75);
        game.wallet().debit(br.com.colheitafeliz.domain.economy.Currency.GOLD, game.wallet().coins().subtract(java.math.BigDecimal.valueOf(100)));
        assertFalse(game.buyAnimal(hen).success(), "100 moedas não pagam uma galinha");
        game.wallet().credit(hen.price() * hen.max() + 100);
        for (int i = 0; i < hen.max(); i++) assertTrue(game.buyAnimal(hen).success());
        assertFalse(game.buyAnimal(hen).success());
        assertTrue(game.message().contains("cercado"));
        assertEquals(hen.max(), game.barn().size());
    }

    @Test void henNeedsFeedThenLaysEggsThatSellWithTheDeposit() {
        ManualClock clock = new ManualClock();
        GameState game = levelThree(clock);
        AnimalType hen = game.animalType("galinha");
        game.wallet().credit(hen.price());
        assertTrue(game.buyAnimal(hen).success());
        assertFalse(game.tendAnimal(0).success(), "sem ração não come");
        assertTrue(game.buyFeed(hen).success());
        assertEquals(1, game.inventory().feed("galinha"));
        assertTrue(game.tendAnimal(0).success());
        assertEquals(0, game.inventory().feed("galinha"));
        clock.advanceSeconds(hen.productionSeconds() - 60);
        assertFalse(game.tendAnimal(0).success());
        assertTrue(game.message().contains("produzindo"));
        clock.advanceSeconds(60);
        java.math.BigDecimal xp = game.progress().currentXp();
        assertTrue(game.tendAnimal(0).success());
        assertEquals(hen.productYield(), game.lastCollected());
        assertEquals(hen.productYield(), game.inventory().product("ovo"));
        assertEquals(xp.add(java.math.BigDecimal.valueOf(hen.collectXp())), game.progress().currentXp());
        assertEquals(Animal.Status.HUNGRY, game.barn().get(0).statusAt(game.now()));

        assertEquals(0, new java.math.BigDecimal("155.3").compareTo(game.depositValue()));
        java.math.BigDecimal beforeSingle = game.wallet().coins();
        assertTrue(game.sellProduct(hen, false).success());
        assertEquals(0, new java.math.BigDecimal("51.8").compareTo(game.wallet().coins().subtract(beforeSingle)));
        assertEquals(hen.productYield() - 1, game.inventory().product("ovo"));
        java.math.BigDecimal coins = game.wallet().coins();
        assertTrue(game.sellEverything().success());
        assertEquals(0, game.inventory().product("ovo"));
        assertEquals(0, new java.math.BigDecimal("103.5").compareTo(game.wallet().coins().subtract(coins)));
    }
}
