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
    @Test void oneHarvestPaysEveryCrossedRewardLevelOnlyOnce() {
        var clock = new br.com.colheitafeliz.testsupport.ManualClock();
        // A long-cycle fixture crosses several rewarded levels in one real harvest action.
        var crop = new br.com.colheitafeliz.domain.crop.CropType("nabo", "Nabo", 8_640_000, 0, 1, 5,
                "nabo", 1, false, java.util.Map.of(br.com.colheitafeliz.domain.economy.Currency.GOLD, new BigDecimal("300")));
        var game = new br.com.colheitafeliz.game.GameState(clock::instant, clock,
                new br.com.colheitafeliz.domain.crop.CropCatalog(java.util.List.of(crop)), bound -> 99);
        assertTrue(game.buySeed(crop).success());
        game.selectTool(br.com.colheitafeliz.game.Tool.HOE);
        assertTrue(game.act(0, 0).success());
        game.selectSeed(crop);
        assertTrue(game.act(0, 0).success());
        game.selectTool(br.com.colheitafeliz.game.Tool.WATER);
        assertTrue(game.act(0, 0).success());
        clock.advanceSeconds(17_280_000);
        game.selectTool(br.com.colheitafeliz.game.Tool.HAND);
        assertTrue(game.act(0, 0).success());
        assertEquals(16, game.progress().level());
        assertEquals(0, new BigDecimal("188").compareTo(game.progress().currentXp()));
        assertEquals(0, new BigDecimal("243200").compareTo(game.wallet().coins()));
        assertEquals(0, new BigDecimal("35").compareTo(game.greenCoins()));
        assertTrue(game.sellEverything().success());
        assertFalse(game.act(0, 0).success());
        game.selectTool(br.com.colheitafeliz.game.Tool.HOE);
        assertTrue(game.act(0, 0).success());
        assertEquals(0, new BigDecimal("190").compareTo(game.progress().currentXp()));
        assertEquals(0, new BigDecimal("243545").compareTo(game.wallet().coins()));
        assertEquals(0, new BigDecimal("36").compareTo(game.greenCoins()));
        assertTrue(game.inventory().seedLots().isEmpty());
    }

    @Test void listedLevelPaysBothCurrenciesWithoutSeedGifts() {
        var clock = new br.com.colheitafeliz.testsupport.ManualClock();
        var game = new br.com.colheitafeliz.game.GameState(clock::instant, clock,
                br.com.colheitafeliz.game.CropCatalogLoader.load(), bound -> 99);
        game.progress().award(137);
        var turnip = game.catalog().get("nabo");
        game.inventory().addSeedLot(new br.com.colheitafeliz.domain.inventory.ItemLot(turnip.id(),
                turnip.seedPrice().getKey(), turnip.seedPrice().getValue(), 1, false));
        game.selectTool(br.com.colheitafeliz.game.Tool.HOE);
        assertTrue(game.act(0, 0).success());
        game.selectSeed(turnip);
        assertTrue(game.act(0, 0).success());
        assertEquals(5, game.progress().level());
        assertEquals(0, new BigDecimal("9500").compareTo(game.wallet().coins()));
        assertEquals(0, new BigDecimal("7").compareTo(game.greenCoins()));
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
        assertEquals(0, BigDecimal.ONE.compareTo(game.greenCoins()));
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
