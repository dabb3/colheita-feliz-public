package br.com.colheitafeliz.core;

import br.com.colheitafeliz.game.CropCatalogLoader;
import br.com.colheitafeliz.game.GameState;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HeadlessCoreTest {
    @Test void economicEngineRunsWithoutDesktopFramework() {
        assertThrows(ClassNotFoundException.class, () -> Class.forName("com.badlogic.gdx.Gdx"));
        var clock = Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC);
        var catalog = CropCatalogLoader.load();
        var game = new GameState(clock::instant, clock, catalog, bound -> 99);
        game.startNewFarm();
        assertEquals(33, catalog.all().size());
        assertEquals(0, new BigDecimal("500").compareTo(game.wallet().coins()));
        assertEquals(0, new BigDecimal("5").compareTo(game.greenCoins()));
        var restored = GameState.restore(clock::instant, clock, catalog, game.snapshot(), bound -> 99);
        assertEquals(game.snapshot(), restored.snapshot());
    }
}
