package br.com.colheitafeliz.game;
import br.com.colheitafeliz.domain.economy.Currency;
import br.com.colheitafeliz.testsupport.ManualClock;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class LandExpansionTest {
    @Test void plotRequiresFiftyGreensAndOrderWithoutDebitingGold() {
        var clock = new ManualClock();
        var game = new GameState(clock::instant, clock, CropCatalogLoader.load(), bound -> 99);
        game.progress().award(50);
        game.wallet().credit(10000);
        game.wallet().credit(Currency.GREEN, new BigDecimal("44.9"));
        assertFalse(game.buyPlot(1, 0).success());
        assertFalse(game.buyPlot(2, 0).success());
        assertEquals(5, game.land().snapshot().size());
        game.wallet().credit(Currency.GREEN, new BigDecimal("0.1"));
        assertTrue(game.buyFeed("growth").success());
        game.wallet().debit(Currency.GREEN, BigDecimal.ONE);
        var gold = game.wallet().coins();
        assertTrue(game.buyPlot(1, 0).success());
        assertEquals(0, game.greenCoins().signum());
        assertEquals(gold, game.wallet().coins());
        assertTrue(game.land().isUnlocked(1, 0));
    }
}
