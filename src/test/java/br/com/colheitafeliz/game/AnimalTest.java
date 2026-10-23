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

    @Test void buyingRespectsLevelCoinsAndPenSize() {
        ManualClock clock = new ManualClock();
        GameState game = new GameState(clock::instant, clock, CropCatalogLoader.load(), bound -> 99);
        AnimalType hen = game.animalType("galinha");
        assertFalse(game.buyAnimal(hen).success());
        assertTrue(game.message().contains("nível 3"));
        game.progress().award(50 + 75);
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
        int xp = game.progress().currentXp();
        assertTrue(game.tendAnimal(0).success());
        assertEquals(hen.productYield(), game.lastCollected());
        assertEquals(hen.productYield(), game.inventory().product("ovo"));
        assertEquals(xp + hen.collectXp(), game.progress().currentXp());
        assertEquals(Animal.Status.HUNGRY, game.barn().get(0).statusAt(game.now()));

        assertEquals(hen.productYield() * hen.productPrice(), game.depositValue());
        assertTrue(game.sellProduct(hen, false).success());
        assertEquals(hen.productYield() - 1, game.inventory().product("ovo"));
        int coins = game.wallet().coins();
        assertTrue(game.sellEverything().success());
        assertEquals(0, game.inventory().product("ovo"));
        assertTrue(game.wallet().coins() >= coins + (hen.productYield() - 1) * hen.productPrice());
    }
}
