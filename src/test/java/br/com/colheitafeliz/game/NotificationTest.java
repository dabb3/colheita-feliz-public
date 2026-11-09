package br.com.colheitafeliz.game;

import br.com.colheitafeliz.domain.notification.NotificationInbox;
import br.com.colheitafeliz.domain.notification.GameNotification;
import br.com.colheitafeliz.domain.crop.Crop;
import br.com.colheitafeliz.domain.farm.TileState;
import br.com.colheitafeliz.testsupport.ManualClock;
import java.util.Map;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NotificationTest {
    @Test void lateCropNotificationPreservesMaturityAndOwnerProtectionTimes() {
        var maturedAt=Instant.parse("2026-11-08T14:07:20.730Z");
        var detectedAt=Instant.parse("2026-11-08T14:53:07.606155006Z");
        var type=CropCatalogLoader.load().get("ervilha");
        var wateredAt=Instant.parse("2026-11-08T11:14:38Z");
        long grown=type.growthTime(2).toMillis()-Duration.between(wateredAt,maturedAt).toMillis();
        var crop=new Crop(type,wateredAt,wateredAt,null,wateredAt,grown,br.com.colheitafeliz.testsupport.TestCrops.origin(type),2);
        var clock=Clock.fixed(detectedAt,ZoneOffset.UTC);
        var game=new GameState(()->detectedAt,clock,CropCatalogLoader.load(),bound->99);
        game.farm().tile(0,1).restore(TileState.READY,crop);

        assertTrue(game.refresh());
        var notification=game.notifications().entries().getFirst();
        assertEquals(detectedAt.toString(),notification.createdAt());
        assertEquals(maturedAt.toString(),notification.metadata().get("maturedAt"));
        assertEquals(maturedAt.plus(Duration.ofMinutes(5)).toString(),notification.metadata().get("ownerExclusiveUntil"));
    }

    @Test void dismissOnlyReadPreservesPermanentCursorsThroughRestore() {
        var box=new NotificationInbox();var now=new ManualClock().instant();
        box.record("christmas-gift-2026","claimed",GameNotification.Type.CROP_READY,"Presente",now,Map.of());
        String gift=box.entries().getFirst().id();assertFalse(box.dismissRead(gift));
        box.markRead(gift);assertTrue(box.dismissRead(gift));assertFalse(box.dismissRead(gift));
        box.record("crop:0","ready",GameNotification.Type.CROP_READY,"Pronta",now,Map.of());
        box.record("vip","expired",GameNotification.Type.VIP_EXPIRED,"VIP",now,Map.of());
        box.markRead(box.entries().getFirst().id());assertEquals(1,box.dismissAllRead());assertEquals(1,box.unread());
        var restored=new NotificationInbox(box.snapshot());assertEquals(box.snapshot(),restored.snapshot());
        assertFalse(restored.record("christmas-gift-2026","claimed",GameNotification.Type.CROP_READY,"Presente",now,Map.of()));
        assertFalse(restored.record("vip","expired",GameNotification.Type.VIP_EXPIRED,"VIP",now,Map.of()));
        assertEquals(0,restored.dismissAllRead());assertEquals(1,restored.entries().size());
    }
    @Test void eachReadySeasonAndTheftAreRecordedOnceWithoutRewards() {
        var clock=new ManualClock();var owner=AnimalEconomyV2Test.rich(clock);var visitor=AnimalEconomyV2Test.rich(clock);
        var tile=owner.farm().tile(0,0);tile.plow();tile.plant(owner.catalog().get("morango"),clock.instant());
        var ready=br.com.colheitafeliz.testsupport.TestCrops.growUntilReady(tile,clock.instant());
        clock.advanceSeconds(java.time.Duration.between(clock.instant(),ready).getSeconds());
        var coins=owner.wallet().coins();var greens=owner.greenCoins();var xp=owner.progress().currentXp();
        owner.refresh();owner.refresh();
        assertEquals(1,owner.notifications().entries().size());
        var visits=new VisitService();long revision=tile.harvestRevision();
        assertFalse(visits.steal(owner,visitor,0,0,revision+1,"João").success());
        assertEquals(1,owner.notifications().entries().size());
        assertTrue(visits.steal(owner,visitor,0,0,revision,"João").success());
        assertEquals("João furtou 1 Morango da sua fazenda.",owner.notifications().entries().getFirst().message());
        assertFalse(visits.steal(owner,visitor,0,0,revision,"João").success());
        assertEquals(2,owner.notifications().entries().size());
        ready=br.com.colheitafeliz.testsupport.TestCrops.growUntilReady(tile,clock.instant());
        clock.advanceSeconds(java.time.Duration.between(clock.instant(),ready).getSeconds());owner.refresh();
        assertEquals(2,owner.notifications().entries().stream().filter(n->n.type()==GameNotification.Type.CROP_READY).count());
        assertEquals(coins,owner.wallet().coins());assertEquals(greens,owner.greenCoins());assertEquals(xp,owner.progress().currentXp());
    }
    @Test void inboxKeepsLatestHundredAndReadSurvivesRoundTripWithoutRecreatingEvictedEvents() {
        var box=new NotificationInbox();var now=new ManualClock().instant();
        box.record("crop:1","old-ready",GameNotification.Type.CROP_READY,"Pronta",now,Map.of());
        var evicted=box.entries().getFirst().id();
        for(int i=0;i<105;i++)assertTrue(box.record("crop:0",""+i,GameNotification.Type.CROP_READY,"Pronta",now,Map.of()));
        assertEquals(100,box.entries().size());assertEquals(100,box.unread());
        assertTrue(box.entries().stream().noneMatch(n->n.id().equals(evicted)));
        assertFalse(box.record("crop:1","old-ready",GameNotification.Type.CROP_READY,"Pronta",now,Map.of()));
        var id=box.entries().getFirst().id();assertTrue(box.markRead(id));assertEquals(99,box.unread());
        var restored=new NotificationInbox(box.snapshot());assertFalse(restored.record("crop:0","104",GameNotification.Type.CROP_READY,"Pronta",now,Map.of()));
        assertEquals(99,restored.unread());assertTrue(restored.markAllRead());assertEquals(0,restored.unread());
        assertFalse(restored.markAllRead());assertFalse(restored.markRead("missing"));
    }
    @Test void realGameReadinessAndVipExpiryAreNotRepeatedOnRefreshOrRestore() {
        var clock=new ManualClock();var game=AnimalEconomyV2Test.rich(clock);
        assertTrue(game.buyVip().success());assertTrue(game.buyAnimal(game.animalType("galinha")).success());
        clock.advanceSeconds(8*3600);game.refresh();
        assertEquals(1,game.notifications().entries().stream().filter(n->n.type()==GameNotification.Type.ANIMAL_READY).count());
        game.refresh();var snapshot=game.snapshot();
        var restored=GameState.restore(clock::instant,clock,CropCatalogLoader.load(),snapshot,bound->99);
        assertEquals(game.notifications().snapshot(),restored.notifications().snapshot());
        clock.advanceSeconds(30*86400);restored.refresh();restored.refresh();
        assertEquals(1,restored.notifications().entries().stream().filter(n->n.type()==GameNotification.Type.VIP_EXPIRED).count());
        assertEquals(0,game.wallet().coins().compareTo(restored.wallet().coins()));
    }
}
