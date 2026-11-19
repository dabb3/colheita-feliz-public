package br.com.colheitafeliz.game;

import br.com.colheitafeliz.domain.animal.Animal;
import br.com.colheitafeliz.domain.economy.Currency;
import br.com.colheitafeliz.domain.inventory.ItemLot;
import br.com.colheitafeliz.testsupport.ManualClock;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AwayTallyTest {
    private static GameState vipGame(ManualClock clock, String... services) {
        var game = new GameState(clock::instant, clock, CropCatalogLoader.load(), n -> 99);
        game.wallet().credit(Currency.GREEN, new BigDecimal("1000"));
        game.buyVip();
        for (String service : services) game.buyAutomation(service);
        return game;
    }

    @Test void automationsCountOnlyReplayedCare() {
        var clock = new ManualClock();
        var game = vipGame(clock, "water", "pests");
        var tile = game.farm().tile(0, 0); tile.plow(); tile.plant(game.catalog().get("nabo"), clock.instant());
        tile.water(clock.instant()); tile.checkPest(clock.instant(), true);
        game.runAutomationsNow(); game.drainAwayTally();
        var start = clock.instant();
        var later = GameState.restoreOnline(start, start.plusSeconds(4 * 3600), game.catalog(), game.snapshot(), n -> 99);
        var tally = later.drainAwayTally();
        assertTrue(tally.waterings() >= 1, "waterings");
        assertEquals(1, tally.pestsRemoved());
        assertTrue(later.drainAwayTally().isEmpty());
    }

    @Test void manualActionsAreNotCounted() {
        var clock = new ManualClock();
        var game = new GameState(clock::instant, clock, CropCatalogLoader.load(), n -> 99);
        var tile = game.farm().tile(0, 0); tile.plow(); tile.plant(game.catalog().get("nabo"), clock.instant());
        game.selectTool(Tool.WATER); game.act(0, 0);
        assertTrue(game.drainAwayTally().isEmpty());
    }

    @Test void animalCaresAndCollectedProducts() {
        var clock = new ManualClock();
        var game = vipGame(clock, "animals");
        var type = game.animalType("galinha");
        var lot = new ItemLot("galinha", Currency.GOLD, type.prices().get(Currency.GOLD), 1, false).withAnimalSale(type.saleTerms(Currency.GOLD));
        game.barn().add(Animal.acquire(type, lot, clock.instant()));
        game.drainAwayTally();
        clock.advanceSeconds(40000); game.runAutomationsNow();
        var tally = game.drainAwayTally();
        assertEquals(1, tally.animalCares());
        assertEquals(Map.of("ovo", 1), tally.collected());
    }
}
