package br.com.colheitafeliz.game;

import br.com.colheitafeliz.domain.notification.NotificationInbox;
import br.com.colheitafeliz.domain.notification.GameNotification;
import br.com.colheitafeliz.testsupport.ManualClock;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NotificationTest {
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
