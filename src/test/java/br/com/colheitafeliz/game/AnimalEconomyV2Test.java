package br.com.colheitafeliz.game;

import br.com.colheitafeliz.domain.animal.Animal;
import br.com.colheitafeliz.domain.animal.AnimalType;
import br.com.colheitafeliz.domain.economy.Currency;
import br.com.colheitafeliz.domain.economy.Market;
import br.com.colheitafeliz.testsupport.ManualClock;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class AnimalEconomyV2Test {
    // Independent approved fixtures: original GOLD proposal and GREEN alternative B CSVs.
    @ParameterizedTest
    @CsvSource({"galinha,8480,8600,9.2,9.4", "abelha,29200,29470,28.4,28.8",
            "pombo,40480,41560,37.4,38.6", "coelho,67440,69240,61.4,62.6",
            "pato,73000,73720,70.0,70.6", "pavao,106240,107710,101.0,102.0",
            "porco,164720,168320,104.8,107.2", "burro,169840,174700,106.4,109.6",
            "ovelha,186240,187740,122.0,123.0", "vaca,228160,230140,182.6,184.0"})
    void approvedSalesThroughActualFeedingCollectionAndSettlement(String id, String gold, String vipGold, String green, String vipGreen) {
        for (var currency : Currency.values()) for (boolean vip : new boolean[]{false, true}) {
            var clock = new ManualClock();
            var game = rich(clock);
            if (vip) assertTrue(game.buyVip().success());
            var type = game.animalType(id);
            assertTrue(game.buyAnimal(type, currency).success());
            assertFalse(game.buyAnimal(type, currency).success());
            int meals = 1;
            while (game.barn().get(0).statusAt(game.now()) != Animal.Status.READY) {
                var animal = game.barn().get(0);
                if (animal.hungryAt(game.now())) {
                    var before = game.wallet().coins();
                    assertTrue(game.buyFeed(type).success());
                    assertEquals(0, before.subtract(game.wallet().coins()).compareTo(BigDecimal.valueOf(type.feedPrice())));
                    assertTrue(game.tendAnimal(id).success()); meals++;
                }
                clock.advanceSeconds(Math.min(type.foodDuration().toSeconds(), game.barn().get(0).remainingAt(game.now()).toSeconds()));
            }
            assertEquals((type.requiredMillis()+type.foodDuration().toMillis()-1)/type.foodDuration().toMillis(), meals);
            // Isolate price from the unchanged daily mission rewards.
            game.dailyMissions().restore(LocalDate.ofInstant(clock.instant(), clock.getZone()).toString(),
                    Map.of("care",1,"trade",1),List.of("care","trade"),LocalDate.ofInstant(clock.instant(),clock.getZone()));
            var before = game.wallet().balance(currency);
            var expected = new BigDecimal(currency == Currency.GOLD ? (vip ? vipGold : gold) : (vip ? vipGreen : green));
            if (type.kind() == AnimalType.Kind.PRODUCER) {
                assertTrue(game.tendAnimal(id).success());
                var lot = game.inventory().produceLots().getFirst();
                assertEquals(0, expected.compareTo(Market.unitRevenue(lot,vip)));
                assertTrue(game.sellProduct(type,false).success());
                assertFalse(game.sellProduct(type,false).success());
            } else assertTrue(game.sellAnimal(0).success());
            assertEquals(0, expected.compareTo(game.wallet().balance(currency).subtract(before)),id+currency+vip);
            assertEquals(0,game.barn().size());
            assertFalse(game.tendAnimal(id).success());
        }
    }

    static GameState rich(ManualClock clock) {
        var game = new GameState(clock::instant, clock, CropCatalogLoader.load(), bound -> bound-1);
        while (game.progress().level()<60) game.progress().award(game.progress().xpToNext());
        game.wallet().credit(Currency.GOLD,new BigDecimal("10000000"));
        game.wallet().credit(Currency.GREEN,new BigDecimal("10000"));
        return game;
    }
}
