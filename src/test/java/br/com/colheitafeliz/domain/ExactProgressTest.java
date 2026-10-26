package br.com.colheitafeliz.domain;

import br.com.colheitafeliz.domain.player.PlayerProgress;
import br.com.colheitafeliz.domain.player.VipStatus;
import br.com.colheitafeliz.game.ProgressionLoader;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExactProgressTest {
    @Test void listedLevelPaysBothCurrenciesWithoutSeedGifts() {
        var clock = new br.com.colheitafeliz.testsupport.ManualClock();
        var game = new br.com.colheitafeliz.game.GameState(clock::instant, clock,
                br.com.colheitafeliz.game.CropCatalogLoader.load(), bound -> 99);
        game.progress().award(345);
        var turnip = game.catalog().get("nabo");
        game.inventory().addSeeds(turnip, 1);
        game.selectTool(br.com.colheitafeliz.game.Tool.HOE);
        assertTrue(game.act(0, 0).success());
        game.selectSeed(turnip);
        assertTrue(game.act(0, 0).success());
        assertEquals(5, game.progress().level());
        assertEquals(0, new BigDecimal("9500").compareTo(game.wallet().coins()));
        assertEquals(0, new BigDecimal("6").compareTo(game.greenCoins()));
        assertEquals(0, game.inventory().seeds(turnip));
        assertEquals(0, game.inventory().seeds(game.catalog().get("melancia")));
    }

    @Test void vipPurchaseUsesOnlyGreensAndFailurePreservesState() {
        var clock = new br.com.colheitafeliz.testsupport.ManualClock();
        var game = new br.com.colheitafeliz.game.GameState(clock::instant, clock,
                br.com.colheitafeliz.game.CropCatalogLoader.load(), bound -> 99);
        assertFalse(game.buyVip().success());
        assertNull(game.vip().expiresAt());
        game.wallet().credit(br.com.colheitafeliz.domain.economy.Currency.GREEN, new BigDecimal("95"));
        assertTrue(game.buyVip().success());
        assertEquals(0, BigDecimal.ZERO.compareTo(game.greenCoins()));
        assertEquals(0, new BigDecimal("500").compareTo(game.wallet().coins()));
        assertTrue(game.vipActive());
        clock.advanceSeconds(Duration.ofDays(30).toSeconds());
        assertFalse(game.vipActive());
    }

    @Test void fractionalXpIsNeverRounded() {
        PlayerProgress progress = new PlayerProgress(ProgressionLoader.load());
        progress.award(new BigDecimal("11.625"));
        progress.award(new BigDecimal("2.8125"));
        assertEquals(0, new BigDecimal("14.4375").compareTo(progress.currentXp()));
    }

    @Test void vipRenewalsUseRealExpiryAndKeepRemainingDays() {
        Instant now = Instant.parse("2026-10-26T00:00:00Z");
        VipStatus vip = new VipStatus(null);
        for (int i = 0; i < 10; i++) vip = vip.purchase(now);
        assertEquals(now.plus(Duration.ofDays(300)), vip.expiresAt());
        assertTrue(vip.activeAt(vip.expiresAt().minusNanos(1)));
        assertFalse(vip.activeAt(vip.expiresAt()));
        Instant later = vip.expiresAt().plusSeconds(1);
        assertEquals(later.plus(Duration.ofDays(30)), vip.purchase(later).expiresAt());
    }
}
