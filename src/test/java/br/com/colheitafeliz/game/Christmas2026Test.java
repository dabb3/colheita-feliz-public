package br.com.colheitafeliz.game;

import br.com.colheitafeliz.domain.economy.Currency;
import br.com.colheitafeliz.testsupport.ManualClock;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Christmas2026Test {
    @Test void readingAndEvictingGiftNotificationDoesNotMakeGiftAvailableAgain(){
        var clock=java.time.Clock.fixed(Christmas2026.GIFT_START,java.time.ZoneOffset.UTC);
        var game=new GameState(clock::instant,clock,CropCatalogLoader.load(),bound->99);assertTrue(game.claimChristmasGift(Instant.EPOCH));
        assertTrue(game.markAllNotificationsRead().success());
        for(int i=0;i<101;i++)game.notifications().record("theft","event-"+i,br.com.colheitafeliz.domain.notification.GameNotification.Type.THEFT,"Evento",clock.instant(),java.util.Map.of());
        assertTrue(game.notifications().entries().stream().noneMatch(n->n.type()==br.com.colheitafeliz.domain.notification.GameNotification.Type.CHRISTMAS_GIFT));
        var restored=GameState.restore(clock::instant,clock,CropCatalogLoader.load(),game.snapshot(),bound->99);
        assertFalse(restored.claimChristmasGift());assertEquals(1,restored.inventory().seeds(restored.catalog().get("mini-bis")));
    }
    @Test void permanentSeedRequiresLevelAndVipThenCompletesSixRealHarvestsForFiftyOneGreens(){
        var clock=new ManualClock();var game=AnimalEconomyV2Test.rich(clock);var type=game.catalog().get("mini-bis");
        assertFalse(game.buySeed(type).success());assertTrue(game.buyVip().success());
        var coins=game.greenCoins();assertTrue(game.buySeed(type).success());assertEquals(0,new BigDecimal("30").compareTo(coins.subtract(game.greenCoins())));
        assertTrue(game.selectSeed(type).success());game.selectTool(Tool.HOE);assertTrue(game.act(0,0).success());game.selectTool(Tool.SEED);assertTrue(game.act(0,0).success());
        for(int season=1;season<=6;season++){
            var tile=game.farm().tile(0,0);var ready=br.com.colheitafeliz.testsupport.TestCrops.growUntilReady(tile,clock.instant());clock.advanceSeconds(java.time.Duration.between(clock.instant(),ready).getSeconds());
            game.selectTool(Tool.HAND);assertTrue(game.act(0,0).success());
        }
        assertEquals(new BigDecimal("51.0"),game.quoteAllItem(type.id()).get(Currency.GREEN));
        assertTrue(game.sellAllProduce(type).success());assertFalse(game.sellAllProduce(type).success());
        clock.advanceSeconds(java.time.Duration.between(clock.instant(),Christmas2026.END.plusSeconds(86400)).getSeconds());
        assertTrue(game.buyVip().success());assertTrue(game.buySeed(type).success());
    }
    @Test void mediaUsesChristmasTracksAndReturnsToDefaultsAtTheExactCutoff(){
        var during=Instant.parse("2026-12-31T23:00:00Z");var after=Christmas2026.END;
        assertEquals("classic/background/background-natal-dia.png",Christmas2026.backgroundPath(during,"day"));
        assertEquals("classic/background/background-natal-por-do-sol.png",Christmas2026.backgroundPath(during,"sunset"));
        assertEquals("classic/background/background-natal-noite.png",Christmas2026.backgroundPath(during,"night"));
        assertEquals("classic/background/background.png",Christmas2026.backgroundPath(after,"day"));
        assertEquals("classic/background/background-sunset.png",Christmas2026.backgroundPath(after,"sunset"));
        assertEquals("classic/background/background-night.png",Christmas2026.backgroundPath(after,"night"));
        assertEquals("sounds/musica-evento-natal-dia-e-por-do-sol.mp3",Christmas2026.musicPath(during,false));
        assertEquals("sounds/musica-evento-natal-noite.mp3",Christmas2026.musicPath(during,true));
        assertEquals("sounds/farm_loop.mp3",Christmas2026.musicPath(after,false));assertEquals("sounds/noite.mp3",Christmas2026.musicPath(after,true));
    }
    @Test void saoPauloBoundariesAndGiftEligibilityUseRealInstants(){
        assertFalse(Christmas2026.active(Instant.parse("2026-11-06T02:59:59Z")));
        assertTrue(Christmas2026.active(Instant.parse("2026-11-06T03:00:00Z")));
        assertTrue(Christmas2026.active(Instant.parse("2027-01-01T02:59:59Z")));
        assertFalse(Christmas2026.active(Instant.parse("2027-01-01T03:00:00Z")));
        assertFalse(Christmas2026.giftDue(Instant.parse("2026-12-24T02:59:59Z"),Instant.EPOCH));
        assertTrue(Christmas2026.giftDue(Instant.parse("2027-02-01T00:00:00Z"),Instant.parse("2026-12-25T02:59:59Z")));
        assertFalse(Christmas2026.giftDue(Instant.parse("2027-02-01T00:00:00Z"),Instant.parse("2026-12-25T03:00:00Z")));
    }
    @Test void miniBisHasExactApprovedTermsAndRemainsInCatalogAfterChristmas(){
        var type=CropCatalogLoader.load().get("mini-bis");
        assertEquals(55,type.requiredLevel());assertTrue(type.vipOnly());assertEquals(new BigDecimal("30"),type.seedPrice().getValue());
        assertEquals(72*3600,type.growthSeconds());assertEquals(36*3600,type.regrowthSeconds());assertEquals(6,type.seasons());
        var lot=new br.com.colheitafeliz.domain.inventory.ItemLot(type.id(),Currency.GREEN,new BigDecimal("30"),1,true);
        BigDecimal total=BigDecimal.ZERO;
        for(int season=1;season<=6;season++){
            var sale=type.plantingOrigin(lot).harvested(season).cropSale();assertEquals(new BigDecimal("8.5"),sale.unitRevenue(true));total=total.add(sale.unitRevenue(true));
        }
        assertEquals(new BigDecimal("51.0"),total);assertEquals(new BigDecimal("21.0"),total.subtract(lot.unitCost()));
    }
    @Test void giftIsOneVipSeedWithoutMoneyXpOrMissionRewardsAndSurvivesRestore(){
        var clock=new ManualClock();clock.advanceSeconds(java.time.Duration.between(clock.instant(),Instant.parse("2026-12-24T03:00:00Z")).getSeconds());
        var game=new GameState(clock::instant,clock,CropCatalogLoader.load(),bound->99);
        var before=game.snapshot();assertTrue(game.claimChristmasGift(Instant.parse("2026-12-01T00:00:00Z")));
        assertFalse(game.claimChristmasGift(Instant.EPOCH));var seed=game.inventory().seedLots().getFirst();
        assertEquals("mini-bis",seed.itemId());assertEquals(1,seed.quantity());assertTrue(seed.vipOnly());assertEquals(Currency.GREEN,seed.currency());
        assertEquals(before.coins(),game.wallet().coins());assertEquals(before.greenCoins(),game.greenCoins());assertEquals(before.currentXp(),game.progress().currentXp());
        assertEquals(before.dailyMissionProgress(),game.snapshot().dailyMissionProgress());
        var restored=GameState.restore(clock::instant,clock,CropCatalogLoader.load(),game.snapshot(),bound->99);
        assertFalse(restored.claimChristmasGift(Instant.EPOCH));assertEquals(1,restored.inventory().seeds(restored.catalog().get("mini-bis")));
        assertFalse(restored.selectSeed(restored.catalog().get("mini-bis")).success());
    }
}
